package com.hinnka.mycamera.video

/** Renderer input/submission diagnostics, not a sensor/RAW frame-loss measurement. */
data class VideoMonitoringStats(
    val inputFps: Float = 0f,
    val submittedFps: Float = 0f,
    val totalQueueReplacements: Long = 0L,
    val renderMs: Float = 0f,
    val frameGapDetected: Boolean = false,
)
