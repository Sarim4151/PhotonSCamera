package com.hinnka.mycamera.video

/**
 * For surfaces that are already encoded in natural-display orientation.
 * The dedicated camera recorder uses sensor-buffer axes and the sensor-aware helper below.
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
