package com.hinnka.mycamera.lut

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.pow

class PreviewIspToneCurveTest {
    private val identity = listOf(0f, 0f, 1f, 1f)

    @Test fun identityTablePreservesEveryEncodedLevel() {
        val table = PreviewIspToneCurve.inverseTable(identity, 4096)
        table.forEachIndexed { index, value -> assertEquals(index / 4095f, value, 0f) }
    }

    @Test fun differentChannelGammaTablesRoundTripReportedCurves() {
        val curves = listOf(0.45, 1.0, 2.2).map { gamma ->
            (0..256).flatMap { listOf(it / 256f, (it / 256.0).pow(gamma).toFloat()) }
        }
        assertNotNull(PreviewIspToneCurve.validated(curves))
        curves.forEach { points ->
            val table = PreviewIspToneCurve.inverseTable(points, 4096)
            table.forEachIndexed { index, input ->
                // Independent forward interpolation verifies the inverse in the encoded domain.
                val lower = (input * 256).toInt().coerceIn(0, 255)
                val weight = (input - points[lower * 2]) * 256
                val encoded = points[lower * 2 + 1] * (1 - weight) + points[lower * 2 + 3] * weight
                assertEquals(index / 4095f, encoded, 0.000002f)
            }
        }
    }

    @Test fun flatPlateauHasDeterministicFiniteInverse() {
        val table = PreviewIspToneCurve.inverseTable(listOf(0f, 0f, .25f, .5f, .75f, .5f, 1f, 1f), 4097)
        assertEquals(.25f, table[2048], 0f)
        val clipped = PreviewIspToneCurve.inverseTable(listOf(0f, 0f, .75f, 1f, 1f, 1f), 4096)
        assertEquals(.75f, clipped.last(), 0f)
        assertTrue(table.all { it.isFinite() && it in 0f..1f })
        assertTrue((1 until table.size).all { table[it] >= table[it - 1] })
    }

    @Test fun missingMalformedOrOutOfRangeMetadataRequestsFallback() {
        assertNull(PreviewIspToneCurve.validated(null))
        assertNull(PreviewIspToneCurve.validated(listOf(identity)))
        val malformed = listOf(
            listOf(0f, 0f, 1f),
            listOf(0f, 0f, .5f, .8f, .4f, .9f, 1f, 1f),
            listOf(0f, 0f, .5f, .8f, 1f, .7f),
            listOf(0f, -.1f, 1f, 1f),
            listOf(0f, 0f, .5f, 1.1f, 1f, 1f),
            listOf(0f, 0f, -0.1f, .2f, 1f, 1f),
            listOf(0f, 0f, 1.1f, .2f, 1f, 1f),
            listOf(0f, 0f, .5f, Float.NaN, 1f, 1f),
            listOf(0f, 0f, .5f, Float.POSITIVE_INFINITY, 1f, 1f),
            listOf(.1f, 0f, 1f, 1f),
            listOf(0f, 0f, .9f, 1f),
        )
        malformed.forEach { assertNull(PreviewIspToneCurve.validated(listOf(identity, it, identity))) }
    }
}
