package com.hinnka.mycamera.gallery

import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.annotation.Keep
import java.io.File

/** Baseline sRGB 4:4:4 JPEG export with memory proportional to the image width.
 * The supplier fills one reusable row, in increasing y order. Exceptions (including
 * coroutine cancellation) propagate after native resources and partial output are released.
 * Does not package gain maps or convert wide-gamut pixels: supply display-referred sRGB.
 */
@Keep
object StreamingJpegEncoder {
    const val MAX_DIMENSION = 65500

    @Keep
    class RowSource(private val fill: (Int, IntArray) -> Unit) {
        @Keep
        fun readRow(y: Int, argb: IntArray) = fill(y, argb)
    }

    fun write(
        context: Context,
        outputFile: File,
        width: Int,
        height: Int,
        quality: Int = 100,
        fillRow: (y: Int, argb: IntArray) -> Unit,
    ): Boolean {
        require(width in 1..MAX_DIMENSION && height in 1..MAX_DIMENSION)
        require(quality in 1..100)
        val target = outputFile.canonicalFile
        val roots = listOf(context.cacheDir.canonicalFile, context.filesDir.canonicalFile)
        require(roots.any { target.path.startsWith(it.path + File.separator) })
        // Never truncate an existing capture, even when validation or loading fails.
        if (!Jpeg444ExportEncoder.isSupported || !target.createNewFile()) return false
        var complete = false
        try {
            val encoded = ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_WRITE_ONLY).use { descriptor ->
                encodeNative(descriptor.fd, width, height, quality, RowSource(fillRow))
            }
            complete = encoded && target.length() > 0L
            return complete
        } finally {
            if (!complete) target.delete()
        }
    }

    private external fun encodeNative(fd: Int, width: Int, height: Int, quality: Int, source: RowSource): Boolean
}
