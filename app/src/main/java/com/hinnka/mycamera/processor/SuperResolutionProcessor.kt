package com.hinnka.mycamera.processor

import android.graphics.Bitmap
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.*

/** Conditional two-times sample reconstruction from a static, translated sRGB burst.
 * This is finite-footprint drizzle, not generative detail synthesis or a guarantee of optical
 * resolution. Four distinct subpixel phases and confident full-resolution registration are
 * mandatory for 2x output. Bursts without the required phase or sample coverage instead produce
 * a native-size aligned linear stack; unalignable bursts preserve the best reference exposure.
 * Rotation, parallax and substantial exposure changes reject individual sources. Motion preserves
 * reference pixels. Missing 2x edge coverage uses reference interpolation. Inputs remain caller-owned.
 */
internal object SuperResolutionProcessor {
    suspend fun process(bitmaps: List<Bitmap>, progress: (Int, Int) -> Unit): PhotoSequenceResult =
        processFrames(bitmaps.map(::BitmapSequenceFrame), progress)
    private val linear = FloatArray(256) { val v = it / 255f; if (v <= .04045f) v / 12.92f else ((v + .055f) / 1.055f).pow(2.4f) }
    private data class Frame(val rgb: FloatArray, val w: Int, val h: Int, val registration: FloatArray? = null) {
        fun luma(x: Int, y: Int): Float { registration?.let { return it[y * w + x] }; val i = (y * w + x) * 3; return .2126f * rgb[i] + .7152f * rgb[i + 1] + .0722f * rgb[i + 2] }
    }
    private data class Shift(val x: Double, val y: Double)
    private fun encode(v: Float): Int {
        val x = v.coerceIn(0f, 1f)
        return ((if (x <= .0031308f) x * 12.92f else 1.055f * x.pow(1f / 2.4f) - .055f) * 255f + .5f).toInt()
    }
    private suspend fun read(b: SequenceFrame, firstRow: Int = 0, lastRow: Int? = null, registration: Boolean = false): Frame {
        val factor = 1
        val w = b.width / factor; val h = (lastRow ?: (b.height / factor - 1)) - firstRow + 1
        val data = FloatArray(w * h * 3); val rows = IntArray(b.width * factor)
        for (y in 0 until h) {
            currentCoroutineContext().ensureActive()
            b.getPixels(rows, 0, b.width, 0, (y + firstRow) * factor, b.width, factor)
            for (x in 0 until w) for (dy in 0 until factor) for (dx in 0 until factor) {
                val c = rows[dy * b.width + x * factor + dx]; val at = (y * w + x) * 3
                for (channel in 0..2) data[at + channel] += linear[(c ushr (16 - 8 * channel)) and 255] / (factor * factor)
            }
        }
        if (!registration) return Frame(data, w, h)
        // Register low frequencies: raw aliased sensor samples violate brightness constancy.
        // Reconstruction still uses the untouched linear samples.
        var luma = FloatArray(w * h) { i -> .2126f * data[i * 3] + .7152f * data[i * 3 + 1] + .0722f * data[i * 3 + 2] }
        repeat(3) {
            val horizontal = FloatArray(w * h); val vertical = FloatArray(w * h)
            for (y in 0 until h) {
                currentCoroutineContext().ensureActive()
                for (x in 0 until w) horizontal[y * w + x] = (luma[y * w + max(0, x - 1)] + 2 * luma[y * w + x] + luma[y * w + min(w - 1, x + 1)]) * .25f
            }
            for (y in 0 until h) for (x in 0 until w) vertical[y * w + x] = (horizontal[max(0, y - 1) * w + x] + 2 * horizontal[y * w + x] + horizontal[min(h - 1, y + 1) * w + x]) * .25f
            luma = vertical
        }
        return Frame(data, w, h, luma)
    }
    private fun sample(f: Frame, x: Double, y: Double, channel: Int = -1): Float {
        val xx = x.coerceIn(0.0, (f.w - 1).toDouble()); val yy = y.coerceIn(0.0, (f.h - 1).toDouble())
        val ix = xx.toInt(); val iy = yy.toInt(); val nx = min(ix + 1, f.w - 1); val ny = min(iy + 1, f.h - 1)
        val fx = (xx - ix).toFloat(); val fy = (yy - iy).toFloat()
        fun at(x0: Int, y0: Int) = if (channel < 0) f.luma(x0, y0) else f.rgb[(y0 * f.w + x0) * 3 + channel]
        return (at(ix, iy) * (1 - fx) + at(nx, iy) * fx) * (1 - fy) + (at(ix, ny) * (1 - fx) + at(nx, ny) * fx) * fy
    }
    private suspend fun error(a: Frame, b: Frame, shift: Shift, spacing: Int, margin: Int): Double {
        var error = 0.0; var mean = 0.0; var square = 0.0; var n = 0
        for (y in margin until a.h - margin step spacing) {
            currentCoroutineContext().ensureActive()
            for (x in margin until a.w - margin step spacing) {
                val u = a.luma(x, y).toDouble(); val v = sample(b, x + shift.x, y + shift.y).toDouble()
                val d = u - v; error += d * d; mean += u; square += u * u; n++
            }
        }
        if (n < 64) return Double.POSITIVE_INFINITY
        val variance = square / n - (mean / n).pow(2)
        return if (variance < .0002) Double.POSITIVE_INFINITY else error / n / variance
    }
    private suspend fun register(a: Frame, b: Frame): Shift? {
        val radius = min(32, max(2, (min(a.w, a.h) * .04).toInt()))
        val margin = radius + 2; val sparse = max(1, max(a.w, a.h) / 96)
        var best = Shift(0.0, 0.0); var score = error(a, b, best, sparse, margin)
        for (dy in -radius..radius step 4) for (dx in -radius..radius step 4) {
            val t = Shift(dx.toDouble(), dy.toDouble()); val s = error(a, b, t, sparse, margin)
            if (s < score) { best = t; score = s }
        }
        for (step in doubleArrayOf(1.0, .25, .125)) {
            val base = best; val span = if (step == 1.0) 4 else 3
            val spacing = max(1, max(a.w, a.h) / 192)
            score = error(a, b, best, spacing, margin)
            for (dy in -span..span) for (dx in -span..span) {
                val t = Shift(base.x + dx * step, base.y + dy * step)
                if (abs(t.x) > radius || abs(t.y) > radius) continue
                val s = error(a, b, t, spacing, margin)
                if (s < score) { best = t; score = s }
            }
        }
        if (!score.isFinite() || score > .035) return null
        // The minimum must be distinguishable from a distant periodic-pattern correspondence.
        for (dy in -radius..radius step 4) for (dx in -radius..radius step 4) {
            if (hypot(dx - best.x, dy - best.y) > 3 && error(a, b, Shift(dx.toDouble(), dy.toDouble()), sparse, margin) < score + .015) return null
        }
        return best
    }
    private fun phaseDistance(a: Shift, b: Shift): Double {
        fun circular(v: Double): Double { val f = abs(v - round(v)); return min(f, 1 - f) }
        return hypot(circular(a.x - b.x), circular(a.y - b.y))
    }
    // Box filtering commutes with bilinear interpolation away from the image boundary.
    private suspend fun lowFrequency(frame: Frame): Frame {
        val rgb = FloatArray(frame.rgb.size)
        for (y in 0 until frame.h) {
            currentCoroutineContext().ensureActive()
            for (x in 0 until frame.w) for (c in 0..2) {
                var sum = 0f
                for (dy in -1..1) for (dx in -1..1)
                    sum += frame.rgb[((y + dy).coerceIn(0, frame.h - 1) * frame.w + (x + dx).coerceIn(0, frame.w - 1)) * 3 + c]
                rgb[(y * frame.w + x) * 3 + c] = sum / 9f
            }
        }
        return Frame(rgb, frame.w, frame.h)
    }

