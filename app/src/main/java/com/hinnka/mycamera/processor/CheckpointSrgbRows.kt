package com.hinnka.mycamera.processor

import kotlinx.coroutines.ensureActive
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.CoroutineContext
import kotlin.math.pow

/** Sequential full-size checkpoint decoding for the JPEG encoder. Storage is one checkpoint row
 * plus the encoder-owned ARGB scanline, independent of image height or accumulated frame count. */
internal class CheckpointSrgbRows(
    private val input: DataInputStream,
    private val width: Int,
    private val height: Int,
    private val cancellation: CoroutineContext,
) {
    private val bytes = ByteArray(Math.multiplyExact(width, 24))
    private val values = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
    var rowsRead: Int = 0
        private set

    fun fill(y: Int, argb: IntArray) {
        cancellation.ensureActive()
        require(y == rowsRead && y < height && argb.size >= width) { "Checkpoint rows must be consumed sequentially" }
        input.readFully(bytes)
        for (x in 0 until width) {
            if (x % 1024 == 0) cancellation.ensureActive()
            var pixel = 0xff000000.toInt()
            for (channel in 0..2) {
                // Each channel stores its reference float followed by the accumulated float.
                val accumulated = values.getFloat(x * 24 + channel * 8 + 4)
                require(accumulated.isFinite()) { "Invalid temporal checkpoint sample" }
                val linear = accumulated.coerceIn(0f, 1f)
                val encoded = if (linear <= .0031308f) linear * 12.92f
                    else 1.055f * linear.pow(1f / 2.4f) - .055f
                pixel = pixel or ((encoded * 255f + .5f).toInt().coerceIn(0, 255) shl (16 - channel * 8))
            }
            argb[x] = pixel
        }
        rowsRead++
    }
}
