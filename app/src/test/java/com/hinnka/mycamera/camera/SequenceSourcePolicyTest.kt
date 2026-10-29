package com.hinnka.mycamera.camera

import org.junit.Assert.*
import org.junit.Test

class SequenceSourcePolicyTest {
    @Test fun forcedFlashLivePhotoUsesOneStillAndKeepsVideoAndNextShotSettings() {
        val original = CameraState(multiFrameOutputScale = 1f, useRaw = false,
            useLivePhoto = true, iso = 320, flashMode = android.hardware.camera2.CameraMetadata.FLASH_MODE_SINGLE)
        assertTrue(original.requiresMultiFrameCaptureSequence)
        val flashed = original.forSingleFlashStill()
        assertFalse(flashed.requiresMultiFrameCaptureSequence)
        assertTrue(flashed.useLivePhoto)
        assertEquals(original.flashMode, flashed.flashMode)
        assertEquals(original.iso, flashed.iso)
        assertTrue(original.requiresMultiFrameCaptureSequence)
    }

    @Test fun outerStackGetsOneSourceWithoutMutatingCameraSettings() {
        val original = CameraState(multiFrameOutputScale = 1f, useRaw = false,
            useLivePhoto = true, useMultipleExposure = true, burstCapturing = true,
            hdrBracketCapturing = true, hdrBracketFrameCount = 3, iso = 640, zoomRatio = 2f)
        assertTrue(original.isMultiFrameEnabled)
        val source = original.forSequenceSource()
        assertFalse(source.isMultiFrameEnabled)
        assertFalse(source.requiresMultiFrameCaptureSequence)
        assertFalse(source.useLivePhoto)
        assertFalse(source.useMultipleExposure)
        assertFalse(source.burstCapturing)
        assertFalse(source.hdrBracketCapturing)
        assertEquals(0, source.hdrBracketFrameCount)
        assertEquals(640, source.iso)
        assertEquals(2f, source.zoomRatio)
        assertTrue(original.useLivePhoto)
        assertEquals(1f, original.multiFrameOutputScale)
    }
}
