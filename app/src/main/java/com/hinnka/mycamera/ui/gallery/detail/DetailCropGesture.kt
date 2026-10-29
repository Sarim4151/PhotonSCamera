package com.hinnka.mycamera.ui.gallery.detail

/** Move the source crop opposite the finger so the underlying photo follows the drag. */
internal fun moveDetailCrop(
    position: Float,
    fingerDeltaPx: Float,
    canvasExtentPx: Int,
    sourceExtentPx: Int,
    cropExtentPx: Int,
    zoom: Float,
): Float {
    val travel = (sourceExtentPx - cropExtentPx).coerceAtLeast(0)
    if (travel == 0 || canvasExtentPx <= 0) return position
    val sourcePixelsPerDisplayPixel = cropExtentPx / (canvasExtentPx * zoom.coerceAtLeast(1f))
    return (position - fingerDeltaPx * sourcePixelsPerDisplayPixel / travel).coerceIn(0f, 1f)
}
