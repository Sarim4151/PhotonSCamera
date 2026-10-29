package com.hinnka.mycamera.camera

/** Advisory computed from the displayed preview, never from sensor RAW highlight headroom. */
enum class PhotoQualitySignal { SOURCE_RESOLUTION_LIMITED, PREVIEW_HIGHLIGHTS_CLIPPING }

object PhotoQualitySignals {
    fun evaluate(state: CameraState): PhotoQualitySignal? {
        if (state.sourceResolutionFallback) return PhotoQualitySignal.SOURCE_RESOLUTION_LIMITED
        val bins = state.histogram ?: return null
        if (bins.size != 256) return null
        val total = bins.sumOf { it.toLong().coerceAtLeast(0L) }
        if (total == 0L) return null
        val clipped = (250..255).sumOf { bins[it].toLong().coerceAtLeast(0L) }
        return if (clipped * 100 >= total) PhotoQualitySignal.PREVIEW_HIGHLIGHTS_CLIPPING else null
    }
}
