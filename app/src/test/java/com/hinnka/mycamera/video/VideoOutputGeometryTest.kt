package com.hinnka.mycamera.video

import android.util.Size
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoOutputGeometryTest {
    @Test fun encoderRasterUsesCameraBufferAxes() {
        assertEquals(Size(1280, 720), VideoResolutionPreset.HD_720P.resolveOutputSize(9f / 16f))
        assertEquals(Size(1920, 1072), VideoResolutionPreset.FHD_1080P.resolveOutputSize(9f / 16f))
        assertEquals(Size(3840, 2160), VideoResolutionPreset.UHD_2160P.resolveOutputSize(9f / 16f))
    }

    @Test fun openGateEncoderPreservesLandscapeSensorAspect() {
        val config = VideoConfig(resolution = VideoResolutionPreset.MAX,
            aspectRatio = VideoAspectRatio.OPEN_GATE)
        assertEquals(Size(4000, 2992), config.resolveOutputSize(.75f, Size(4000, 3000)))
    }

    @Test fun ultrawide4kKeepsLandscapeEncoderAxes() {
        val size = VideoResolutionPreset.UHD_2160P.resolveOutputSize(9f / 21f)
        assertEquals(3840, size.width)
        assertEquals(1632, size.height)
    }
}
