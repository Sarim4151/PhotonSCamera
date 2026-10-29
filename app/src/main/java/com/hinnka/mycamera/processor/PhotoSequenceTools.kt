package com.hinnka.mycamera.processor

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.*

enum class PhotoSequenceOperation { FOCUS_STACK, MOTION_AVERAGE, LIGHT_TRAILS, BEST_FRAME, MOTION_HDR, NIGHT_STACK, ASTRO_STACK, STAR_TRAILS, SUPER_RESOLUTION }

data class PhotoSequenceResult(
    val bitmap: Bitmap,
    val bestFrameIndex: Int? = null,
    val alignmentOffsets: List<Pair<Int, Int>> = emptyList(),
    val rejectedFrameIndices: List<Int> = emptyList(),
    val acceptedFrameCount: Int = 0,
    val reconstructionType: String? = null,
    val fallbackReason: String? = null
)

/**
 * Deterministic burst processing for equally sized, software-readable photographs.
 * Registration models integer translation only (no rotation, parallax, rolling shutter or subject
 * motion). Incompatible or ambiguous textured frames are rejected rather than blended unaligned.
 * Focus fusion uses local detail, not depth; moving subjects can ghost and textureless regions cannot
 * be focus-ranked reliably. Best-frame ranking measures detail and clipping, never eyes/expression.
 * RGB is interpreted as sRGB; output is opaque sRGB ARGB_8888. Inputs are never modified or recycled.
 * Offsets mean source(x + dx, y + dy) corresponds to reference(x, y). Fusion crops to shared bounds.
 */
object PhotoSequenceTools {
    private val linear = FloatArray(256) { i ->
        val v = i / 255f
        if (v <= .04045f) v / 12.92f else ((v + .055f) / 1.055f).pow(2.4f)
    }

    suspend fun process(
        bitmaps: List<Bitmap>,
        operation: PhotoSequenceOperation,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): PhotoSequenceResult = process(bitmaps, operation, null, onProgress)

    suspend fun process(
        bitmaps: List<Bitmap>,
        operation: PhotoSequenceOperation,
        astroHorizon: Float?,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): PhotoSequenceResult {
        require(bitmaps.all { !it.isRecycled && it.config != Bitmap.Config.HARDWARE })
        return processFrames(bitmaps.map(::BitmapSequenceFrame), operation, onProgress, astroHorizon)
    }

    internal suspend fun processFrames(bitmaps: List<SequenceFrame>, operation: PhotoSequenceOperation,
        onProgress: (Int, Int) -> Unit = { _, _ -> }, astroHorizon: Float? = null): PhotoSequenceResult {
        // withContext can cancel after its block completes but before the caller owns the result.
        var owned: PhotoSequenceResult? = null
        try {
            return withContext(Dispatchers.Default) {
                processFramesNow(bitmaps, operation, onProgress, astroHorizon).also { owned = it }
            }
        } catch (failure: Throwable) {
            owned?.bitmap?.let { if (!it.isRecycled) it.recycle() }
            throw failure
        }
    }

