package com.hinnka.mycamera.panorama

import android.content.Context
import android.graphics.*
import com.hinnka.mycamera.gallery.*
import com.hinnka.mycamera.data.ContentRepository
import com.hinnka.mycamera.processor.SequenceMemoryBudget
import kotlinx.coroutines.*
import java.util.UUID
import kotlin.math.*

/** Planar horizontal translation stitch. Never writes PhotoSphere/equirectangular metadata. */
object PanoramaStitcher {
    data class Frame(val photo:MediaData,val x:Double,val y:Double,val gain:Double)
    data class Plan(val frames:List<Frame>,val width:Int,val height:Int)
    private fun decode(context:Context,photo:MediaData,edge:Int):Bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver,photo.uri)) { d,i,_ ->
        require(!i.mimeType.contains("dng",true))
        val scale=min(1.0,edge.toDouble()/max(i.size.width,i.size.height))
        val w=max(1,(i.size.width*scale).roundToInt());val h=max(1,(i.size.height*scale).roundToInt())
        check(SequenceMemoryBudget.snapshot(context).fits(w.toLong()*h*8+8L*1024*1024,w.toLong()*h*4))
        d.allocator=ImageDecoder.ALLOCATOR_SOFTWARE;d.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
        if(scale<1)d.setTargetSize(w,h)
    }
    private fun gray(bitmap:Bitmap):DoubleArray {
        val p=IntArray(bitmap.width*bitmap.height);bitmap.getPixels(p,0,bitmap.width,0,0,bitmap.width,bitmap.height)
        return DoubleArray(p.size){i -> val c=p[i];((c shr 16 and 255)*.299+(c shr 8 and 255)*.587+(c and 255)*.114)}
    }
    private data class Match(val x:Int,val y:Int,val score:Double,val gain:Double)
    private suspend fun match(a:DoubleArray,b:DoubleArray,w:Int,h:Int,center:Match?=null):Match {
        fun score(dx:Int,dy:Int):Match {
            val l=max(0,dx);val r=min(w,w+dx);val t=max(0,dy);val bottom=min(h,h+dy)
            if(r-l<w/5 || bottom-t<h*3/4)return Match(dx,dy,-1.0,1.0)
            var sa=0.0;var sb=0.0;var aa=0.0;var bb=0.0;var ab=0.0;var n=0
            for(y in t+2 until bottom-2 step (if(w>500)6 else 3))for(x in l+2 until r-2 step (if(w>500)6 else 3)){val av=a[y*w+x];val bv=b[(y-dy)*w+x-dx];sa+=av;sb+=bv;aa+=av*av;bb+=bv*bv;ab+=av*bv;n++}
            if(n<(if(w<100)40 else 200))return Match(dx,dy,-1.0,1.0)
            val va=aa-sa*sa/n;val vb=bb-sb*sb/n
            if(va/n<70 || vb/n<70)return Match(dx,dy,-1.0,1.0)
            return Match(dx,dy,(ab-sa*sb/n)/sqrt(va*vb),(sa/max(1.0,sb)).coerceIn(.6,1.67))
        }
        val candidates=mutableListOf<Match>()
        val ys=if(center==null)-h/6..h/6 else center.y-6..center.y+6
        val xs=if(center==null)-w*4/5..w*4/5 else center.x-6..center.x+6
        for(dy in ys){currentCoroutineContext().ensureActive();for(dx in xs)if(abs(dx)>w/10)candidates+=score(dx,dy)}
        val coarse=candidates.maxByOrNull {it.score} ?: error("No overlap")
        var best=coarse
        for(dy in coarse.y-4..coarse.y+4)for(dx in coarse.x-4..coarse.x+4){val m=score(dx,dy);if(m.score>best.score)best=m}
        // Reject flat scenes, unrelated images and periodic texture instead of guessing placement.
        require(best.score>(if(w<100).72 else .86) && candidates.none {abs(it.x-best.x)+abs(it.y-best.y)>16 && it.score>best.score-.025})
        return best
    }
    private suspend fun register(a:Bitmap,b:Bitmap):Match {
        var result:Match?=null;var lastWidth=0;var lastHeight=0
        for(edge in listOf(80,320,max(a.width,a.height)).distinct()) {
            val ratio=min(1.0,edge.toDouble()/max(a.width,a.height))
            val w=max(1,(a.width*ratio).roundToInt());val h=max(1,(a.height*ratio).roundToInt())
            val aa=Bitmap.createScaledBitmap(a,w,h,true);val bb=Bitmap.createScaledBitmap(b,w,h,true)
            try {
                val prior=result?.let{Match((it.x.toDouble()*w/lastWidth).roundToInt(),(it.y.toDouble()*h/lastHeight).roundToInt(),it.score,it.gain)}
                result=match(gray(aa),gray(bb),w,h,prior);lastWidth=w;lastHeight=h
            }finally{if(aa!==a)aa.recycle();if(bb!==b)bb.recycle()}
        }
        return result ?: error("No registration")
    }
    suspend fun align(context:Context,photos:List<MediaData>):Plan=withContext(Dispatchers.Default) {
        require(photos.size in 2..8 && photos.all {it.isImage})
        check(SequenceMemoryBudget.snapshot(context).fits(32L*1024*1024,48L*1024*1024))
        var previous:Bitmap?=null
        val frames=mutableListOf<Frame>();var nativeW=0;var nativeH=0;var w=0;var h=0;var direction=0
        try {
            for(photo in photos){currentCoroutineContext().ensureActive()
                var sw=0;var sh=0
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver,photo.uri)){d,i,_->sw=i.size.width;sh=i.size.height;d.setTargetSize(1,1)}.recycle()
                if(frames.isEmpty()){nativeW=sw;nativeH=sh}else require(sw==nativeW && sh==nativeH)
                val next=decode(context,photo,1024)
                try {
                    if(previous==null){w=next.width;h=next.height;frames+=Frame(photo,0.0,0.0,1.0)}
                    else {require(next.width==w && next.height==h);val m=register(previous!!,next)
                        val sign=if(m.x>0)1 else -1;if(direction==0)direction=sign;require(direction==sign)
                        val last=frames.last();frames+=Frame(photo,last.x+m.x.toDouble()*nativeW/w,last.y+m.y.toDouble()*nativeH/h,(last.gain*m.gain).coerceIn(.5,2.0))}
                    previous?.recycle();previous=next
                }catch(t:Throwable){next.recycle();throw t}
            }
            Plan(frames,nativeW,nativeH)
        }finally{previous?.recycle()}
    }
    private data class Geometry(val left:Int,val top:Int,val width:Int,val height:Int)
    private fun geometry(plan:Plan):Geometry {
        val left=floor(plan.frames.minOf {it.x}).toInt();val right=ceil(plan.frames.maxOf {it.x}+plan.width).toInt()
        val top=ceil(plan.frames.maxOf {it.y}).toInt();val bottom=floor(plan.frames.minOf {it.y}+plan.height).toInt()
        require(right>left && bottom>top && right-left<=65500 && bottom-top<=65500)
        return Geometry(left,top,right-left,bottom-top)
    }
    fun dimensions(plan:Plan):Pair<Int,Int> = geometry(plan).let {it.width to it.height}
    suspend fun render(context:Context,plan:Plan,original:Boolean):Bitmap {
        var owned:Bitmap?=null
        try { val result=withContext(Dispatchers.Default){
        val g=geometry(plan);val scale=if(original)1.0 else min(1.0,1600.0/max(g.width,g.height))
        val ow=max(1,(g.width*scale).roundToInt());val oh=max(1,(g.height*scale).roundToInt())
        val sourceBytes=(plan.width*scale).roundToLong()*(plan.height*scale).roundToLong()*8
        check(SequenceMemoryBudget.snapshot(context).fits(ow.toLong()*oh*4+sourceBytes+16L*1024*1024,ow.toLong()*32+8L*1024*1024))
        val output=Bitmap.createBitmap(ow,oh,Bitmap.Config.ARGB_8888).also{owned=it}
        try {
            val row=IntArray(ow)
            fun weight(f:Frame,x:Double):Double {val local=x-f.x;return if(local<0 || local>=plan.width)0.0 else min(min(local+1,plan.width-local),plan.width*.15).coerceAtLeast(1.0)}
            for((index,f)in plan.frames.withIndex()){
                currentCoroutineContext().ensureActive()
                val source=decode(context,f.photo,if(original)Int.MAX_VALUE else max(1,(max(plan.width,plan.height)*scale).roundToInt()))
                try {
                    val sourceRow=IntArray(source.width)
                    for(y in 0 until oh){if(y%32==0)currentCoroutineContext().ensureActive()
                        val worldY=g.top+(y+.5)/scale;val sy=((worldY-f.y)*source.height/plan.height).toInt().coerceIn(0,source.height-1)
                        source.getPixels(sourceRow,0,source.width,0,sy,source.width,1);output.getPixels(row,0,ow,0,y,ow,1)
                        for(x in 0 until ow){val wx=g.left+(x+.5)/scale;val blendWeight=weight(f,wx);if(blendWeight==0.0)continue
                            var oldWeight=0.0;for(prior in 0 until index)oldWeight+=weight(plan.frames[prior],wx)
                            val alpha=blendWeight/(oldWeight+blendWeight);val sx=((wx-f.x)*source.width/plan.width).toInt().coerceIn(0,source.width-1)
                            val c=sourceRow[sx];val old=row[x];var result=0xff000000.toInt()
                            for(shift in 0..16 step 8){val v=((old ushr shift and 255)*(1-alpha)+(c ushr shift and 255)*f.gain*alpha).roundToInt().coerceIn(0,255);result=result or (v shl shift)}
                            row[x]=result
                        };output.setPixels(row,0,ow,0,y,ow,1)
                    }
                }finally{source.recycle()}
            };output
        }catch(t:Throwable){output.recycle();owned=null;throw t}
        };owned=null;return result
        }finally{owned?.recycle()}
    }
    suspend fun save(context:Context,plan:Plan){val bitmap=render(context,plan,true)
        try {currentCoroutineContext().ensureActive();withContext(Dispatchers.IO+NonCancellable){val id=UUID.randomUUID().toString()
            try {val metadata=MediaMetadata(width=bitmap.width,height=bitmap.height,dateTaken=System.currentTimeMillis(),customProperties=mapOf("panoramaMethod" to "planar_translation_ncc_feather","panoramaSources" to plan.frames.joinToString(","){it.photo.id}))
                check(GalleryManager.preparePhoto(context,metadata,null,null,false,1f,includeCropRegionInOutputSize=false,photoId=id)==id)
                GalleryManager.saveBitmapPhoto(context,id,bitmap,false,ContentRepository.getInstance(context).photoProcessor,0f,0f,0f)
                val file=GalleryManager.getPhotoFile(context,id);val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(file.absolutePath,bounds)
                check(file.length()>0 && bounds.outWidth==bitmap.width && bounds.outHeight==bitmap.height)
            }catch(t:Throwable){GalleryManager.deletePhotoOnly(context,id);throw t}
        }}finally{bitmap.recycle()}
    }
}
