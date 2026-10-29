package com.hinnka.mycamera.video

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build

/** Select the very encoder whose size and frame-rate support was advertised. */
internal object VideoEncoderSelector {
    fun find(mime: String, width: Int, height: Int, fps: Int): MediaCodecInfo? = try {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.asSequence()
            .filter { it.isEncoder && !(Build.VERSION.SDK_INT >= 29 && it.isAlias) }
            .filter { info -> info.supportedTypes.any { it.equals(mime, ignoreCase = true) } }
            .filter { info ->
                val capabilities = info.getCapabilitiesForType(mime)
                capabilities.colorFormats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface) &&
                    capabilities.videoCapabilities.areSizeAndRateSupported(width, height, fps.toDouble())
            }
            .sortedBy { if (Build.VERSION.SDK_INT >= 29 && it.isHardwareAccelerated) 0 else 1 }
            .firstOrNull()
    } catch (_: Exception) { null }
}
