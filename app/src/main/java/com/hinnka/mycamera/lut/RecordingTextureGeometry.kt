package com.hinnka.mycamera.lut

/**
 * Remove the sensor-to-natural-display rotation from a producer sampling matrix.
 *
 * The encoder is allocated in sensor-buffer axes. Camera2's OES transform includes
 * sensor presentation rotation, as does the enhanced YUV presentation matrix. Applying
 * either unchanged to a landscape encoder stretches a portrait frame across it.
 * Post-multiply by the inverse cardinal rotation about the texture centre, preserving
 * the producer's crop, texel inset and OES vertical-origin flip. MP4 metadata then
 * applies sensor/device/facing rotation exactly once. No Android GL dependency here.
 */
internal fun normalizeRecordingTextureTransform(source: FloatArray, sensorRotationDegrees: Int,
    result: FloatArray) {
    require(source.size >= 16 && result.size >= 16)
    require(sensorRotationDegrees % 90 == 0)
    val rotation = Math.floorMod(sensorRotationDegrees, 360)
    for (row in 0..3) {
        val x = source[row]
        val y = source[4 + row]
        val z = source[8 + row]
        val t = source[12 + row]
        result[8 + row] = z
        when (rotation) {
            0 -> { result[row] = x; result[4 + row] = y; result[12 + row] = t }
            90 -> { result[row] = -y; result[4 + row] = x; result[12 + row] = t + y }
            180 -> { result[row] = -x; result[4 + row] = -y; result[12 + row] = t + x + y }
            270 -> { result[row] = y; result[4 + row] = -x; result[12 + row] = t + x }
        }
    }
}
