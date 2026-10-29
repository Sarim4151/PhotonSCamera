package com.hinnka.mycamera.selective

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.hinnka.mycamera.gallery.GalleryManager
import com.hinnka.mycamera.gallery.MediaMetadata
import com.hinnka.mycamera.data.ContentRepository
import com.hinnka.mycamera.processor.SequenceMemoryBudget
import kotlinx.coroutines.*
import java.util.UUID
import kotlin.math.*

object SelectiveEditor {
    enum class Tool { BRUSH, LINEAR, RADIAL, SUBJECT, BACKGROUND }
    data class Point(val x:Float,val y:Float)
    data class Mask(val tool:Tool,val points:List<Point>,val radius:Float,val semantic:SemanticMasks.PersonMask?=null) {
        val hasContent:Boolean get()=if(tool==Tool.SUBJECT || tool==Tool.BACKGROUND)semantic!=null else points.isNotEmpty()
    }
    data class Adjustments(val exposure:Float,val warmth:Float,val saturation:Float)
    data class Layer(val id:String=UUID.randomUUID().toString(),val name:String="",
        val mask:Mask=Mask(Tool.BRUSH,emptyList(),.15f),
        val adjustment:Adjustments=Adjustments(0f,0f,0f),val enabled:Boolean=true)
    data class Project(val sourceId:String,val layers:List<Layer> = listOf(Layer()),
        val selectedId:String=layers.first().id,val revision:Long=0)
    private fun weight(x:Float,y:Float,m:Mask,aspect:Float):Float {
        if(m.tool==Tool.SUBJECT || m.tool==Tool.BACKGROUND)return m.semantic?.weight(x,y,m.tool==Tool.BACKGROUND) ?: 0f
        if(m.points.isEmpty()) return 0f
        val a=m.points.first();val b=m.points.last()
        fun distance(p:Point)=hypot((x-p.x)*aspect,y-p.y)
        val dx=(b.x-a.x)*aspect;val dy=b.y-a.y
        return when(m.tool) {
            Tool.RADIAL -> (1f-distance(a)/hypot(dx,dy).coerceAtLeast(.01f)).coerceIn(0f,1f)
            Tool.LINEAR -> (((x-a.x)*aspect*dx+(y-a.y)*dy)/(dx*dx+dy*dy).coerceAtLeast(.0001f)).coerceIn(0f,1f)
            Tool.SUBJECT,Tool.BACKGROUND -> 0f
            Tool.BRUSH -> {
                var d=distance(a)
                for(i in 1 until m.points.size) {
                    val p=m.points[i-1];val q=m.points[i]
                    val sx=(q.x-p.x)*aspect;val sy=q.y-p.y
                    val t=(((x-p.x)*aspect*sx+(y-p.y)*sy)/(sx*sx+sy*sy).coerceAtLeast(.000001f)).coerceIn(0f,1f)
                    d=min(d,hypot((x-p.x)*aspect-t*sx,y-p.y-t*sy))
                }
                (1f-d/m.radius).coerceIn(0f,1f)
            }
        }
    }
    suspend fun render(source:Bitmap,mask:Mask,adjustment:Adjustments,overlay:Boolean=false):Bitmap =
        render(source,listOf(Layer(mask=mask,adjustment=adjustment)),if(overlay)0 else -1)

