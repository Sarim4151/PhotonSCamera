package com.hinnka.mycamera.camera

/** Approximate metres mapped to Camera2 diopters; this is not lens calibration. */
enum class SnapFocusDistance(val meters: Float?) {
    ONE_METER(1f), ONE_AND_HALF_METERS(1.5f), TWO_METERS(2f),
    TWO_AND_HALF_METERS(2.5f), FIVE_METERS(5f), INFINITY(null);

    fun dioptersFor(minimumFocusDistance: Float): Float? {
        if (!minimumFocusDistance.isFinite() || minimumFocusDistance <= 0f) return null
        val diopters = meters?.let { 1f / it } ?: 0f
        // Do not silently clamp a labelled distance to a different focus setting.
        return diopters.takeIf { it <= minimumFocusDistance }
    }
}
