package com.hinnka.mycamera.video

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoOrientationTest {
    @Test
    fun cameraSensorAndDeviceRotationAreIncludedInVideoMetadata() {
        assertEquals(90, resolveCameraVideoOrientationDegrees(90, 0, false, 0))
        assertEquals(180, resolveCameraVideoOrientationDegrees(90, 90, false, 0))
        assertEquals(0, resolveCameraVideoOrientationDegrees(90, 270, false, 0))
        assertEquals(270, resolveCameraVideoOrientationDegrees(270, 0, true, 0))
        assertEquals(180, resolveCameraVideoOrientationDegrees(270, 90, true, 0))
    }
    @Test
    fun portraitDoesNotReapplySensorOrientation() {
        assertEquals(0, resolveSurfaceTextureVideoOrientationDegrees(0, 0))
    }

    @Test
    fun landscapeUsesDeviceDisplayRotation() {
        assertEquals(90, resolveSurfaceTextureVideoOrientationDegrees(90, 0))
        assertEquals(270, resolveSurfaceTextureVideoOrientationDegrees(270, 0))
    }

    @Test
    fun cameraCalibrationOffsetIsAppliedAndNormalized() {
        assertEquals(0, resolveSurfaceTextureVideoOrientationDegrees(270, 90))
        assertEquals(270, resolveSurfaceTextureVideoOrientationDegrees(0, -90))
    }
}
