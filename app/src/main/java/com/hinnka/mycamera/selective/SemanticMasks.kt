package com.hinnka.mycamera.selective

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.ColorSpace
import com.hinnka.mycamera.processor.PersonCompositeTools
import kotlinx.coroutines.*
import kotlin.math.*

/** Normalized, bounded person probabilities from bundled on-device selfie segmentation. */
object SemanticMasks {
    data class PersonMask(val width:Int,val height:Int,val confidence:ByteArray) {
        init {require(width in 1..512 && height in 1..512 && confidence.size==width*height)}
        fun weight(x:Float,y:Float,background:Boolean=false):Float {
            val px=(x*width-.5f).coerceIn(0f,width-1f);val py=(y*height-.5f).coerceIn(0f,height-1f)
            val ix=px.toInt();val iy=py.toInt();val jx=min(ix+1,width-1);val jy=min(iy+1,height-1)
            val fx=px-ix;val fy=py-iy
            fun v(a:Int,b:Int)=(confidence[b*width+a].toInt() and 255)/255f
            val p=(v(ix,iy)*(1-fx)+v(jx,iy)*fx)*(1-fy)+(v(ix,jy)*(1-fx)+v(jx,jy)*fx)*fy
            return if(background)1f-p else p
        }
        /** Dilated uncertainty protects hair and boundaries from motion streaks. */
        fun protection(x:Float,y:Float):Float {
            var p=weight(x,y)
            for(dy in -1..1)for(dx in -1..1)p=max(p,weight(x+dx*2f/width,y+dy*2f/height))
            return ((p-.05f)/.2f).coerceIn(0f,1f)
        }
    }
    fun compact(mask:PersonMask,maxEdge:Int=128):PersonMask {
        require(maxEdge in 1..128)
        val scale=min(1f,maxEdge.toFloat()/max(mask.width,mask.height))
        val w=max(1,(mask.width*scale).roundToInt());val h=max(1,(mask.height*scale).roundToInt())
        return PersonMask(w,h,ByteArray(w*h){i->(mask.weight((i%w+.5f)/w,(i/w+.5f)/h)*255).roundToInt().toByte()})
    }
    suspend fun detectPerson(source:Bitmap):PersonMask=withContext(Dispatchers.Default) {
        val scale=min(1f,512f/max(source.width,source.height))
        val small=Bitmap.createScaledBitmap(source,max(1,(source.width*scale).roundToInt()),max(1,(source.height*scale).roundToInt()),true)
        var normalized:Bitmap?=null
        try {
            val input=if(small.config==Bitmap.Config.ARGB_8888 && small.colorSpace?.isSrgb!=false)small else {
                val readable=if(small.config==Bitmap.Config.HARDWARE)small.copy(Bitmap.Config.ARGB_8888,false) ?: error("Cannot read subject input") else small
                try {
                    Bitmap.createBitmap(small.width,small.height,Bitmap.Config.ARGB_8888).also { owned ->
                        normalized=owned
                        Canvas(owned).drawBitmap(readable,0f,0f,Paint(Paint.FILTER_BITMAP_FLAG))
                    }
                }finally{if(readable!==small)readable.recycle()}
            }
            val raw=PersonCompositeTools.segment(input)
            require(raw.width<=512 && raw.height<=512)
            require(raw.values.all {it.isFinite()})
            require(raw.values.count {it>.8f}>raw.values.size/500 && raw.values.count {it<.2f}>raw.values.size/100) {"Person/background segmentation uncertain"}
            PersonMask(raw.width,raw.height,ByteArray(raw.values.size){(raw.values[it].coerceIn(0f,1f)*255).roundToInt().toByte()})
        } finally {normalized?.recycle();if(small!==source)small.recycle()}
    }
}
