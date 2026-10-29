package com.hinnka.mycamera.camera

import android.graphics.Bitmap

enum class AdvancedCameraMode { SMART, PRE_CAPTURE, LIVE_COMPOSITE, LIGHT_GRAFFITI, LIVE_ND, NIGHT, ASTRO, STAR_TRAILS, MOTION_HDR, SOURCE_BURST, HIGH_DETAIL, PREVIEW_COMPOSITE, PREVIEW_ND }

data class AdvancedCameraState(
    val mode: AdvancedCameraMode? = null,
    val running: Boolean = false,
    val saving: Boolean = false,
    val frames: Int = 0,
    val total: Int = 0,
    val elapsedSeconds: Int = 0,
    val preview: Bitmap? = null,
    val status: String = "",
)

/** Preview buffering stores rendered JPEG-equivalent frames, never sensor RAW. */
data class AdvancedCaptureOptions(
    val bufferSeconds: Int = 3,
    val bufferFps: Int = 4,
    val bufferMaxEdge: Int = 1280,
    val bufferMemoryMb: Int = 32,
    val exposureSpacingEv: Float = 1.5f,
    val fuseExposureBracket: Boolean = true,
    val durationSeconds: Int = 60,
    val intensity: Float = 1f,
    /** 0 requests original sensor dimensions; positive values are an explicit input working edge. */
    val detailInputMaxEdge: Int = 0,
    /** Manual horizon: sky above, reference foreground below; null disables separation. */
    val astroHorizon: Float? = null,
)

data class AdvancedCaptureSelection(val mode: AdvancedCameraMode, val frames: Int, val gapSeconds: Int, val options: AdvancedCaptureOptions)
