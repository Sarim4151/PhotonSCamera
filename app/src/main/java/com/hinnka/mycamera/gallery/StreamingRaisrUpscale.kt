package com.hinnka.mycamera.gallery

import android.content.Context
import android.graphics.Bitmap
import com.hinnka.mycamera.raw.MgcRaisrUpscale
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Full-sensor 2× JPEG without ever constructing a full-size output Bitmap.
 * The existing native RAISR tile kernel receives 16 source pixels of context
 * around each core, then a scanline encoder consumes one output band at a time.
 * Core origins and halos are even so RAISR's 2-pixel filter phase is stable.
 */
internal object StreamingRaisrUpscale {
    private const val CORE_WIDTH = 512
    private const val CORE_HEIGHT = 128
    private const val HALO = 16

    private data class Tile(val left: Int, val width: Int, val pixels: IntArray)

    fun write(context: Context, source: Bitmap, output: File, quality: Int): Boolean {
        val outputWidth = source.width.toLong() * 2L
        val outputHeight = source.height.toLong() * 2L
        require(outputWidth in 1..StreamingJpegEncoder.MAX_DIMENSION.toLong() &&
            outputHeight in 1..StreamingJpegEncoder.MAX_DIMENSION.toLong())
        val raisr = MgcRaisrUpscale()
        val workspace = ByteBuffer.allocateDirect(
            (CORE_WIDTH + 2 * HALO) * (CORE_HEIGHT + 2 * HALO) * 4 * Float.SIZE_BYTES
        ).order(ByteOrder.nativeOrder())
        val sourceRow = IntArray(CORE_WIDTH + 2 * HALO)
        var bandTop = -1
        var tiles: List<Tile> = emptyList()
        return StreamingJpegEncoder.write(context, output, outputWidth.toInt(), outputHeight.toInt(), quality) { y, row ->
            val targetBand = y / (CORE_HEIGHT * 2) * CORE_HEIGHT
            if (targetBand != bandTop) {
                val coreHeight = minOf(CORE_HEIGHT, source.height - targetBand)
                tiles = (0 until source.width step CORE_WIDTH).map { coreLeft ->
                    val coreWidth = minOf(CORE_WIDTH, source.width - coreLeft)
                    val workLeft = maxOf(0, coreLeft - HALO)
                    val workTop = maxOf(0, targetBand - HALO)
                    val workRight = minOf(source.width, coreLeft + coreWidth + HALO)
                    val workBottom = minOf(source.height, targetBand + coreHeight + HALO)
                    val workWidth = workRight - workLeft
                    val workHeight = workBottom - workTop
                    workspace.clear()
                    val floats = workspace.asFloatBuffer()
                    for (sourceY in workTop until workBottom) {
                        source.getPixels(sourceRow, 0, workWidth, workLeft, sourceY, workWidth, 1)
                        for (x in 0 until workWidth) {
                            val pixel = sourceRow[x]
                            floats.put(((pixel ushr 16) and 255) / 255f)
                            floats.put(((pixel ushr 8) and 255) / 255f)
                            floats.put((pixel and 255) / 255f)
                            floats.put(1f)
                        }
                    }
                    val bitmap = raisr.upscaleTile(workspace, workWidth, workHeight,
                        coreLeft - workLeft, targetBand - workTop, coreWidth, coreHeight, 2f)
                        ?: error("RAISR tile failed at $coreLeft,$targetBand")
                    try {
                        val tilePixels = IntArray(bitmap.width * bitmap.height)
                        bitmap.getPixels(tilePixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                        Tile(coreLeft * 2, bitmap.width, tilePixels)
                    } finally {
                        bitmap.recycle()
                    }
                }
                bandTop = targetBand
            }
            val localY = y - bandTop * 2
            for (tile in tiles) {
                System.arraycopy(tile.pixels, localY * tile.width, row, tile.left, tile.width)
            }
        }
    }
}
