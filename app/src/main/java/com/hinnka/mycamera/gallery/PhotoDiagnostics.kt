package com.hinnka.mycamera.gallery

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sqrt

/** Bounded SDR decode of the saved image; never borrows a cached display bitmap. */
object PhotoDiagnostics {
    data class Report(val metadata: MediaMetadata?, val width: Int, val height: Int,
        val edge: Double, val highlights: Double, val shadows: Double, val residual: Double?, val flatArea: Double)

    suspend fun analyze(context: Context, photo: MediaData): Report = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        val metadata = photo.metadata ?: GalleryManager.loadMetadata(context, photo.id)
            ?: MediaMetadata.fromUri(context, photo.uri)
        val bitmap = GalleryManager.loadBitmap(context, photo.uri, maxEdge = 640,
            preserveHdr = false, maxByteCount = 2L * 1024 * 1024) ?: error("Image unavailable")
        try {
            currentCoroutineContext().ensureActive()
            val width = bitmap.width; val height = bitmap.height
            check(width >= 3 && height >= 3)
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels,0,width,0,0,width,height)
            val luma = IntArray(pixels.size)
            var highlights = 0; var shadows = 0
            pixels.forEachIndexed { index, c ->
                val r = c shr 16 and 255; val g = c shr 8 and 255; val b = c and 255
                luma[index] = (77*r + 150*g + 29*b) shr 8
                if (r >= 250 || g >= 250 || b >= 250) highlights++
                if (r <= 5 && g <= 5 && b <= 5) shadows++
            }
            var edge = 0.0; var residual = 0.0; var flat = 0
            for (y in 1 until height-1) {
                currentCoroutineContext().ensureActive()
                for (x in 1 until width-1) {
                    val p = y*width+x
                    val a=luma[p-1]; val b=luma[p+1]; val c=luma[p-width]; val d=luma[p+width]
                    val lap=4*luma[p]-a-b-c-d
                    edge += lap.toDouble()*lap
                    if (abs(a-b)+abs(c-d)<12 && luma[p] in 16..239) { residual += abs(lap/4.0); flat++ }
                }
            }
            val interior=(width-2)*(height-2)
            Report(metadata,width,height,sqrt(edge/interior)/255*100,
                highlights*100.0/pixels.size,shadows*100.0/pixels.size,
                if(flat>=64 && flat>=interior/100) residual/flat/255*100 else null,flat*100.0/interior)
        } finally { bitmap.recycle() }
    }
}
