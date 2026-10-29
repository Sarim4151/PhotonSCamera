package com.hinnka.mycamera.ui.gallery.detail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.ColorSpace
import android.graphics.Rect
import com.hinnka.mycamera.gallery.StreamingJpegEncoder
import java.io.Closeable
import java.io.File
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking

/** Owns an encoded rendered look, never the gallery's borrowed bitmap cache. */
class DetailRegionSource(val file: File) : Closeable {
    private val decoder = requireNotNull(BitmapRegionDecoder.newInstance(file.absolutePath, false))
    val width = decoder.width
    val height = decoder.height
    init {
        if (width !in 1..StreamingJpegEncoder.MAX_DIMENSION || height !in 1..StreamingJpegEncoder.MAX_DIMENSION) {
            decoder.recycle(); file.delete()
            error("Unsupported full-size JPEG dimensions")
        }
    }
    @Synchronized fun decode(rect: Rect): Bitmap {
        check(!decoder.isRecycled)
        return requireNotNull(decoder.decodeRegion(rect, BitmapFactory.Options().apply {
            inSampleSize = 1
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
        }))
    }
    @Synchronized override fun close() { if (!decoder.isRecycled) decoder.recycle(); file.delete() }

    /** A 128-row strip plus a single 512x128 tile with a 32px processing halo. */
    suspend fun export(context: Context, target: File, render: suspend (Bitmap) -> Bitmap) {
        require(width in 1..StreamingJpegEncoder.MAX_DIMENSION && height in 1..StreamingJpegEncoder.MAX_DIMENSION)
        val job = currentCoroutineContext()
        val stripHeight = 128
        val strip = IntArray(Math.multiplyExact(width, stripHeight))
        var stripTop = -1
        check(StreamingJpegEncoder.write(context, target, width, height, 100) { row, pixels ->
            job.ensureActive()
            val top = row / stripHeight * stripHeight
            if (top != stripTop) {
                val bottom = minOf(height, top + stripHeight)
                var left = 0
                while (left < width) {
                    job.ensureActive()
                    val right = minOf(width, left + 512)
                    val region = Rect(maxOf(0, left - 32), maxOf(0, top - 32),
                        minOf(width, right + 32), minOf(height, bottom + 32))
                    val input = decode(region)
                    var output: Bitmap? = null
                    try {
                        val processed = runBlocking { render(input) }
                        output = processed
                        check(processed.width == input.width && processed.height == input.height)
                        processed.getPixels(strip, left, width, left - region.left, top - region.top,
                            right - left, bottom - top)
                    } finally { if (output !== input) output?.recycle(); input.recycle() }
                    left = right
                }
                stripTop = top
            }
            strip.copyInto(pixels, 0, (row - top) * width, (row - top + 1) * width)
        })
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(target.absolutePath, bounds)
        check(bounds.outWidth == width && bounds.outHeight == height)
    }
}
