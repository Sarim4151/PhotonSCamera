package com.hinnka.mycamera.video

import org.junit.Assert.*
import org.junit.Test

class VideoStreamEligibilityTest {
    private fun select(sizes: List<Pair<Int, Int>>, minimumLongEdge: Int? = null) =
        VideoStreamEligibility.findOpenGateInput(sizes, 0.75f, minimumLongEdge, { it.first }, { it.second })

    @Test fun croppedStreamsDoNotMasqueradeAsOpenGate() {
        assertNull(select(listOf(3840 to 2160, 1920 to 1080)))
    }

    @Test fun maxUsesLargestEligibleSensorAspectStream() {
        assertEquals(4000 to 3000, select(listOf(1920 to 1440, 4000 to 3000, 7680 to 4320)))
    }

    @Test fun presetNeedsEnoughInputPixelsAndChoosesSmallestCoveringStream() {
        val sizes = listOf(1440 to 1080, 2560 to 1920, 4000 to 3000)
        assertEquals(2560 to 1920, select(sizes, 1920))
        assertNull(select(sizes, 5000))
    }

    @Test fun slowSensorModesCannotAdvertiseOptimisticFrameRates() {
        assertFalse(VideoStreamEligibility.supportsFps(66_666_667L, 24))
        assertFalse(VideoStreamEligibility.supportsFps(33_333_334L, 60))
        assertTrue(VideoStreamEligibility.supportsFps(33_333_334L, 30))
    }
}
