package com.hinnka.mycamera.processor

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.*

/** Bundled on-device person probabilities; never a geometric substitute for segmentation. */
object PersonCompositeTools {
    data class Mask(val width: Int, val height: Int, val values: FloatArray) {
        fun at(x: Float, y: Float): Float {
            if (x !in 0f..1f || y !in 0f..1f) return 0f
            val fx=x*(width-1); val fy=y*(height-1)
            val ix=fx.toInt(); val iy=fy.toInt(); val jx=min(ix+1,width-1); val jy=min(iy+1,height-1)
            val a=fx-ix; val b=fy-iy
            return (values[iy*width+ix]*(1-a)+values[iy*width+jx]*a)*(1-b)+(values[jy*width+ix]*(1-a)+values[jy*width+jx]*a)*b
        }
    }
    data class Alignment(val dx: Float, val dy: Float, val residual: Float, val samples: Int) {
        val acceptable get() = samples >= 180 && residual < 25f
    }
    /** Keep the first exposure's group; import people only into its unoccupied background.
     * The returned mask stays in donor coordinates for the shared preview/full-size renderer.
     */
    fun addedPersonMask(first: Mask, second: Mask, alignment: Alignment): Mask {
        require(alignment.acceptable)
        val values = FloatArray(second.values.size)
        for (y in 0 until second.height) for (x in 0 until second.width) {
            val sx = x.toFloat() / (second.width - 1).coerceAtLeast(1)
            val sy = y.toFloat() / (second.height - 1).coerceAtLeast(1)
            val original = first.at(sx + alignment.dx, sy + alignment.dy)
            // Soft exclusion protects existing people, including uncertain segmentation edges.
            val unoccupied = ((.6f - original) / .45f).coerceIn(0f, 1f)
            values[y * second.width + x] = second.values[y * second.width + x] * unoccupied
        }
        return Mask(second.width, second.height, values)
    }
    fun preflight(width: Int, height: Int, includeRecipeProcessing: Boolean = false) {
        val pixels=width.toLong()*height
        require(width > 0 && height > 0 && pixels < Int.MAX_VALUE / 4)
        // API30+ software bitmap pixels live in native memory. Source decoding and recipe
        // rendering are sequential; full-size sources are staged to disk one at a time.
        val nativeNeeded = pixels * (if (includeRecipeProcessing) 12L else 4L) + 32L * 1024 * 1024
        val javaNeeded = width.toLong() * 64L + 16L * 1024 * 1024
        val budget = SequenceMemoryBudget.snapshot()
        require(budget.fits(nativeNeeded, javaNeeded)) {
            "Composite source needs native ${nativeNeeded / (1024 * 1024)} MiB and Java ${javaNeeded / (1024 * 1024)} MiB; available native ${budget.nativeBytes / (1024 * 1024)} MiB, Java ${budget.javaBytes / (1024 * 1024)} MiB"
        }
    }
    fun preflightOutput(width: Int, height: Int) {
        val pixels = width.toLong() * height
        require(width > 0 && height > 0 && pixels < Int.MAX_VALUE / 4)
        require(SequenceMemoryBudget.snapshot().fits(
            pixels * 4L + 16L * 1024 * 1024, width.toLong() * 32L + 8L * 1024 * 1024)) {
            "Full resolution output exceeds native bitmap or Java scanline memory"
        }
    }