    private suspend fun nativeStack(bitmaps: List<SequenceFrame>, shifts: Array<Shift?>, referenceIndex: Int,
        rejected: List<Int>, reason: String, progress: (Int, Int) -> Unit): PhotoSequenceResult {
        val w = bitmaps[referenceIndex].width; val h = bitmaps[referenceIndex].height
        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        try {
            for (start in 0 until h step 32) {
                currentCoroutineContext().ensureActive()
                val rows = min(32, h - start); val top = max(0, start - 2)
                val reference = read(bitmaps[referenceIndex], top, min(h - 1, start + rows + 2))
                val lowReference = lowFrequency(reference)
                val sum = FloatArray(w * rows * 3); val counts = IntArray(w * rows) { 1 }
                val motion = BooleanArray(w * rows)
                for (y in 0 until rows) for (x in 0 until w) for (c in 0..2)
                    sum[(y * w + x) * 3 + c] = reference.rgb[((start + y - top) * w + x) * 3 + c]
                for (i in bitmaps.indices) {
                    val shift = shifts[i] ?: continue
                    if (i == referenceIndex) continue
                    val y0 = max(0, floor(start + shift.y).toInt() - 2)
                    val y1 = min(h - 1, ceil(start + rows - 1 + shift.y).toInt() + 2)
                    if (y1 < y0) continue
                    val frame = read(bitmaps[i], y0, y1); val low = lowFrequency(frame)
                    for (y in 0 until rows) {
                        currentCoroutineContext().ensureActive()
                        for (x in 0 until w) {
                            val sx = x + shift.x; val sy = start + y + shift.y
                            if (sx !in 0.0..(w - 1).toDouble() || sy !in 0.0..(h - 1).toDouble()) continue
                            val at = y * w + x
                            // One disagreeing source vetoes every contribution at this pixel.
                            if ((0..2).any { abs(sample(low, sx, sy - y0, it) - sample(lowReference, x.toDouble(), (start + y - top).toDouble(), it)) > .10f }) {
                                motion[at] = true
                            }
                            counts[at]++
                            for (c in 0..2) sum[at * 3 + c] += sample(frame, sx, sy - y0, c)
                        }
                    }
                }
                val pixels = IntArray(w * rows)
                for (y in 0 until rows) for (x in 0 until w) {
                    val at = y * w + x; var packed = 0xff shl 24
                    for (c in 0..2) {
                        val value = if (motion[at]) reference.rgb[((start + y - top) * w + x) * 3 + c] else sum[at * 3 + c] / counts[at]
                        packed = packed or (encode(value) shl (16 - 8 * c))
                    }
                    pixels[at] = packed
                }
                output.setPixels(pixels, 0, w, 0, start, w, rows)
                progress(bitmaps.size + ((start + rows).toLong() * bitmaps.size / h).toInt(), bitmaps.size * 2)
            }
            return PhotoSequenceResult(output, bestFrameIndex = referenceIndex,
                alignmentOffsets = shifts.map { (it?.x?.roundToInt() ?: 0) to (it?.y?.roundToInt() ?: 0) },
                rejectedFrameIndices = rejected, acceptedFrameCount = shifts.count { it != null },
                reconstructionType = if (shifts.count { it != null } > 1) "aligned_native" else "reference_native", fallbackReason = reason)
        } catch (t: Throwable) { output.recycle(); throw t }
    }
    suspend fun processFrames(bitmaps: List<SequenceFrame>, progress: (Int, Int) -> Unit): PhotoSequenceResult {
        require(bitmaps.isNotEmpty()) { "A reconstruction requires a source frame" }
        val w = bitmaps[0].width; val h = bitmaps[0].height
        require(min(w, h) >= 64) { "Insufficient image area for super resolution registration" }
        // Register a native-sampling central window. This retains real subpixel phases without
        // allocating full-frame RGB floats; reconstruction below reads only source stripes.
        suspend fun registrationFrame(bitmap: SequenceFrame): Frame {
            val cropW = min(1024, w); val cropH = min(1024, h)
            val pixels = IntArray(cropW * cropH)
            bitmap.getPixels(pixels, 0, cropW, (w - cropW) / 2, (h - cropH) / 2, cropW, cropH)
            val window = Bitmap.createBitmap(pixels, cropW, cropH, Bitmap.Config.ARGB_8888)
            return try { read(BitmapSequenceFrame(window), registration = true) } finally { window.recycle() }
        }
        // Rank sparse native rows before alignment so an unusable burst still preserves its
        // sharpest valid exposure. This does not allocate another full-resolution frame.
        suspend fun quality(bitmap: SequenceFrame): Double {
            val row = IntArray(w); var detail = 0.0; var clipped = 0; var count = 0
            for (y in 0 until h step max(1, h / 48)) {
                currentCoroutineContext().ensureActive()
                bitmap.getPixels(row, 0, w, 0, y, w, 1)
                for (x in 1 until w - 1 step max(1, w / 192)) {
                    fun luma(c: Int) = .2126 * linear[(c ushr 16) and 255] + .7152 * linear[(c ushr 8) and 255] + .0722 * linear[c and 255]
                    val value = luma(row[x]); val lap = 2 * value - luma(row[x - 1]) - luma(row[x + 1])
                    detail += lap * lap; count++
                    if (value < .005 || value > .95) clipped++
                }
            }
            return if (count == 0) 0.0 else detail / count * (1.0 - clipped.toDouble() / count)
        }
        var referenceIndex = 0; var bestQuality = Double.NEGATIVE_INFINITY
        for (i in bitmaps.indices) {
            val score = quality(bitmaps[i])
            if (score > bestQuality) { bestQuality = score; referenceIndex = i }
        }
        val bestReferenceIndex = referenceIndex
        referenceIndex = 0
        val reference = registrationFrame(bitmaps[referenceIndex])
        val shifts = arrayOfNulls<Shift>(bitmaps.size); shifts[referenceIndex] = Shift(0.0, 0.0)
        val rejected = ArrayList<Int>(); val phases = arrayListOf(shifts[referenceIndex]!!)
        val total = bitmaps.size * 2; progress(1, total)
        var registered = 1
        for (i in bitmaps.indices) {
            if (i == referenceIndex) continue
            currentCoroutineContext().ensureActive()
            shifts[i] = register(reference, registrationFrame(bitmaps[i]))
            val shift = shifts[i]
            if (shift == null) rejected += i else if (phases.all { phaseDistance(it, shift) >= .3 }) phases += shift
            progress(++registered, total)
        }
        if (shifts.count { it != null } == 1 && bestReferenceIndex != referenceIndex) {
            shifts[referenceIndex] = null; shifts[bestReferenceIndex] = Shift(0.0, 0.0)
            rejected.clear(); rejected.addAll(bitmaps.indices.filter { it != bestReferenceIndex })
            referenceIndex = bestReferenceIndex
        }
        if (phases.size < 4) return nativeStack(bitmaps, shifts, referenceIndex, rejected,
            "Insufficient independent subpixel phases for super resolution", progress)
        // Four phase groups must span both axes; diagonal-only jitter cannot fill a 2D lattice.
        val phaseBins = phases.map { ((it.x - floor(it.x)) * 2).toInt().coerceIn(0, 1) to ((it.y - floor(it.y)) * 2).toInt().coerceIn(0, 1) }.toSet()
        if (phaseBins.size != 4) return nativeStack(bitmaps, shifts, referenceIndex, rejected,
            "Subpixel shifts do not cover both image axes", progress)
        val budget = SequenceMemoryBudget.snapshot()
        val native2x = w.toLong() * h * 16L + 16L * 1024 * 1024
        val java2x = maxOf(64L * 1024 * 1024, w.toLong() * 8192L) + 16L * 1024 * 1024
        if (!budget.fits(native2x, java2x)) return nativeStack(bitmaps, shifts, referenceIndex, rejected,
            "Native aligned output: insufficient native bitmap or Java scratch memory for a two-times output", progress)
        val outW = w * 2; val outH = h * 2
        val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        try {
            var interior = 0L; var covered = 0L
            val border = min(68, min(w, h) / 4)
            for (start in 0 until outH step 32) {
                currentCoroutineContext().ensureActive()
                val rows = min(32, outH - start); val sum = FloatArray(outW * rows * 3); val weights = FloatArray(outW * rows)
                val motion = BooleanArray(outW * rows)
                val referenceTop = max(0, start / 2 - 40)
                val referenceStripe = read(bitmaps[referenceIndex], referenceTop, min(h - 1, (start + rows) / 2 + 40))
                val lowReference = lowFrequency(referenceStripe)

                for (i in bitmaps.indices) {
                    val shift = shifts[i] ?: continue
                    val y0 = max(0, floor((start - 2) / 2.0 + shift.y).toInt() - 2)
                    val y1 = min(h - 1, ceil((start + rows + 1) / 2.0 + shift.y).toInt() + 2)
                    if (y1 < y0) continue
                    val frame = read(bitmaps[i], y0, y1)
                    val frameTop = y0
                    val lowFrame = if (i != referenceIndex) lowFrequency(frame) else frame
                    for (y in y0..y1) {
                        currentCoroutineContext().ensureActive()
                        for (x in 0 until w) {
                            val ox = 2 * (x - shift.x); val oy = 2 * (y - shift.y)
                            if (i != referenceIndex) {
                                // Every reconstructed area must agree with the preserved reference,
                                // including subjects outside the registration window. A disagreement
                                // invalidates the whole footprint and uses the reference there.
                                val rx = x - shift.x; val ry = y - shift.y
                                val inconsistent = rx !in 1.0..(w - 2).toDouble() || ry !in 1.0..(h - 2).toDouble() ||
                                    (0..2).any { channel ->
                                        abs(sample(lowFrame, x.toDouble(), (y - frameTop).toDouble(), channel) -
                                            sample(lowReference, rx, ry - referenceTop, channel)) > .10f
                                    }
                                if (inconsistent) {
                                    for (yy in floor(oy).toInt() - 3..ceil(oy).toInt() + 3) {
                                        if (yy !in start until start + rows) continue
                                        for (xx in floor(ox).toInt() - 3..ceil(ox).toInt() + 3) {
                                            if (xx in 0 until outW) motion[(yy - start) * outW + xx] = true
                                        }
                                    }
                                    continue
                                }
                            }
                            for (yy in floor(oy).toInt()..ceil(oy).toInt()) {
                                if (yy !in start until start + rows) continue
                                val wy = max(0.0, 1 - abs(yy - oy) / .85)
                                for (xx in floor(ox).toInt()..ceil(ox).toInt()) {
                                    if (xx !in 0 until outW) continue
                                    val weight = (wy * max(0.0, 1 - abs(xx - ox) / .85)).toFloat()
                                    if (weight <= 0f) continue
                                    val at = (yy - start) * outW + xx; weights[at] += weight
                                    for (c in 0..2) sum[at * 3 + c] += frame.rgb[((y - frameTop) * w + x) * 3 + c] * weight
                                }
                            }
                        }
                    }
                }
                val fallbackTop = referenceTop
                val fallback = referenceStripe
                val pixels = IntArray(outW * rows)
                for (y in 0 until rows) {
                    currentCoroutineContext().ensureActive()
                    for (x in 0 until outW) {
                        val at = y * outW + x; val weight = weights[at]
                        if (x in border until outW - border && start + y in border until outH - border) { interior++; if (weight > .05f) covered++ }
                        var packed = 0xff shl 24
                        for (c in 0..2) {
                            val value = if (weight > .05f && !motion[at]) sum[at * 3 + c] / weight else sample(fallback, x / 2.0, (start + y) / 2.0 - fallbackTop, c)
                            packed = packed or (encode(value) shl (16 - 8 * c))
                        }
                        pixels[at] = packed
                    }
                }
                output.setPixels(pixels, 0, outW, 0, start, outW, rows)
                progress(bitmaps.size + ((start + rows).toLong() * bitmaps.size / outH).toInt(), total)
            }
            if (interior <= 0 || covered.toDouble() / interior < .98) {
                output.recycle()
                return nativeStack(bitmaps, shifts, referenceIndex, rejected, "Insufficient sample coverage for super resolution", progress)
            }
            return PhotoSequenceResult(output, bestFrameIndex = referenceIndex, alignmentOffsets = shifts.map { (it?.x?.roundToInt() ?: 0) to (it?.y?.roundToInt() ?: 0) }, rejectedFrameIndices = rejected, acceptedFrameCount = shifts.count { it != null }, reconstructionType = "drizzle_2x")
        } catch (t: Throwable) { output.recycle(); throw t }
    }
}
