package com.hinnka.mycamera.precapture

import android.content.Context
import android.os.SystemClock
import com.hinnka.mycamera.gallery.GalleryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Disk-only ring. Completion time is a conservative upper bound on exposure time. */
internal class FullSourceRing(private val context: Context, val sessionId: String) {
    data class Frame(val id: String, val completedNanos: Long, val bytes: Long)
    private val mutex = Mutex()
    private val admission = Any()
    @Volatile private var frames: List<Frame> = emptyList()
    @Volatile private var closed = false
    @Volatile var stopNanos: Long? = null
        private set
    fun stop() { if (stopNanos == null) stopNanos = SystemClock.elapsedRealtimeNanos() }
    /** Synchronous admission barrier; session owner also cleans captures still in the camera. */
    fun close() { synchronized(admission) { closed = true; stop() } }
    fun snapshot(): List<Frame> = frames
    suspend fun accept(id: String, completed: Long): Boolean = withContext(Dispatchers.IO + NonCancellable) {
        mutex.withLock {
            val metadata = GalleryManager.loadMetadata(context, id)
            check(metadata?.customProperties?.get("addMeSessionId") == sessionId) { "Invalid private capture session" }
            val file = GalleryManager.getOriginalImageFile(context, id)
            check(file != null && file.length() > 0) { "Missing full source" }
            val frame = Frame(id, completed, file.length())
            if (closed || stopNanos?.let { completed >= it } == true || frame.bytes > MAX_BYTES) {
                delete(frame)
                false
            } else {
                val next = (frames + frame).toMutableList()
                while (next.isNotEmpty() && (next.size > 12 || next.sumOf { it.bytes } > MAX_BYTES ||
                        completed - next.first().completedNanos > 10_000_000_000L)) delete(next.removeAt(0))
                // close() can happen during suspended metadata/deletion work.
                val admitted = synchronized(admission) {
                    if (closed) false else { frames = next.toList(); true }
                }
                if (!admitted) {
                    next.forEach { delete(it) }
                    frames = emptyList()
                }
                admitted
            }
        }
    }
    suspend fun clear() = withContext(Dispatchers.IO + NonCancellable) {
        close()
        mutex.withLock {
            frames.forEach { delete(it) }
            frames = emptyList()
        }
    }
    private suspend fun delete(frame: Frame) {
        if (GalleryManager.loadMetadata(context, frame.id)?.customProperties?.get("addMeSessionId") == sessionId)
            GalleryManager.deletePhotoOnly(context, frame.id)
    }
    companion object { const val MAX_BYTES = 96L * 1024 * 1024 }
}
