package com.hinnka.mycamera.processor

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Lossless sRGB ARGB rows. No image-sized array or memory mapping is retained. */
internal class CompositeArgbSpool private constructor(
    val width: Int, val height: Int, private val input: RandomAccessFile,
) : Closeable {
    private val bytes = ByteArray(Math.multiplyExact(width, 4))
    private val pixels = ByteBuffer.wrap(bytes).asIntBuffer()

    fun readRow(y: Int, destination: IntArray) {
        require(y in 0 until height && destination.size >= width)
        input.seek(HEADER_BYTES + y.toLong() * width * 4L)
        input.readFully(bytes)
        pixels.position(0)
        pixels.get(destination, 0, width)
    }
    override fun close() = input.close()

    companion object {
        private const val MAGIC = 0x41444D45
        private const val VERSION = 1
        private const val HEADER_BYTES = 16L

        suspend fun write(file: File, width: Int, height: Int, readRow: (Int, IntArray) -> Unit) {
            require(width > 0 && height > 0 && width.toLong() * height < Int.MAX_VALUE / 4)
            val length = HEADER_BYTES + width.toLong() * height * 4L
            require(file.parentFile!!.usableSpace > length + 32L * 1024 * 1024) { "Insufficient composite cache storage" }
            val pending = File(file.parentFile, file.name + ".pending")
            try {
                RandomAccessFile(pending, "rw").use { output ->
                    output.setLength(0)
                    output.writeInt(MAGIC); output.writeInt(VERSION)
                    output.writeInt(width); output.writeInt(height)
                    val row = IntArray(width)
                    val bytes = ByteArray(width * 4)
                    val buffer = ByteBuffer.wrap(bytes).asIntBuffer()
                    for (y in 0 until height) {
                        currentCoroutineContext().ensureActive()
                        readRow(y, row)
                        buffer.position(0); buffer.put(row)
                        output.write(bytes)
                    }
                    check(output.length() == length)
                    output.fd.sync()
                }
                check(!file.exists() && pending.renameTo(file)) { "Cannot publish composite cache" }
            } finally { pending.delete() }
        }

        fun open(file: File): CompositeArgbSpool {
            val input = RandomAccessFile(file, "r")
            try {
                require(input.readInt() == MAGIC && input.readInt() == VERSION)
                val width = input.readInt(); val height = input.readInt()
                require(width > 0 && height > 0 && width.toLong() * height < Int.MAX_VALUE / 4)
                require(input.length() == HEADER_BYTES + width.toLong() * height * 4L)
                return CompositeArgbSpool(width, height, input)
            } catch (e: Throwable) { input.close(); throw e }
        }
    }
}
