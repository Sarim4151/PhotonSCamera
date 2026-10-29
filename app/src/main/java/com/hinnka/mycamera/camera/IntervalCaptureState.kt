package com.hinnka.mycamera.camera

/** Counts capture submissions, not files successfully saved by downstream processing. */
data class IntervalCaptureState(
    val isRunning: Boolean = false,
    val plannedSlots: Int = 0,
    val completedSlots: Int = 0,
    val acceptedCaptures: Int = 0,
    val skippedSlots: Int = 0,
)
