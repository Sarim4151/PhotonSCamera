package com.hinnka.mycamera.processor

import android.graphics.Bitmap
import kotlin.math.abs

/** Fixed-reference translation with tiled consensus so local subject motion remains in the stack. */
internal class PreviewFrameRegistration(bitmap: Bitmap) {
    data class Shift(val x: Int, val y: Int)
    private val width = bitmap.width
    private val height = bitmap.height
    private val reference = luminance(bitmap)
    private val coarseWidth = (width + 3) / 4
    private val coarseReference = downsample(reference)
    private val margin = maxOf(1, minOf(18, minOf(width, height) / 8))
    private val stride = maxOf(2, maxOf(width, height) / 96)
    private val textured = BooleanArray(16)

    init {
        val energy = FloatArray(16)
        val counts = IntArray(16)
        for (y in margin until height - margin step stride) for (x in margin until width - margin step stride) {
            val tile = (y * 4 / height) * 4 + x * 4 / width
            val at = y * width + x
            energy[tile] += abs(value(reference, at + 1) - value(reference, at - 1)) +
                abs(value(reference, at + width) - value(reference, at - width))
            counts[tile]++
        }
        for (i in textured.indices) textured[i] = counts[i] > 0 && energy[i] / counts[i] >= 6f
    }

    fun estimate(bitmap: Bitmap): Shift? {
        val current = luminance(bitmap)
        val coarseCurrent = downsample(current)
        val texturedCount = textured.count { it }
        // A featureless background cannot define camera motion. Keep stationary dark scenes usable
        // for light trails, but reject changes that cannot be registered reliably.
        if (texturedCount < 4) return if (score(current, 0, 0, false) <= 5f) Shift(0, 0) else null
        val radius = minOf(16, margin - 1).coerceAtLeast(0)
        var best = Shift(0, 0)
        var bestScore = score(current, 0, 0, true, coarseCurrent)
        for (dy in -radius..radius step 4) for (dx in -radius..radius step 4) {
            val candidate = score(current, dx, dy, true, coarseCurrent)
            if (candidate < bestScore) { bestScore = candidate; best = Shift(dx, dy) }
        }
        val coarse = best
        bestScore = score(current, best.x, best.y, true)
        for (dy in maxOf(-radius, coarse.y - 3)..minOf(radius, coarse.y + 3)) {
            for (dx in maxOf(-radius, coarse.x - 3)..minOf(radius, coarse.x + 3)) {
                val candidate = score(current, dx, dy, true)
                if (candidate < bestScore) { bestScore = candidate; best = Shift(dx, dy) }
            }
        }
        if (bestScore > 12f) return null
        // A second, spatially distinct fit means repeated texture or insufficient evidence.
        var second = Float.MAX_VALUE
        for (dy in -radius..radius step 4) for (dx in -radius..radius step 4) {
            if (abs(dx - best.x) > 3 || abs(dy - best.y) > 3)
                second = minOf(second, score(current, dx, dy, true))
        }
        if (second - bestScore < 1.5f) return null
        return best
    }

    private fun score(current: ByteArray, dx: Int, dy: Int, textureOnly: Boolean, coarse: ByteArray? = null): Float {
        val sums = FloatArray(16)
        val counts = IntArray(16)
        for (y in margin until height - margin step stride) for (x in margin until width - margin step stride) {
            val tile = (y * 4 / height) * 4 + x * 4 / width
            if (textureOnly && !textured[tile]) continue
            val sx = x + dx
            val sy = y + dy
            if (sx !in 0 until width || sy !in 0 until height) continue
            val difference = if (coarse == null) abs(value(reference, y * width + x) - value(current, sy * width + sx))
                else abs(value(coarseReference, (y / 4) * coarseWidth + x / 4) -
                    value(coarse, (sy / 4) * coarseWidth + sx / 4))
            sums[tile] += minOf(40, difference)
            counts[tile]++
        }
        val scores = sums.indices.filter { counts[it] > 0 }.map { sums[it] / counts[it] }.sorted()
        if (scores.isEmpty()) return Float.MAX_VALUE
        // Ignore the worst quarter of tiles (moving objects), retaining a background consensus.
        val kept = maxOf(1, scores.size * 3 / 4)
        return (0 until kept).sumOf { scores[it].toDouble() }.toFloat() / kept
    }

    private fun value(data: ByteArray, at: Int) = data[at].toInt() and 255

    private fun downsample(data: ByteArray): ByteArray {
        val result = ByteArray(coarseWidth * ((height + 3) / 4))
        for (y in 0 until height step 4) for (x in 0 until width step 4) {
            var sum = 0
            var count = 0
            for (sy in y until minOf(y + 4, height)) for (sx in x until minOf(x + 4, width)) {
                sum += value(data, sy * width + sx)
                count++
            }
            result[(y / 4) * coarseWidth + x / 4] = (sum / count).toByte()
        }
        return result
    }

    private fun luminance(bitmap: Bitmap): ByteArray {
        val result = ByteArray(width * height)
        val row = IntArray(width)
        for (y in 0 until height) {
            bitmap.getPixels(row, 0, width, 0, y, width, 1)
            for (x in row.indices) {
                val c = row[x]
                result[y * width + x] = (((c ushr 16 and 255) * 77 +
                    (c ushr 8 and 255) * 150 + (c and 255) * 29) ushr 8).toByte()
            }
        }
        return result
    }
}
