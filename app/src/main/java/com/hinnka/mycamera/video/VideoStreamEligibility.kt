package com.hinnka.mycamera.video

import kotlin.math.abs

/** Stream metadata checks, independent of Camera2 and codec objects. */
internal object VideoStreamEligibility {
    fun supportsFps(minFrameDurationNs: Long, fps: Int): Boolean =
        minFrameDurationNs <= 0L || fps <= 1_000_000_000.0 / minFrameDurationNs + 0.01

    fun <T> findOpenGateInput(
        sizes: List<T>,
        targetPortraitAspect: Float,
        minimumLongEdge: Int?,
        width: (T) -> Int,
        height: (T) -> Int
    ): T? {
        val matching = sizes.filter {
            val short = minOf(width(it), height(it))
            val long = maxOf(width(it), height(it))
            short > 0 && long > 0 &&
                abs(short.toFloat() / long - targetPortraitAspect) <= 0.01f &&
                (minimumLongEdge == null || long >= minimumLongEdge)
        }
        return if (minimumLongEdge == null) {
            matching.maxByOrNull { width(it).toLong() * height(it) }
        } else {
            matching.minByOrNull { width(it).toLong() * height(it) }
        }
    }
}
