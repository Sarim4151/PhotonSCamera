package com.hinnka.mycamera.camera

/** Exposure/histogram advisor; does not infer subject motion from a single histogram. */
internal object SmartCaptureAdvisor {
    data class Plan(val mode: AdvancedCameraMode, val frames: Int, val reason: String)
    fun choose(iso: Int, exposureNs: Long, histogram: IntArray?, autoExposure: Boolean,
               exposureLocked: Boolean, thermalStatus: Int, requestedFrames: Int, frameMotion: Float? = null): Plan {
        val hot = thermalStatus >= 3
        val bins = histogram?.takeIf { it.size == 256 }
        val count = bins?.sumOf { it.coerceAtLeast(0).toLong() } ?: 0L
        val highlights = if (count > 0) bins!!.takeLast(4).sumOf { it.coerceAtLeast(0).toLong() }.toDouble() / count else 0.0
        val shadows = if (count > 0) bins!!.take(16).sumOf { it.coerceAtLeast(0).toLong() }.toDouble() / count else 0.0
        return when {
            hot -> Plan(AdvancedCameraMode.NIGHT, 3, "thermal_budget")
            frameMotion != null && frameMotion.isFinite() && frameMotion > .16f ->
                Plan(AdvancedCameraMode.NIGHT, 3, "tracked_frame_motion")
            iso >= 800 || exposureNs >= 40_000_000L -> Plan(AdvancedCameraMode.NIGHT, requestedFrames.coerceIn(3, 9), "low_light")
            autoExposure && !exposureLocked && highlights > .01 && shadows > .12 ->
                Plan(AdvancedCameraMode.MOTION_HDR, 3, "highlight_shadow_range")
            else -> Plan(AdvancedCameraMode.HIGH_DETAIL, requestedFrames.coerceIn(6, 9), "detail")
        }
    }
}
