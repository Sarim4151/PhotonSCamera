package com.hinnka.mycamera.processor

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.*

/** Coordinates are normalized in the baseline. Translation samples donor at (x-dx, y-dy). */
data class CompositeRegion(val x: Float = .5f, val y: Float = .5f, val width: Float = .35f,
    val height: Float = .45f, val dx: Float = 0f, val dy: Float = 0f,
    val feather: Float = .04f, val ellipse: Boolean = true, val reversed: Boolean = false)

object RegionCompositeTools {
    suspend fun render(first: Bitmap, second: Bitmap, region: CompositeRegion): Bitmap {
        var output: Bitmap? = null
        try { return withContext(Dispatchers.Default) {
        val baseline = if (region.reversed) second else first
        val donor = if (region.reversed) first else second
        val w = baseline.width; val h = baseline.height
        require(w.toLong()*h*4 < Runtime.getRuntime().maxMemory()-(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())) { "Insufficient memory for full resolution composite" }
        val result=baseline.copy(Bitmap.Config.ARGB_8888,true) ?: error("Cannot allocate composite")
        output=result
        val base = IntArray(w)
        val src = IntArray(donor.width)
        val rx=region.width/2; val ry=region.height/2
        for(y in 0 until h) {
            currentCoroutineContext().ensureActive()
            baseline.getPixels(base,0,w,0,y,w,1)
            val sampleY=(((y+.5f)/h-region.dy)*donor.height).toInt()
            if(sampleY in 0 until donor.height) donor.getPixels(src,0,donor.width,0,sampleY,donor.width,1)
            for(x in 0 until w) {
                val nx=(x+.5f)/w; val ny=(y+.5f)/h
                val ax=abs(nx-region.x)/rx; val ay=abs(ny-region.y)/ry
                val distance=if(region.ellipse) sqrt(ax*ax+ay*ay) else max(ax,ay)
                val edge=region.feather / min(rx,ry)
                val alpha=if(edge<=0f) { if(distance<=1f) 1f else 0f } else ((1f-distance)/edge).coerceIn(0f,1f)
                val sx=((nx-region.dx)*donor.width).toInt(); val sy=((ny-region.dy)*donor.height).toInt()
                if(alpha<=0f || sx !in 0 until donor.width || sy !in 0 until donor.height) continue
                val a=base[x]; val b=src[sx]
                fun blend(shift:Int)=((((a ushr shift) and 255)*(1-alpha)+((b ushr shift) and 255)*alpha).roundToInt()).coerceIn(0,255)
                base[x]=(255 shl 24) or (blend(16) shl 16) or (blend(8) shl 8) or blend(0)
            }
            result.setPixels(base,0,w,0,y,w,1)
        }
        result
        } } catch (failure: Throwable) { output?.recycle(); throw failure }
    }
}
