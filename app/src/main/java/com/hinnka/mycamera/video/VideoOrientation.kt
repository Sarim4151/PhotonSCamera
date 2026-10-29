package com.hinnka.mycamera.video

/**
 * The enhanced-stabilization YUV presentation path has already applied the sensor rotation.
 * Its MP4 hint therefore carries only the capture orientation and optional calibration offset.
 */
internal fun resolveSurfaceTextureVideoOrientationDegrees(
    deviceRotationDegrees: Int,
    calibrationOffsetDegrees: Int,
): Int {
    return Math.floorMod(deviceRotationDegrees + calibrationOffsetDegrees, 360)
}

/** Rotation metadata for an unrotated Camera2 sensor stream encoded in sensor-buffer axes. */
internal fun resolveCameraVideoOrientationDegrees(
    sensorOrientationDegrees: Int,
    deviceRotationDegrees: Int,
    frontFacing: Boolean,
    calibrationOffsetDegrees: Int,
): Int = Math.floorMod(
    sensorOrientationDegrees + (if (frontFacing) -deviceRotationDegrees else deviceRotationDegrees) +
        calibrationOffsetDegrees,
    360,
)
