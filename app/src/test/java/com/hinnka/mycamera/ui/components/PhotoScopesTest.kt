package com.hinnka.mycamera.ui.components

import org.junit.Assert.*
import org.junit.Test

class PhotoScopesTest {
    @Test fun blackAndWhiteOccupyEndpointsAndNeutralChroma() {
        val s = calculatePhotoScopes(intArrayOf(0xff000000.toInt(), 0xffffffff.toInt()), 2, 1, 65)
        assertEquals(1, s.waveform[64 * 65])
        assertEquals(1, s.waveform[64])
        assertEquals(2, s.vectors[32 * 65 + 32])
        assertEquals(2, s.waveform.sum())
    }
    @Test fun primariesUse709LumaAndBoundedChroma() {
        val s = calculatePhotoScopes(intArrayOf(0xffff0000.toInt(), 0xff00ff00.toInt(), 0xff0000ff.toInt()), 3, 1, 101)
        assertEquals(1, s.waveform[79 * 101]) // red Y′ = .2126
        assertEquals(1, s.waveform[28 * 101 + 50]) // green Y′ = .7152
        assertEquals(1, s.waveform[93 * 101 + 100]) // blue Y′ = .0722
        assertEquals(3, s.vectors.sum())
        assertEquals(1, s.vectors[0 * 101 + 39]) // red Cr = +.5
        assertEquals(1, s.vectors[55 * 101 + 100]) // blue Cb = +.5
    }
    @Test fun transparentPixelsAreExcluded() {
        val s = calculatePhotoScopes(intArrayOf(0x00ffffff), 1, 1)
        assertEquals(0, s.waveform.sum())
        assertEquals(0, s.vectors.sum())
    }
}