    private suspend fun processFramesNow(bitmaps: List<SequenceFrame>, operation: PhotoSequenceOperation,
        onProgress: (Int, Int) -> Unit, astroHorizon: Float?): PhotoSequenceResult = run {
        require(bitmaps.size in 2..15) { "A sequence requires 2 to 15 frames" }
        val width = bitmaps.first().width
        val height = bitmaps.first().height
        require(width > 0 && height > 0 && width.toLong() * height < Int.MAX_VALUE / 4) {
            "Frame dimensions exceed supported array sizes"
        }
        require(bitmaps.all { it.width == width && it.height == height }) {
            "Frames must be readable and have identical dimensions"
        }
        val budget = SequenceMemoryBudget.snapshot()
        val nativeOutput = width.toLong() * height * 4L + 16L * 1024 * 1024
        // Primitive stripe arrays, bounded registration windows and previews use Java heap.
        val javaScratch = if (operation == PhotoSequenceOperation.SUPER_RESOLUTION)
            maxOf(64L * 1024 * 1024, width.toLong() * 3072L) + 16L * 1024 * 1024
        else width.toLong() * 4096L + 24L * 1024 * 1024
        require(budget.fits(nativeOutput, javaScratch)) {
            "Insufficient native bitmap or Java scratch memory for requested source resolution"
        }
        if (operation == PhotoSequenceOperation.SUPER_RESOLUTION) {
            return@run SuperResolutionProcessor.processFrames(bitmaps, onProgress)
        }
        if (operation in setOf(PhotoSequenceOperation.MOTION_HDR, PhotoSequenceOperation.NIGHT_STACK,
                PhotoSequenceOperation.ASTRO_STACK, PhotoSequenceOperation.STAR_TRAILS)) {
            return@run AdvancedSequenceProcessor.processFrames(bitmaps, operation, onProgress, astroHorizon)
        }
        val context = currentCoroutineContext()
        val total = bitmaps.size * 2
        onProgress(0, total)
        val registration = mutableListOf<Bitmap>()
        try {
            for (frame in bitmaps) registration += frame.registrationBitmap()
            val rw = registration[0].width; val rh = registration[0].height
            val reference = pixels(registration[0])
            val offsets = ArrayList<Pair<Int, Int>>(bitmaps.size)
            offsets.add(0 to 0)
            var bestIndex = 0
            var bestScore = Double.NEGATIVE_INFINITY
            for (i in bitmaps.indices) {
                context.ensureActive()
                val frame = if (i == 0) reference else pixels(registration[i])
                if (i > 0) offsets.add(if (operation == PhotoSequenceOperation.BEST_FRAME) 0 to 0 else {
                    val coarse = register(reference, frame, rw, rh)
                    val step = max(1, ceil(max(width, height) / 512.0).toInt())
                    refine(bitmaps[0], bitmaps[i], coarse.first * step, coarse.second * step, step)
                })
                if (operation == PhotoSequenceOperation.BEST_FRAME) {
                    val score = qualityNative(bitmaps[i])
                    if (score > bestScore) { bestScore = score; bestIndex = i }
                }
                onProgress(i + 1, total)
            }
            if (operation == PhotoSequenceOperation.BEST_FRAME) {
                val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                try {
                    val row = IntArray(width)
                    for (y in 0 until height) {
                        context.ensureActive()
                        bitmaps[bestIndex].getPixels(row, 0, width, 0, y, width, 1)
                        for (x in row.indices) row[x] = row[x] or (0xff shl 24)
                        result.setPixels(row, 0, width, 0, y, width, 1)
                    }
                    onProgress(total, total)
                    return@run PhotoSequenceResult(result, bestIndex, offsets, acceptedFrameCount = 1)
                } catch (t: Throwable) { result.recycle(); throw t }
            }
            val left = offsets.maxOf { max(0, -it.first) }
            val top = offsets.maxOf { max(0, -it.second) }
            val right = offsets.minOf { min(width, width - it.first) }
            val bottom = offsets.minOf { min(height, height - it.second) }
            val outWidth = right - left
            val outHeight = bottom - top
            require(outWidth > 0 && outHeight > 0) { "Frames have no common aligned area" }
            val result = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
            try {
                // Stripes bound working memory independently of burst length. A three-pixel halo
                // supports local-detail measurement without seams between stripes.
                for (start in 0 until outHeight step 48) {
                    context.ensureActive()
                    val rows = min(48, outHeight - start)
                    val size = outWidth * rows
                    val red = FloatArray(size)
                    val green = FloatArray(size)
                    val blue = FloatArray(size)
                    val weights = FloatArray(size)
                    for (i in bitmaps.indices) {
                        context.ensureActive()
                        val (dx, dy) = offsets[i]
                        val sx = left + dx
                        val sy = top + start + dy
                        val x0 = max(0, sx - 3)
                        val y0 = max(0, sy - 3)
                        val x1 = min(width, sx + outWidth + 3)
                        val y1 = min(height, sy + rows + 3)
                        val stride = x1 - x0
                        val source = IntArray(stride * (y1 - y0))
                        bitmaps[i].getPixels(source, 0, stride, x0, y0, stride, y1 - y0)
                        for (y in 0 until rows) {
                            context.ensureActive()
                            for (x in 0 until outWidth) {
                                val index = y * outWidth + x
                                val px = sx + x - x0
                                val py = sy + y - y0
                                val color = source[py * stride + px]
                                val weight = if (operation == PhotoSequenceOperation.FOCUS_STACK)
                                    detail(source, stride, y1 - y0, px, py) else 1f
                                val r = linear[(color ushr 16) and 255]
                                val g = linear[(color ushr 8) and 255]
                                val b = linear[color and 255]
                                if (operation == PhotoSequenceOperation.LIGHT_TRAILS) {
                                    red[index] = max(red[index], r)
                                    green[index] = max(green[index], g)
                                    blue[index] = max(blue[index], b)
                                } else {
                                    red[index] += r * weight
                                    green[index] += g * weight
                                    blue[index] += b * weight
                                    weights[index] += weight
                                }
                            }
                        }
                    }
                    val output = IntArray(size) { index ->
                        val divisor = if (operation == PhotoSequenceOperation.LIGHT_TRAILS) 1f else weights[index]
                        (0xff shl 24) or (encode(red[index] / divisor) shl 16) or
                            (encode(green[index] / divisor) shl 8) or encode(blue[index] / divisor)
                    }
                    result.setPixels(output, 0, outWidth, 0, start, outWidth, rows)
                    onProgress(bitmaps.size + ((start + rows).toLong() * bitmaps.size / outHeight).toInt(), total)
                }
                PhotoSequenceResult(result, alignmentOffsets = offsets, acceptedFrameCount = bitmaps.size)
            } catch (t: Throwable) { result.recycle(); throw t }
        } finally { registration.forEach { it.recycle() } }
    }

