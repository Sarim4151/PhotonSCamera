package com.hinnka.mycamera.lut

/** Camera2 curve metadata is optional; malformed metadata must use the established fallback. */
internal object PreviewIspToneCurve {
    fun validated(reported: List<List<Float>>?): List<List<Float>>? = reported?.takeIf {
        it.size == 3 && it.all(::validChannel)
    }

    private fun validChannel(points: List<Float>): Boolean =
        points.size >= 4 && points.size % 2 == 0 &&
            points.all { it.isFinite() && it in 0f..1f } &&
            points.first() == 0f && points[points.size - 2] == 1f &&
            (2 until points.size step 2).all { i ->
                points[i] > points[i - 2] && points[i + 1] >= points[i - 1]
            }

    fun inverseTable(points: List<Float>, size: Int): FloatArray {
        require(validChannel(points)) { "Invalid ISP tone curve" }
        require(size >= 2)
        return FloatArray(size) { inverse(points, it.toFloat() / (size - 1)) }
    }

    // Earliest input at a plateau is deterministic; no inverse can recover clipped information.
    private fun inverse(points: List<Float>, output: Float): Float {
        if (output <= points[1]) return points[0]
        if (output > points.last()) return points[points.size - 2]
        var low = 1
        var high = points.size / 2 - 1
        while (low < high) {
            val middle = (low + high) / 2
            if (points[middle * 2 + 1] < output) low = middle + 1 else high = middle
        }
        val i = low * 2
        val span = points[i + 1] - points[i - 1]
        return if (span > 0f) points[i - 2] + (points[i] - points[i - 2]) *
            ((output - points[i - 1]) / span) else points[i - 2]
    }
}
