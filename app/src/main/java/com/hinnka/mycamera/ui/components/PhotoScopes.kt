package com.hinnka.mycamera.ui.components

import kotlin.math.roundToInt

/** Display-referred scopes: Rec.709 luma coefficients applied to encoded preview RGB.
 * These are not scene-linear luminance or a video legal-range measurement. */
internal data class PhotoScopeData(val waveform: IntArray, val vectors: IntArray, val bins: Int)

internal fun calculatePhotoScopes(pixels: IntArray, width: Int, height: Int, bins: Int = 64): PhotoScopeData {
    require(width > 0 && height > 0 && pixels.size == width * height && bins >= 2)
    val waveform = IntArray(bins * bins)
    val vectors = IntArray(bins * bins)
    for (y in 0 until height) for (x in 0 until width) {
        val p = pixels[y * width + x]
        if ((p ushr 24) == 0) continue
        val r = ((p ushr 16) and 255) / 255f
        val g = ((p ushr 8) and 255) / 255f
        val b = (p and 255) / 255f
        val luma = .2126f * r + .7152f * g + .0722f * b
        val cb = (b - luma) / 1.8556f
        val cr = (r - luma) / 1.5748f
        fun bin(v: Float) = (v.coerceIn(0f, 1f) * (bins - 1)).roundToInt()
        waveform[bin(1f - luma) * bins + if (width == 1) 0 else x * (bins - 1) / (width - 1)]++
        vectors[bin(.5f - cr) * bins + bin(cb + .5f)]++
    }
    return PhotoScopeData(waveform, vectors, bins)
}