    // Sequential layer compositing uses a single destination bitmap and one source scanline.
    suspend fun render(source:Bitmap,layers:List<Layer>,overlayIndex:Int = -1):Bitmap {
        var owned:Bitmap?=null
        try {
            val result=withContext(Dispatchers.Default) {
                require(source.width>0 && source.height>0 && source.width.toLong()*source.height<Int.MAX_VALUE/4)
                check(SequenceMemoryBudget.snapshot().fits(source.width.toLong()*source.height*4+16L*1024*1024,
                    source.width.toLong()*16+16L*1024*1024))
                val output=Bitmap.createBitmap(source.width,source.height,Bitmap.Config.ARGB_8888)
                owned=output
                val row=IntArray(source.width)
                val gains=layers.map {2f.pow(it.adjustment.exposure)}
                val aspect=source.width.toFloat()/source.height
                val active=layers.indices.filter {i->
                    val layer=layers[i]
                    layer.enabled && layer.mask.hasContent &&
                        (layer.adjustment.exposure!=0f || layer.adjustment.warmth!=0f || layer.adjustment.saturation!=0f)
                }
                // Most brush/radial masks cover only a small area: reject outside their bounds
                // before computing distances to the brush path at full sensor resolution.
                val bounds=layers.map {layer->
                    val m=layer.mask;val a=m.points.firstOrNull()
                    when {
                        m.tool==Tool.SUBJECT || m.tool==Tool.BACKGROUND -> floatArrayOf(0f,0f,1f,1f)
                        a==null -> floatArrayOf(1f,1f,0f,0f)
                        m.tool==Tool.BRUSH -> floatArrayOf(
                            m.points.minOf {it.x}-m.radius/aspect,m.points.minOf {it.y}-m.radius,
                            m.points.maxOf {it.x}+m.radius/aspect,m.points.maxOf {it.y}+m.radius)
                        m.tool==Tool.RADIAL -> {
                            val b=m.points.last();val radius=hypot((b.x-a.x)*aspect,b.y-a.y).coerceAtLeast(.01f)
                            floatArrayOf(a.x-radius/aspect,a.y-radius,a.x+radius/aspect,a.y+radius)
                        }
                        else -> floatArrayOf(0f,0f,1f,1f)
                    }
                }
                for(y in 0 until source.height) {
                    currentCoroutineContext().ensureActive()
                    source.getPixels(row,0,row.size,0,y,row.size,1)
                    for(x in row.indices) {
                        if(x%256==0)currentCoroutineContext().ensureActive()
                        val original=row[x]
                        var red=((original ushr 16) and 255).toFloat()
                        var green=((original ushr 8) and 255).toFloat()
                        var blue=(original and 255).toFloat()
                        val nx=(x+.5f)/source.width;val ny=(y+.5f)/source.height
                        for(index in active) {
                            val layer=layers[index]
                            val region=bounds[index]
                            if(nx<region[0] || ny<region[1] || nx>region[2] || ny>region[3])continue
                            val w=weight(nx,ny,layer.mask,aspect)
                            if(w==0f)continue
                            val adjustment=layer.adjustment
                            val l=.2126f*red+.7152f*green+.0722f*blue
                            fun channel(v:Float,warm:Float):Float {
                                val edited=(l+(v-l)*(1+adjustment.saturation))*gains[index]+warm*adjustment.warmth*32
                                return (v+(edited-v)*w).coerceIn(0f,255f)
                            }
                            val rr=channel(red,1f);val gg=channel(green,0f);val bb=channel(blue,-1f)
                            red=rr;green=gg;blue=bb
                        }
                        val overlay=layers.getOrNull(overlayIndex)
                        if(overlay!=null && overlay.enabled) {
                            val w=weight(nx,ny,overlay.mask,aspect)*.4f
                            red=red*(1-w)+255*w;green*=1-w;blue*=1-w
                        }
                        row[x]=(original and -0x1000000) or (red.roundToInt() shl 16) or (green.roundToInt() shl 8) or blue.roundToInt()
                    }
                    output.setPixels(row,0,row.size,0,y,row.size,1)
                }
                output
            }
            owned=null;return result
        } finally {owned?.recycle()}
    }
    suspend fun save(context:Context,sourceId:String,bitmap:Bitmap)=withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        withContext(NonCancellable) {
            val id=UUID.randomUUID().toString()
            try {
                val metadata=MediaMetadata(width=bitmap.width,height=bitmap.height,dateTaken=System.currentTimeMillis(),customProperties=mapOf("selectiveSource" to sourceId,"selectiveTool" to "layered_mask"))
                check(GalleryManager.preparePhoto(context,metadata,null,null,false,1f,includeCropRegionInOutputSize=false,photoId=id)==id)
                GalleryManager.saveBitmapPhoto(context,id,bitmap,false,ContentRepository.getInstance(context).photoProcessor,0f,0f,0f)
                val file=GalleryManager.getPhotoFile(context,id);val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
                BitmapFactory.decodeFile(file.absolutePath,bounds)
                check(file.length()>0 && bounds.outWidth==bitmap.width && bounds.outHeight==bitmap.height)
            } catch(failure:Throwable) {GalleryManager.deletePhotoOnly(context,id);throw failure}
        }
    }
}
