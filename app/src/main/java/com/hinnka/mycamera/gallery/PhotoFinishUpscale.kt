package com.hinnka.mycamera.gallery

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import com.hinnka.mycamera.camera.PhotoResolutionPolicy
import com.hinnka.mycamera.raw.MgcRaisrUpscale
import com.hinnka.mycamera.raw.RawOutputScaling
import java.io.IOException

/** A localized export failure which leaves the saved capture available for editing. */
internal class PhotoFinishUpscaleException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** Per-capture recipe, after the selected colour engine, LUT, crop and frame. */
internal object PhotoFinishUpscale {
    fun requested(properties: Map<String, String>) = properties["photoFinishUpscale"] == "true" || properties["enhancedZoom"] == "true"

    fun shouldStream(source: Bitmap, metadata: MediaMetadata, hasHdrGainmap: Boolean): Boolean {
        if (!requested(metadata.customProperties)) return false
        val priorScale = maxOf(RawOutputScaling.read(metadata.customProperties),
            metadata.customProperties["photoCaptureOutputScale"]?.toFloatOrNull() ?: 1f)
        return PhotoResolutionPolicy.finishAction(true, priorScale, hasHdrGainmap ||
            (android.os.Build.VERSION.SDK_INT >= 34 && source.hasGainmap())) ==
            PhotoResolutionPolicy.FinishAction.UPSCALE &&
            PhotoResolutionPolicy.requiresStreaming2x(source.width, source.height)
    }

    fun apply(context: Context, source: Bitmap, metadata: MediaMetadata, hasHdrGainmap: Boolean): Bitmap {
        if (!requested(metadata.customProperties)) return source
        val priorScale = maxOf(RawOutputScaling.read(metadata.customProperties),
            metadata.customProperties["photoCaptureOutputScale"]?.toFloatOrNull() ?: 1f)
        val hdr = hasHdrGainmap || (android.os.Build.VERSION.SDK_INT >= 34 && source.hasGainmap())
        when (PhotoResolutionPolicy.finishAction(true, priorScale, hdr)) {
            PhotoResolutionPolicy.FinishAction.NONE -> return source
            PhotoResolutionPolicy.FinishAction.HDR_UNSUPPORTED -> throw PhotoFinishUpscaleException(
                context.getString(com.hinnka.mycamera.R.string.photo_upscale_hdr_error))
            PhotoResolutionPolicy.FinishAction.SCALE_CONFLICT -> throw PhotoFinishUpscaleException(
                context.getString(com.hinnka.mycamera.R.string.photo_upscale_scale_error))
            PhotoResolutionPolicy.FinishAction.UPSCALE -> Unit
        }
        val memory = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
        // Output bitmap pixels live in native RAM; RAISR processes bounded bands.
        // Preserve system headroom while allowing large-memory devices to finish 2x.
        val budget = if (memory.lowMemory) 0L else
            (memory.availMem - maxOf(memory.threshold, 256L * 1024 * 1024))
                .coerceAtLeast(0L) * 3 / 4
        if (!PhotoResolutionPolicy.canAllocate2x(source.width, source.height, budget)) {
            throw PhotoFinishUpscaleException(context.getString(com.hinnka.mycamera.R.string.photo_upscale_memory_error))
        }
        // JNI locks pixels and reads half/byte channel values directly; it does not
        // perform Android getPixels' implicit conversion into sRGB.
        val result = try {
            MgcRaisrUpscale().upscale(source, 2f)
        } catch (failure: OutOfMemoryError) {
            throw PhotoFinishUpscaleException(context.getString(com.hinnka.mycamera.R.string.photo_upscale_memory_error), failure)
        } catch (failure: RuntimeException) {
            throw PhotoFinishUpscaleException(context.getString(com.hinnka.mycamera.R.string.photo_upscale_failed), failure)
        } ?: throw PhotoFinishUpscaleException(context.getString(com.hinnka.mycamera.R.string.photo_upscale_failed))
        try {
            source.colorSpace?.let { result.setColorSpace(it) }
        } catch (failure: IllegalArgumentException) {
            result.recycle()
            throw PhotoFinishUpscaleException(context.getString(com.hinnka.mycamera.R.string.photo_upscale_failed), failure)
        }
        return result
    }
}
