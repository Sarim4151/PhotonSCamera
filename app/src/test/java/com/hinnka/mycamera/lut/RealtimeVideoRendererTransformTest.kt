package com.hinnka.mycamera.lut

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeVideoRendererTransformTest {
    @Test fun quarterTurnTextureDoesNotCropLandscapeEncoderToPortraitStrip() {
        assertArrayEquals(floatArrayOf(0f, 0f, 1f, 1f),
            resolveRecordingCropRect(3840, 2160, 3840, 2160), 0f)
        // Exact CMF Phone 1 matrix from the 2026-10-29 device log. The old path
        // rotated portrait content into a 3840x2160 raster and tagged it as 0 degrees.
        val deviceTransform = floatArrayOf(0f, -1f, 0f, 0f, -1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)
        val sensorTransform = FloatArray(16)
        normalizeRecordingTextureTransform(deviceTransform, 90, sensorTransform)
        val verticalOriginFlip = floatArrayOf(1f, 0f, 0f, 0f, 0f, -1f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f, 1f, 0f, 1f)
        assertArrayEquals(verticalOriginFlip, sensorTransform, 0f)
        assertFalse(isTextureTransformAxesSwapped(sensorTransform))

        for (orientation in listOf(0, 90, 180, 270)) {
            val yuv = requireNotNull(resolveMgcEisPresentationMatrix(orientation))
            normalizeRecordingTextureTransform(yuv, orientation, sensorTransform)
            val identity = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
            assertArrayEquals(identity, sensorTransform, 0f)
            // OES keeps its vertical origin flip and per-frame producer crop.
            val oes = yuv.copyOf()
            for (column in 0..3) {
                oes[column * 4] = .8f * yuv[column * 4] + .1f * yuv[column * 4 + 3]
                oes[column * 4 + 1] = -.9f * yuv[column * 4 + 1] + .95f * yuv[column * 4 + 3]
            }
            normalizeRecordingTextureTransform(oes, orientation, sensorTransform)
            assertArrayEquals(floatArrayOf(.8f, 0f, 0f, 0f, 0f, -.9f, 0f, 0f,
                0f, 0f, 1f, 0f, .1f, .95f, 0f, 1f), sensorTransform, .000001f)
        }
    }
    @Test
    fun identityAndFlipDoNotSwapAxes() {
        assertFalse(
            isTextureTransformAxesSwapped(
                floatArrayOf(
                    1f, 0f, 0f, 0f,
                    0f, -1f, 0f, 0f,
                    0f, 0f, 1f, 0f,
                    0f, 1f, 0f, 1f,
                )
            )
        )
    }

    @Test
    fun quarterTurnSwapsAxes() {
        assertTrue(
            isTextureTransformAxesSwapped(
                floatArrayOf(
                    0f, 1f, 0f, 0f,
                    1f, 0f, 0f, 0f,
                    0f, 0f, 1f, 0f,
                    0f, 0f, 0f, 1f,
                )
            )
        )
    }

    @Test
    fun sensorOrientationBuildsRawYuvPresentationTransform() {
        assertMappedCorners(
            orientationDegrees = 0,
            bottomLeft = floatArrayOf(0f, 0f),
            bottomRight = floatArrayOf(1f, 0f),
            topLeft = floatArrayOf(0f, 1f),
        )
        assertMappedCorners(
            orientationDegrees = 90,
            bottomLeft = floatArrayOf(1f, 0f),
            bottomRight = floatArrayOf(1f, 1f),
            topLeft = floatArrayOf(0f, 0f),
        )
        assertMappedCorners(
            orientationDegrees = 180,
            bottomLeft = floatArrayOf(1f, 1f),
            bottomRight = floatArrayOf(0f, 1f),
            topLeft = floatArrayOf(1f, 0f),
        )
        assertMappedCorners(
            orientationDegrees = 270,
            bottomLeft = floatArrayOf(0f, 1f),
            bottomRight = floatArrayOf(0f, 0f),
            topLeft = floatArrayOf(1f, 1f),
        )
    }

    @Test
    fun sensorOrientationMustBeCardinal() {
        assertNull(resolveMgcEisPresentationMatrix(45))
    }

    private fun assertMappedCorners(
        orientationDegrees: Int,
        bottomLeft: FloatArray,
        bottomRight: FloatArray,
        topLeft: FloatArray,
    ) {
        val matrix = requireNotNull(resolveMgcEisPresentationMatrix(orientationDegrees))
        assertArrayEquals(bottomLeft, map(matrix, 0f, 0f), 0f)
        assertArrayEquals(bottomRight, map(matrix, 1f, 0f), 0f)
        assertArrayEquals(topLeft, map(matrix, 0f, 1f), 0f)
        assertTrue(isTextureTransformAxesSwapped(matrix) == (orientationDegrees % 180 != 0))
    }

    private fun map(matrix: FloatArray, x: Float, y: Float): FloatArray {
        return floatArrayOf(
            matrix[0] * x + matrix[4] * y + matrix[12],
            matrix[1] * x + matrix[5] * y + matrix[13],
        )
    }
}
