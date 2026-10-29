package com.hinnka.mycamera.processor

import android.graphics.Bitmap
import com.hinnka.mycamera.camera.AdvancedCameraMode
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.pow

/** Owns incoming software bitmaps. All returned snapshots are independent, opaque sRGB images.
 * Preview integration registers small camera translations and cannot recover clipped highlights. */
class TemporalPreviewBuffer(private val mode: AdvancedCameraMode, private val seconds: Int = 3, private val memoryMb: Int = 32) {
    data class Snapshot(val bitmap: Bitmap, val wallTimeMs: Long, val elapsedMs: Long)
    private val mutex = Mutex()
    private val ring = ArrayDeque<Snapshot>()
    private var channels: FloatArray? = null
    private var samples: IntArray? = null
    private var registration: PreviewFrameRegistration? = null
    private var width = 0
    private var height = 0
    private var count = 0
    private var latestWall = 0L
    private var latestElapsed = 0L
    private val linear = FloatArray(256) {
        val v = it / 255f
        if (v <= .04045f) v / 12.92f else ((v + .055f) / 1.055f).pow(2.4f)
    }
    suspend fun offer(bitmap: Bitmap, wallTimeMs: Long, elapsedMs: Long): Int {
        var retained = false
        try { return mutex.withLock {
            currentCoroutineContext().ensureActive()
            require(bitmap.config != Bitmap.Config.HARDWARE && maxOf(bitmap.width, bitmap.height) <= 1536)
            if (count == 0) { width = bitmap.width; height = bitmap.height }
            require(bitmap.width == width && bitmap.height == height) { "Preview dimensions changed" }
            if (mode == AdvancedCameraMode.PRE_CAPTURE) {
                ring.addLast(Snapshot(bitmap, wallTimeMs, elapsedMs)); retained = true
                while (ring.size > maxOf(1, memoryMb.coerceIn(8, 128) * 1024 * 1024 / (width * height * 4)) || elapsedMs - ring.first().elapsedMs > seconds.coerceIn(1, 15) * 1000L) ring.removeFirst().bitmap.recycle()
                count++
                ring.size
            } else {
                val shift = if (count == 0) {
                    registration = PreviewFrameRegistration(bitmap)
                    PreviewFrameRegistration.Shift(0, 0)
                } else registration?.estimate(bitmap) ?: return@withLock count
                val data = channels ?: FloatArray(width * height * 3).also { channels = it }
                val weights = samples ?: IntArray(width * height).also { samples = it }
                val row = IntArray(width)
                for (y in 0 until height) {
                    currentCoroutineContext().ensureActive()
                    val sourceY = y + shift.y
                    if (sourceY !in 0 until height) continue
                    bitmap.getPixels(row, 0, width, 0, sourceY, width, 1)
                    for (x in row.indices) {
                        val sourceX = x + shift.x
                        if (sourceX !in 0 until width) continue
                        val at = (y * width + x) * 3
                        val c = row[sourceX]
                        val weight = (weights[y * width + x] + 1).coerceAtMost(1_000_000)
                        weights[y * width + x] = weight
                        for (ch in 0..2) {
                            val v = linear[(c ushr (16 - ch * 8)) and 255]
                            data[at + ch] = if (mode == AdvancedCameraMode.PREVIEW_COMPOSITE)
                                maxOf(data[at + ch], v) else data[at + ch] + (v - data[at + ch]) / weight
                        }
                    }
                }
                latestWall = wallTimeMs; latestElapsed = elapsedMs
                count = (count + 1).coerceAtMost(1_000_000)
                count
            }
        } } finally { if (!retained && !bitmap.isRecycled) bitmap.recycle() }
    }
    suspend fun snapshot(beforeElapsedMs: Long = Long.MAX_VALUE): Snapshot? = mutex.withLock {
        currentCoroutineContext().ensureActive()
        if (mode == AdvancedCameraMode.PRE_CAPTURE) {
            val selected = ring.lastOrNull { it.elapsedMs < beforeElapsedMs && beforeElapsedMs - it.elapsedMs <= seconds.coerceIn(1, 15) * 1000L }
                ?: return@withLock null
            val copy = selected.bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return@withLock null
            return@withLock selected.copy(bitmap = copy)
        }
        val data = channels ?: return@withLock null
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val row = IntArray(width)
            for (y in 0 until height) {
                currentCoroutineContext().ensureActive()
                for (x in row.indices) {
                    val at = (y * width + x) * 3
                    var color = 0xff000000.toInt()
                    for (ch in 0..2) {
                        val v = data[at + ch].coerceIn(0f, 1f)
                        val encoded = if (v <= .0031308f) v * 12.92f else 1.055f * v.pow(1f / 2.4f) - .055f
                        color = color or ((encoded * 255f + .5f).toInt().coerceIn(0, 255) shl (16 - ch * 8))
                    }
                    row[x] = color
                }
                bitmap.setPixels(row, 0, width, 0, y, width, 1)
            }
            Snapshot(bitmap, latestWall, latestElapsed)
        } catch (error: Throwable) { bitmap.recycle(); throw error }
    }
    suspend fun close() = mutex.withLock {
        ring.forEach { if (!it.bitmap.isRecycled) it.bitmap.recycle() }
        ring.clear(); channels = null; samples = null; registration = null; count = 0
    }
}
