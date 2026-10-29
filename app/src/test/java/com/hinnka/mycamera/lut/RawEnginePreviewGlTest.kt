package com.hinnka.mycamera.lut

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RawEnginePreviewGlTest {
    @Test fun previewEngineDoesNotDarkenFinishedEngineLookUsingOriginalIspLuma() {
        val sampling = RawEnginePreviewGl.SAMPLING_GLSL
        assertTrue(sampling.contains("linearToSrgb(clamp(mix(lo, hi, position.b - lower), 0.0, 1.0))"))
        assertFalse(sampling.contains("sourceLuma"))
        assertFalse(sampling.contains("ceiling"))
    }
}
