package com.hinnka.mycamera.processor

import android.graphics.Bitmap
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import kotlin.math.*

/** Readable rendered pixels; native sources are spooled once instead of retaining a burst in RAM. */
internal interface SequenceFrame {
    val width: Int
    val height: Int
    fun getPixels(pixels: IntArray, offset: Int, stride: Int, x: Int, y: Int, width: Int, height: Int)
}
internal class BitmapSequenceFrame(private val bitmap: Bitmap) : SequenceFrame {
    override val width get() = bitmap.width
    override val height get() = bitmap.height
    override fun getPixels(pixels: IntArray, offset: Int, stride: Int, x: Int, y: Int, width: Int, height: Int) =
        bitmap.getPixels(pixels, offset, stride, x, y, width, height)
}
internal class DiskSequenceFrame private constructor(private val file: File,
    override val width: Int, override val height: Int) : SequenceFrame, Closeable {
    private val input = RandomAccessFile(file, "r")
    override fun getPixels(pixels: IntArray, offset: Int, stride: Int, x: Int, y: Int, width: Int, height: Int) {
        require(x >= 0 && y >= 0 && width > 0 && height > 0 && x + width <= this.width && y + height <= this.height)
        val bytes = ByteArray(width * 4); val ints = ByteBuffer.wrap(bytes).asIntBuffer()
        for (row in 0 until height) {
            input.seek(((y + row).toLong() * this.width + x) * 4L); input.readFully(bytes)
            ints.position(0); ints.get(pixels, offset + row * stride, width)
        }
    }
    override fun close() { try { input.close() } finally { file.delete() } }
    companion object {
        suspend fun write(file: File, bitmap: Bitmap): DiskSequenceFrame {
            try {
                RandomAccessFile(file, "rw").use { output ->
                    val row = IntArray(bitmap.width); val bytes = ByteArray(bitmap.width * 4)
                    val ints = ByteBuffer.wrap(bytes).asIntBuffer()
                    for (y in 0 until bitmap.height) {
                        currentCoroutineContext().ensureActive()
                        bitmap.getPixels(row, 0, row.size, 0, y, row.size, 1)
                        ints.position(0); ints.put(row); output.write(bytes)
                    }
                }
                return DiskSequenceFrame(file, bitmap.width, bitmap.height)
            } catch (failure: Throwable) { file.delete(); throw failure }
        }
    }
}

/** Bounded registration/ranking lattice only; output fusion always reads native source rows. */
internal suspend fun SequenceFrame.registrationBitmap(maxEdge: Int = 512): Bitmap {
    val step = max(1, ceil(max(width, height).toDouble() / maxEdge).toInt())
    val w = (width + step - 1) / step; val h = (height + step - 1) / step
    val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    try {
        val stripe = IntArray(width * step); val sampled = IntArray(w)
        for (y in 0 until h) {
            currentCoroutineContext().ensureActive()
            val rows = min(step, height - y * step)
            getPixels(stripe, 0, width, 0, y * step, width, rows)
            for (x in 0 until w) {
                val columns = min(step, width - x * step)
                var r = 0L; var g = 0L; var b = 0L
                for (yy in 0 until rows) for (xx in 0 until columns) {
                    val color = stripe[yy * width + x * step + xx]
                    r += (color ushr 16) and 255; g += (color ushr 8) and 255; b += color and 255
                }
                val count = rows * columns
                sampled[x] = (0xff shl 24) or ((r / count).toInt() shl 16) or
                    ((g / count).toInt() shl 8) or (b / count).toInt()
            }
            output.setPixels(sampled, 0, w, 0, y, w, 1)
        }
        return output
    } catch (failure: Throwable) { output.recycle(); throw failure }
}
