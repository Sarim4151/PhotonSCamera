package com.hinnka.mycamera.actionpan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.hinnka.mycamera.data.ContentRepository
import com.hinnka.mycamera.gallery.GalleryManager
import com.hinnka.mycamera.gallery.MediaMetadata
import com.hinnka.mycamera.processor.SequenceMemoryBudget
import com.hinnka.mycamera.selective.SemanticMasks.PersonMask
import kotlinx.coroutines.*
import java.util.UUID
import kotlin.math.*

object ActionPanEditor {
    fun preflight(width:Int,height:Int) {
        require(width>0 && height>0 && width.toLong()*height<Int.MAX_VALUE/4)
        val halo=ceil(max(width,height)*.04).toLong()+2
        check(SequenceMemoryBudget.snapshot().fits(width.toLong()*height*4+24L*1024*1024,
            width.toLong()*(64+2*halo)*4+16L*1024*1024))
    }
    /** One output plus bounded source stripes. Foreground samples never enter the blur. */
    suspend fun render(source:Bitmap,mask:PersonMask,direction:Float,strength:Float):Bitmap {
        require(direction.isFinite() && strength.isFinite() && strength in 0f..1f)
        var owned:Bitmap?=null
        try {
            val result=withContext(Dispatchers.Default) {
                preflight(source.width,source.height)
                val w=source.width;val h=source.height
                val radius=max(w,h)*.04f*strength
                val angle=direction*PI/180;val dx=cos(angle).toFloat()*radius;val dy=sin(angle).toFloat()*radius
                val halo=ceil(abs(dy)).toInt()+1
                val stripe=IntArray(w*min(h,64+halo*2));val row=IntArray(w)
                val output=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);owned=output
                // Precompute only a low resolution protection plane; no sensor-sized float buffers.
                val protection=FloatArray(mask.width*mask.height)
                for(my in 0 until mask.height)for(mx in 0 until mask.width)
                    protection[my*mask.width+mx]=mask.protection((mx+.5f)/mask.width,(my+.5f)/mask.height)
                fun protect(x:Int,y:Int)=protection[min(mask.height-1,y*mask.height/h)*mask.width+min(mask.width-1,x*mask.width/w)]
                for(start in 0 until h step 64) {
                    currentCoroutineContext().ensureActive()
                    val end=min(h,start+64);val top=max(0,start-halo);val bottom=min(h,end+halo)
                    source.getPixels(stripe,0,w,0,top,w,bottom-top)
                    for(y in start until end) {
                        currentCoroutineContext().ensureActive()
                        for(x in 0 until w) {
                            val original=stripe[(y-top)*w+x];val p=protect(x,y)
                            if(p>=.999f || radius<.5f){row[x]=original;continue}
                            var rr=0f;var gg=0f;var bb=0f;var total=0f
                            for(t in -12..12) {
                                val sx=(x+dx*t/12).roundToInt().coerceIn(0,w-1)
                                val sy=(y+dy*t/12).roundToInt().coerceIn(0,h-1)
                                val weight=1f-protect(sx,sy)
                                val c=stripe[(sy-top)*w+sx]
                                rr+=(c ushr 16 and 255)*weight;gg+=(c ushr 8 and 255)*weight;bb+=(c and 255)*weight;total+=weight
                            }
                            if(total<.001f){row[x]=original;continue}
                            fun blend(v:Float,shift:Int)=((original ushr shift and 255)*p+v/total*(1-p)).roundToInt().coerceIn(0,255)
                            row[x]=(original and -0x1000000) or (blend(rr,16) shl 16) or (blend(gg,8) shl 8) or blend(bb,0)
                        }
                        output.setPixels(row,0,w,0,y,w,1)
                    }
                }
                output
            }
            owned=null;return result
        } finally {owned?.recycle()}
    }
    suspend fun save(context:Context,sourceId:String,bitmap:Bitmap,direction:Float,strength:Float)=withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        // Once publication starts, drain and verify or remove the exact owned photo ID.
        withContext(NonCancellable) {
            val id=UUID.randomUUID().toString()
            try {
                val metadata=MediaMetadata(width=bitmap.width,height=bitmap.height,dateTaken=System.currentTimeMillis(),customProperties=mapOf(
                    "actionPanSource" to sourceId,"actionPanMask" to "mlkit_person","actionPanDirection" to direction.toString(),"actionPanStrength" to strength.toString()))
                check(GalleryManager.preparePhoto(context,metadata,null,null,false,1f,includeCropRegionInOutputSize=false,photoId=id)==id)
                GalleryManager.saveBitmapPhoto(context,id,bitmap,false,ContentRepository.getInstance(context).photoProcessor,0f,0f,0f)
                val file=GalleryManager.getPhotoFile(context,id);val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
                BitmapFactory.decodeFile(file.absolutePath,bounds)
                check(file.length()>0 && bounds.outWidth==bitmap.width && bounds.outHeight==bitmap.height)
            } catch(failure:Throwable){GalleryManager.deletePhotoOnly(context,id);throw failure}
        }
    }
}