    /** Distributed native patches refine the bounded lattice estimate to integer source pixels. */
    private suspend fun refine(ref: SequenceFrame, src: SequenceFrame, dx: Int, dy: Int, radius: Int): Pair<Int, Int> {
        if (radius == 1) return dx to dy
        val border = max(abs(dx), abs(dy)) + radius + 20
        if (ref.width <= border * 2 || ref.height <= border * 2) return dx to dy
        data class Patch(val ref: IntArray, val src: IntArray, val side: Int)
        val side = 16 + radius * 2; val patches = ArrayList<Patch>()
        for (gy in 0..4) for (gx in 0..4) {
            currentCoroutineContext().ensureActive()
            val x = border + (ref.width - border * 2) * gx / 4 - 8
            val y = border + (ref.height - border * 2) * gy / 4 - 8
            val a = IntArray(256); val b = IntArray(side * side)
            ref.getPixels(a, 0, 16, x, y, 16, 16)
            src.getPixels(b, 0, side, x + dx - radius, y + dy - radius, side, side)
            patches += Patch(a, b, side)
        }
        var best = dx to dy; var minimum = Double.POSITIVE_INFINITY
        for (oy in -radius..radius) for (ox in -radius..radius) {
            currentCoroutineContext().ensureActive(); var error = 0.0
            for (p in patches) for (y in 0 until 16 step 2) for (x in 0 until 16 step 2) {
                val d = luma(p.ref[y * 16 + x]) - luma(p.src[(y + oy + radius) * p.side + x + ox + radius])
                error += d * d
            }
            if (error < minimum) { minimum = error; best = dx + ox to dy + oy }
        }
        return best
    }

    private fun pixels(bitmap: Bitmap) = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

    private fun luma(color: Int): Float =
        (((color ushr 16) and 255) * .2126f + ((color ushr 8) and 255) * .7152f + (color and 255) * .0722f) / 255f

    private fun encode(value: Float): Int {
        val v = value.coerceIn(0f, 1f)
        val srgb = if (v <= .0031308f) v * 12.92f else 1.055f * v.pow(1f / 2.4f) - .055f
        return (srgb * 255f + .5f).toInt().coerceIn(0, 255)
    }

    // Spatially feathered detail weights, with a small floor so featureless regions average.
    private fun detail(p: IntArray, w: Int, h: Int, x: Int, y: Int): Float {
        if (w < 3 || h < 3) return 1f
        var sum = 0f
        for (oy in -2..2) for (ox in -2..2) {
            val xx = (x + ox).coerceIn(1, w - 2)
            val yy = (y + oy).coerceIn(1, h - 2)
            val at = yy * w + xx
            val lap = abs(4 * luma(p[at]) - luma(p[at - 1]) - luma(p[at + 1]) - luma(p[at - w]) - luma(p[at + w]))
            sum += lap * (3 - abs(ox)) * (3 - abs(oy))
        }
        return .0001f + (sum / 81f).pow(2)
    }

