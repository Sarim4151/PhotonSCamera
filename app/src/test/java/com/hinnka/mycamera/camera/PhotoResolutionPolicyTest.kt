package com.hinnka.mycamera.camera

import org.junit.Assert.*
import org.junit.Test

class PhotoResolutionPolicyTest {
    @Test fun onlyAdvertisedSizeCanBeSelected() {
        assertEquals("1920x1080", PhotoResolutionPolicy.select("1920x1080", listOf("4000x3000", "1920x1080")))
    }
    @Test fun staleLensOrFormatSelectionFallsBackToLargestWorkingStream() {
        assertEquals("4000x3000", PhotoResolutionPolicy.select("8000x6000", listOf("4000x3000", "1920x1080")))
        assertNull(PhotoResolutionPolicy.select("8000x6000", emptyList()))
    }
    @Test fun autoUsesLargestAdvertisedStream() {
        assertEquals("4000x3000", PhotoResolutionPolicy.select(null, listOf("4000x3000", "1920x1080")))
    }
    @Test fun autoTwoTimesUsesAdvertisedBinnedSensorStream() {
        val sizes = listOf("8000x6000", "4000x3000", "1920x1080")
        assertEquals("4000x3000", PhotoResolutionPolicy.selectForUpscale(null, sizes, true))
        assertEquals("8000x6000", PhotoResolutionPolicy.selectForUpscale(null, sizes, false))
        assertEquals("8000x6000", PhotoResolutionPolicy.selectForUpscale("8000x6000", sizes, true))
        assertEquals("4000x3000", PhotoResolutionPolicy.selectForUpscale(null, listOf("4000x3000"), true))
    }
    @Test fun allocationIncludesScratchAndRejectsOverflow() {
        assertTrue(PhotoResolutionPolicy.canAllocate2x(4000, 3000, 768L * 1024 * 1024))
        assertFalse(PhotoResolutionPolicy.canAllocate2x(8000, 6000, 512L * 1024 * 1024))
        assertFalse(PhotoResolutionPolicy.canAllocate2x(8000, 6000, Long.MAX_VALUE))
        assertTrue(PhotoResolutionPolicy.requiresStreaming2x(8000, 6000))
        assertFalse(PhotoResolutionPolicy.requiresStreaming2x(4000, 3000))
        assertFalse(PhotoResolutionPolicy.requiresStreaming2x(33000, 1000))
        assertFalse(PhotoResolutionPolicy.canAllocate2x(4000, 3000, 512L * 1024 * 1024))
        assertFalse(PhotoResolutionPolicy.canAllocate2x(4000, 3000, 200_000_000L))
        assertFalse(PhotoResolutionPolicy.canAllocate2x(Int.MAX_VALUE, Int.MAX_VALUE, Long.MAX_VALUE))
    }
    @Test fun disabledRecipeDoesNotUpscale() {
        assertEquals(PhotoResolutionPolicy.FinishAction.NONE, PhotoResolutionPolicy.finishAction(false, 1f, false))
    }
    @Test fun alreadyTwoTimesUpscaledRecipeIsNotScaledAgain() {
        assertEquals(PhotoResolutionPolicy.FinishAction.NONE, PhotoResolutionPolicy.finishAction(true, 2f, false))
    }
    @Test fun fractionalExistingUpscaleIsAnExplicitConflict() {
        assertEquals(PhotoResolutionPolicy.FinishAction.SCALE_CONFLICT, PhotoResolutionPolicy.finishAction(true, 1.5f, false))
    }
    @Test fun hdrIsRejectedBeforeAllocation() {
        assertEquals(PhotoResolutionPolicy.FinishAction.HDR_UNSUPPORTED, PhotoResolutionPolicy.finishAction(true, 1f, true))
    }
    @Test fun nativeSdrUsesFinishUpscale() {
        assertEquals(PhotoResolutionPolicy.FinishAction.UPSCALE, PhotoResolutionPolicy.finishAction(true, 1f, false))
    }
    @Test fun finishUpscaleUsesNativeCaptureInsteadOfFractionalIntermediate() {
        assertEquals(1f, PhotoResolutionPolicy.captureScaleForFinish2x(1.5f, true), 0f)
        assertEquals(1.5f, PhotoResolutionPolicy.captureScaleForFinish2x(1.5f, false), 0f)
    }
}
