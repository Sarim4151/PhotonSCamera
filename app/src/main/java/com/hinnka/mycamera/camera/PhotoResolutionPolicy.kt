package com.hinnka.mycamera.camera

/** Only advertised stream dimensions may become a native capture size. */
object PhotoResolutionPolicy {
    // The finish exporter still materializes one ARGB_8888 bitmap, plus its
    // source and encoder buffers. Larger results need a streaming tile encoder.
    const val MAX_BITMAP_FINISH_PIXELS: Long = 64_000_000L

    /** Large 2× outputs use scanline JPEG instead of a full output Bitmap. */
    fun requiresStreaming2x(width: Int, height: Int): Boolean =
        width > 0 && height > 0 && width.toLong() * 2L <= 65_500L &&
            height.toLong() * 2L <= 65_500L &&
            width.toLong() * height * 4L > MAX_BITMAP_FINISH_PIXELS

    fun select(requested: String?, advertised: List<String>): String? =
        requested?.takeIf { it in advertised } ?: advertised.firstOrNull()

    /** Prefer a real binned stream when its advertised full-resolution pair is twice each axis. */
    fun selectForUpscale(requested: String?, advertised: List<String>, enabled: Boolean): String? {
        if (!enabled || requested != null) return select(requested, advertised)
        val sizes = advertised.mapNotNull { label ->
            val parts = label.split('x')
            val width = parts.getOrNull(0)?.toIntOrNull()
            val height = parts.getOrNull(1)?.toIntOrNull()
            if (width != null && height != null && width > 0 && height > 0)
                Triple(label, width, height) else null
        }
        val available = sizes.map { it.second.toLong() to it.third.toLong() }.toSet()
        return sizes.filter { (it.second * 2L to it.third * 2L) in available }
            .maxByOrNull { it.second.toLong() * it.third }?.first ?: select(null, advertised)
    }

    enum class FinishAction { NONE, UPSCALE, SCALE_CONFLICT, HDR_UNSUPPORTED }

    /** A finish pass owns the 2× enlargement; do not bake a fractional first pass. */
    fun captureScaleForFinish2x(configuredScale: Float, finish2x: Boolean): Float =
        if (finish2x) 1f else configuredScale

    fun finishAction(requested: Boolean, priorScale: Float, hdr: Boolean): FinishAction = when {
        !requested -> FinishAction.NONE
        priorScale >= 1.999f -> FinishAction.NONE
        hdr -> FinishAction.HDR_UNSUPPORTED
        priorScale > 1.001f -> FinishAction.SCALE_CONFLICT
        else -> FinishAction.UPSCALE
    }

    /** Include native transfer/stage scratch, encoder headroom and reject overflowing dimensions. */
    fun canAllocate2x(width: Int, height: Int, availableBytes: Long): Boolean {
        if (width !in 1..16383 || height !in 1..16383) return false
        val inputPixels = width.toLong() * height
        if (inputPixels * 4L > MAX_BITMAP_FINISH_PIXELS) return false
        // Count output, source/encoder copies and bounded native stages. Free
        // system RAM alone does not make a 192 MP Bitmap safe for the process.
        val estimatedBytes = inputPixels * 32L + 256L * 1024 * 1024
        return estimatedBytes <= availableBytes
    }
}
