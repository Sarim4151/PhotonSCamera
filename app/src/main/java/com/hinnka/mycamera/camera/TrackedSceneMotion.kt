package com.hinnka.mycamera.camera

import kotlin.math.hypot

/** Motion of a detected target relative to the frame, not world-space speed or identity. */
internal class TrackedSceneMotion {
    private data class Sample(val x: Float, val y: Float, val label: String, val timeMs: Long)
    private var previous: Sample? = null
    private var filtered: Float? = null
    fun clear() { previous = null; filtered = null }
    fun observe(x: Float, y: Float, label: String, timeMs: Long): Float? {
        if (!x.isFinite() || !y.isFinite() || x !in 0f..1f || y !in 0f..1f) { clear(); return null }
        val current = Sample(x, y, label, timeMs)
        val last = previous
        previous = current
        val delta = if (last == null) 0L else timeMs - last.timeMs
        if (last == null || last.label != label || delta !in 100L..900L) { filtered = null; return null }
        val distance = hypot(x - last.x, y - last.y)
        // A remote jump can be a new subject; never interpret it as a motion measurement.
        if (distance > .3f) { filtered = null; return null }
        val speed = distance * 1000f / delta
        val result = filtered?.let { .4f * it + .6f * speed } ?: speed
        filtered = result
        return result
    }
}