    private suspend fun qualityNative(frame: SequenceFrame): Double {
        val w = frame.width; val h = frame.height
        if (w < 3 || h < 3) return 0.0
        var energy = 0.0; var clipped = 0; var count = 0
        val stripe = IntArray(w * 3)
        for (y in 1 until h - 1 step max(1, h / 192)) {
            currentCoroutineContext().ensureActive()
            frame.getPixels(stripe, 0, w, 0, y - 1, w, 3)
            for (x in 1 until w - 1 step max(1, w / 192)) {
                val at = w + x; val c = stripe[at]
                val lap = 4 * luma(c) - luma(stripe[at - 1]) - luma(stripe[at + 1]) - luma(stripe[x]) - luma(stripe[w * 2 + x])
                energy += lap * lap
                if (((c ushr 16) and 255) >= 250 || ((c ushr 8) and 255) >= 250 || (c and 255) >= 250 || luma(c) <= .015f) clipped++
                count++
            }
        }
        return if (count == 0) 0.0 else energy / count * (1.0 - clipped.toDouble() / count)
    }

    private suspend fun register(ref: IntArray, src: IntArray, w: Int, h: Int): Pair<Int, Int> {
        val maxX = floor(w * .04).toInt()
        val maxY = floor(h * .04).toInt()
        val step = max(1, max(w, h) / 192)
        val marginX = min(maxX + 1, (w - 1) / 2)
        val marginY = min(maxY + 1, (h - 1) / 2)
        // A fixed common sampling area makes candidate scores comparable. Mean subtraction
        // tolerates a modest exposure change; normalized residual detects incompatible frames.
        suspend fun error(dx: Int, dy: Int): Pair<Double, Double> {
            var sumA = 0.0; var sumB = 0.0; var aa = 0.0; var bb = 0.0; var ab = 0.0; var n = 0
            for (y in marginY until h - marginY step step) {
                currentCoroutineContext().ensureActive()
                for (x in marginX until w - marginX step step) {
                    val a = luma(ref[y * w + x]).toDouble()
                    val b = luma(src[(y + dy) * w + x + dx]).toDouble()
                    sumA += a; sumB += b; aa += a * a; bb += b * b; ab += a * b; n++
                }
            }
            val va = max(0.0, aa - sumA * sumA / n)
            val vb = max(0.0, bb - sumB * sumB / n)
            val residual = max(0.0, va + vb - 2 * (ab - sumA * sumB / n)) / n
            val exposureMismatch = max(0.0, abs(sumA - sumB) / n - .12)
            return (residual + exposureMismatch * exposureMismatch) to ((va + vb) / (2 * n))
        }
        var best = 0 to 0
        var score = error(0, 0).first
        // Coarse search on the downsampled luminance lattice, then integer-pixel refinement.
        for (dy in -maxY..maxY step step) for (dx in -maxX..maxX step step) {
            val e = error(dx, dy).first
            if (e < score) { score = e; best = dx to dy }
        }
        val coarse = best
        for (dy in max(-maxY, coarse.second - step)..min(maxY, coarse.second + step)) {
            for (dx in max(-maxX, coarse.first - step)..min(maxX, coarse.first + step)) {
                val e = error(dx, dy).first
                if (e < score) { score = e; best = dx to dy }
            }
        }
        val (residual, variance) = error(best.first, best.second)
        require(residual <= .012 && (variance < .0001 || residual / variance <= .55)) {
            "Sequence contains a frame that cannot be aligned by translation"
        }
        if (variance < .0001) {
            require(error(0, 0).first <= .0004) { "Insufficient detail to align sequence" }
            return 0 to 0
        }
        // Reject repeated-pattern ambiguity when a distant shift is equally plausible.
        for (dy in -maxY..maxY step step) for (dx in -maxX..maxX step step) {
            if (abs(dx - best.first) + abs(dy - best.second) > max(3, step * 2)) {
                require(error(dx, dy).first > residual * 1.08 + .000001) { "Ambiguous sequence alignment" }
            }
        }
        return best
    }
}