    suspend fun segment(bitmap: Bitmap): Mask {
        val segmenter=Segmentation.getClient(SelfieSegmenterOptions.Builder().setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE).enableRawSizeMask().build())
        try {
            // ML Kit does not cancel its native reader when its coroutine is cancelled. Drain first.
            val result=withContext(NonCancellable) {
                suspendCoroutine<com.google.mlkit.vision.segmentation.SegmentationMask> { continuation ->
                    segmenter.process(InputImage.fromBitmap(bitmap,0))
                        .addOnSuccessListener { continuation.resume(it) }
                        .addOnFailureListener { continuation.resumeWithException(it) }
                }
            }
            currentCoroutineContext().ensureActive()
            val values=FloatArray(result.width*result.height)
            result.buffer.rewind(); result.buffer.asFloatBuffer().get(values)
            require(values.count { it > .6f } > values.size/500) { "No reliable person mask found" }
            return Mask(result.width,result.height,values)
        } finally { segmenter.close() }
    }
    /** Translation fit on background only. Residual rejects scene changes and rotation/parallax. */
    suspend fun align(first: Bitmap, second: Bitmap, firstMask: Mask, secondMask: Mask): Alignment = withContext(Dispatchers.Default) {
        require(first.width==second.width && first.height==second.height) { "Capture dimensions changed" }
        fun luminance(c:Int)=((c shr 16 and 255)*.299f+(c shr 8 and 255)*.587f+(c and 255)*.114f)
        // Area averages prevent coarse registration from aliasing fine texture into a false peak.
        val gridWidth = min(160, first.width)
        val gridHeight = min(120, first.height)
        suspend fun plane(bitmap: Bitmap): FloatArray {
            val values = FloatArray(gridWidth * gridHeight)
            val row = IntArray(bitmap.width)
            for (y in 0 until bitmap.height) {
                currentCoroutineContext().ensureActive()
                bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
                val gy = y * gridHeight / bitmap.height
                for (x in row.indices) values[gy * gridWidth + x * gridWidth / bitmap.width] += luminance(row[x])
            }
            for (gy in 0 until gridHeight) for (gx in 0 until gridWidth) {
                val columns = ((gx + 1) * bitmap.width + gridWidth - 1) / gridWidth - (gx * bitmap.width + gridWidth - 1) / gridWidth
                val rows = ((gy + 1) * bitmap.height + gridHeight - 1) / gridHeight - (gy * bitmap.height + gridHeight - 1) / gridHeight
                values[gy * gridWidth + gx] /= (columns * rows).toFloat()
            }
            return values
        }
        val firstPlane = plane(first); val secondPlane = plane(second)
        fun coarse(values: FloatArray, x: Float, y: Float): Float {
            val px = (x * gridWidth - .5f).coerceIn(0f, gridWidth - 1f)
            val py = (y * gridHeight - .5f).coerceIn(0f, gridHeight - 1f)
            val ix = px.toInt(); val iy = py.toInt()
            val nx = min(ix + 1, gridWidth - 1); val ny = min(iy + 1, gridHeight - 1)
            val fx = px - ix; val fy = py - iy
            return (values[iy * gridWidth + ix] * (1 - fx) + values[iy * gridWidth + nx] * fx) * (1 - fy) +
                (values[ny * gridWidth + ix] * (1 - fx) + values[ny * gridWidth + nx] * fx) * fy
        }
        fun score(tx: Float, ty: Float, smooth: Boolean): Alignment {
            var sum = 0f; var count = 0
            for (gy in 8 until 112 step 3) for (gx in 8 until 152 step 3) {
                val x = gx / 160f; val y = gy / 120f; val sx = x - tx; val sy = y - ty
                if (sx !in .02f.. .98f || sy !in .02f.. .98f || firstMask.at(x,y) > .08f || secondMask.at(sx,sy) > .08f) continue
                val u = if (smooth) coarse(firstPlane, x, y) else luminance(first.getPixel((x * first.width).toInt(), (y * first.height).toInt()))
                val v = if (smooth) coarse(secondPlane, sx, sy) else luminance(second.getPixel((sx * second.width).toInt(), (sy * second.height).toInt()))
                sum += abs(u - v); count++
            }
            return Alignment(tx, ty, if (count >= 180) sum / count else Float.MAX_VALUE, count)
        }
        fun better(candidate: Alignment, current: Alignment): Boolean = candidate.residual < current.residual - .0001f ||
            (abs(candidate.residual - current.residual) <= .0001f &&
                candidate.dx * candidate.dx + candidate.dy * candidate.dy < current.dx * current.dx + current.dy * current.dy)
        var best = Alignment(0f, 0f, Float.MAX_VALUE, 0)
        for (dy in -10..10) {
            currentCoroutineContext().ensureActive()
            for (dx in -10..10) {
                val candidate = score(dx / 160f, dy / 160f, true)
                if (better(candidate, best)) best = candidate
            }
        }
        // Refine every source pixel around the coarse fit, then validate on original detail.
        val centerX = (best.dx * first.width).roundToInt()
        val centerY = (best.dy * first.height).roundToInt()
        val radiusX = ceil(first.width / 160f).toInt().coerceAtLeast(1)
        val radiusY = ceil(first.height / 160f).toInt().coerceAtLeast(1)
        best = score(centerX.toFloat() / first.width, centerY.toFloat() / first.height, false)
        for (dy in centerY - radiusY..centerY + radiusY) {
            currentCoroutineContext().ensureActive()
            for (dx in centerX - radiusX..centerX + radiusX) {
                val candidate = score(dx.toFloat() / first.width, dy.toFloat() / first.height, false)
                if (better(candidate, best)) best = candidate
            }
        }
        best
    }
    /** Shared by the bounded review bitmap and full-source disk renderer. */
    internal suspend fun renderRows(
        width: Int, height: Int, mask: Mask, alignment: Alignment, roi: CompositeRegion,
        readFirst: (Int, IntArray) -> Unit,
        readSecond: (Int, IntArray) -> Unit,
        writeOutput: (Int, IntArray) -> Unit,
    ) {
        require(alignment.acceptable) { "Background changed; retake with a steady camera" }
        val row = IntArray(width)
        val donor = IntArray(width)
        for (y in 0 until height) {
            currentCoroutineContext().ensureActive()
            readFirst(y, row)
            val ny = (y + .5f) / height
            val sy = ny - alignment.dy - roi.dy
            if (sy in 0f.. .999999f) {
                readSecond((sy * height).toInt(), donor)
                for (x in row.indices) {
                    val nx = (x + .5f) / width
                    val sx = nx - alignment.dx - roi.dx
                    if (sx !in 0f.. .999999f) continue
                    val distance = max(abs(nx - roi.x) / (roi.width / 2), abs(ny - roi.y) / (roi.height / 2))
                    val edge = (roi.feather / min(roi.width / 2, roi.height / 2)).coerceAtLeast(.001f)
                    val crop = ((1 - distance) / edge).coerceIn(0f, 1f)
                    val probability = mask.at(sx, sy)
                    val alpha = ((probability - .15f) / .7f).coerceIn(0f, 1f).let { it * it * (3 - 2 * it) } * crop
                    if (alpha <= 0f) continue
                    val a = row[x]; val b = donor[(sx * width).toInt()]
                    fun blend(shift: Int) = (((a ushr shift and 255) * (1 - alpha) +
                        (b ushr shift and 255) * alpha).roundToInt()).coerceIn(0, 255)
                    row[x] = (255 shl 24) or (blend(16) shl 16) or (blend(8) shl 8) or blend(0)
                }
            }
            writeOutput(y, row)
        }
    }

    suspend fun render(first: Bitmap, second: Bitmap, mask: Mask, alignment: Alignment, roi: CompositeRegion): Bitmap {
        var pending: Bitmap? = null
        try { return withContext(Dispatchers.Default) {
            require(first.width == second.width && first.height == second.height) { "Capture dimensions changed" }
            val output = Bitmap.createBitmap(first.width, first.height, Bitmap.Config.ARGB_8888)
            pending = output
            renderRows(first.width, first.height, mask, alignment, roi,
                { y, row -> first.getPixels(row, 0, first.width, 0, y, first.width, 1) },
                { y, row -> second.getPixels(row, 0, second.width, 0, y, second.width, 1) },
                { y, row -> output.setPixels(row, 0, first.width, 0, y, first.width, 1) })
            output
        } } catch (failure: Throwable) {
            pending?.let { if (!it.isRecycled) it.recycle() }
            throw failure
        }
    }
}
