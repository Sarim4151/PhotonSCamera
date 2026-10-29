package com.hinnka.mycamera.bestface

import android.content.Context
import android.graphics.*
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.*
import com.hinnka.mycamera.data.ContentRepository
import com.hinnka.mycamera.gallery.*
import com.hinnka.mycamera.processor.SequenceMemoryBudget
import kotlinx.coroutines.*
import java.io.Closeable
import java.util.UUID
import kotlin.math.*

/** Real source pixels only. Spatial slots are not biometric identities. */
object BestFaceMerger {
    data class Point(val x: Float, val y: Float)
    data class Face(val bounds: RectF, val left: Point, val right: Point,
        val outline: List<Point>, val yaw: Float, val pitch: Float, val roll: Float)
    data class Option(val frame: Int, val face: Int)
    class Session(val photos: List<MediaData>, val frames: List<Bitmap>, val faces: List<List<Face>>,
        val options: List<List<Option>>, val rejectedFrames: Int) : Closeable {
        private var readers=0
        private var closed=false
        @Synchronized fun acquire() { check(!closed);readers++ }
        @Synchronized fun release() { readers--;if(closed && readers==0) recycle() }
        @Synchronized override fun close() { closed=true;if(readers==0) recycle() }
        private fun recycle() { frames.forEach { if(!it.isRecycled)it.recycle() } }
    }

    private fun decode(context: Context, photo: MediaData, edge: Int?): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, photo.uri)
        val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            require(!info.mimeType.contains("dng",ignoreCase=true)) { "Use a rendered photograph, not a RAW preview" }
            val scale = if(edge == null) 1.0 else min(1.0,edge.toDouble()/max(info.size.width,info.size.height))
            val w=max(1,(info.size.width*scale).roundToInt()); val h=max(1,(info.size.height*scale).roundToInt())
            check(SequenceMemoryBudget.snapshot(context).fits(w.toLong()*h*8+16L*1024*1024,8L*1024*1024))
            decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired=true
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
            if(edge!=null && scale<1) decoder.setTargetSize(w,h)
        }
        if(decoded.config==Bitmap.Config.ARGB_8888) return decoded
        try {
            check(SequenceMemoryBudget.snapshot(context).fits(decoded.width.toLong()*decoded.height*4+8L*1024*1024,0))
            return decoded.copy(Bitmap.Config.ARGB_8888,true) ?: error("Unable to normalize image")
        } finally { decoded.recycle() }
    }

    suspend fun open(context: Context, photos: List<MediaData>): Session {
        var owned: Session?=null
        val frames=mutableListOf<Bitmap>()
        try {
            val result=withContext(Dispatchers.IO) {
                require(photos.size in 2..6 && photos.all { it.isImage })
                check(SequenceMemoryBudget.snapshot(context).fits((photos.size+1)*2048L*2048*4+24L*1024*1024,24L*1024*1024))
                val detector=FaceDetection.getClient(FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL).setMinFaceSize(.04f).build())
                val contourDetector=FaceDetection.getClient(FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                    .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL).setMinFaceSize(.1f).build())
                val found=mutableListOf<List<Face>>()
                try {
                    for(photo in photos) {
                        currentCoroutineContext().ensureActive()
                        val bitmap=decode(context,photo,2048); frames+=bitmap
                        require(bitmap.width==frames[0].width && bitmap.height==frames[0].height)
                        val raw=Tasks.await(detector.process(InputImage.fromBitmap(bitmap,0)))
                        val faces=mutableListOf<Face>()
                        for(face in raw.sortedBy { it.boundingBox.left }.take(6)) {
                            currentCoroutineContext().ensureActive()
                            val le=face.getLandmark(FaceLandmark.LEFT_EYE)?.position ?: continue
                            val re=face.getLandmark(FaceLandmark.RIGHT_EYE)?.position ?: continue
                            val b=Rect(face.boundingBox).apply { inset(-width()/8,-height()/8) }
                            if(!b.intersect(0,0,bitmap.width,bitmap.height) || b.width()<40 || b.height()<40) continue
                            val crop=Bitmap.createBitmap(bitmap,b.left,b.top,b.width(),b.height())
                            try {
                                val outline=Tasks.await(contourDetector.process(InputImage.fromBitmap(crop,0)))
                                    .minByOrNull { abs(it.boundingBox.exactCenterX()-crop.width/2f)+abs(it.boundingBox.exactCenterY()-crop.height/2f) }
                                    ?.getContour(FaceContour.FACE)?.points ?: continue
                                if(outline.size<12) continue
                                val eyes=listOf(Point(le.x,le.y),Point(re.x,re.y)).sortedBy { it.x }
                                if(hypot(eyes[1].x-eyes[0].x,eyes[1].y-eyes[0].y)<16) continue
                                faces+=Face(RectF(face.boundingBox),eyes[0],eyes[1],outline.map { Point(it.x+b.left,it.y+b.top) },
                                    face.headEulerAngleY,face.headEulerAngleX,face.headEulerAngleZ)
                            } finally { if(crop!==bitmap) crop.recycle() }
                        }
                        found+=faces
                    }
                } finally { detector.close(); contourDetector.close() }
                require(found[0].isNotEmpty())
                val options=found[0].indices.map { mutableListOf(Option(0,it)) }
                var rejected=0
                for(index in 1 until frames.size) {
                    currentCoroutineContext().ensureActive()
                    val shift=register(frames[0],frames[index],found[0],found[index])
                    if(shift==null) { rejected++; continue }
                    for(slot in found[0].indices) {
                        val reference=found[0][slot]
                        val candidates=found[index].indices.filter { candidate ->
                            val donor=found[index][candidate]
                            val distance=hypot(donor.bounds.centerX()-shift.x-reference.bounds.centerX(),
                                donor.bounds.centerY()-shift.y-reference.bounds.centerY())
                            distance<reference.bounds.width()*.4f && compatible(reference,donor) &&
                                lightingCompatible(frames[0],frames[index],reference,donor) &&
                                found[0].indices.all { other -> other==slot || hypot(donor.bounds.centerX()-shift.x-found[0][other].bounds.centerX(),
                                    donor.bounds.centerY()-shift.y-found[0][other].bounds.centerY())>distance*1.5f }
                        }
                        if(candidates.size==1) options[slot]+=Option(index,candidates.single())
                    }
                }
                Session(photos.toList(),frames.toList(),found,options,rejected).also { owned=it }
            }
            owned=null
            return result
        } catch(failure: Throwable) { if(owned==null) frames.forEach { if(!it.isRecycled) it.recycle() }; throw failure }
        finally { owned?.close() }
    }

    private fun compatible(a: Face,b: Face): Boolean {
        val aw=hypot(a.right.x-a.left.x,a.right.y-a.left.y)
        val bw=hypot(b.right.x-b.left.x,b.right.y-b.left.y)
        if(bw/aw !in .85f..1.18f || abs(a.yaw-b.yaw)>9 || abs(a.pitch-b.pitch)>9 || abs(a.roll-b.roll)>12) return false
        return a.outline.all { p -> val q=map(p,a,b)
            b.outline.minOf { hypot(it.x-q.x,it.y-q.y) }<bw*.28f }
    }

    private fun lightingCompatible(a:Bitmap,b:Bitmap,ref:Face,src:Face):Boolean {
        val difference=DoubleArray(3);var count=0
        val cx=ref.bounds.centerX();val cy=ref.bounds.centerY()
        for(outline in ref.outline) {
            val p=Point(outline.x*.85f+cx*.15f,outline.y*.85f+cy*.15f)
            val q=map(p,ref,src)
            if(p.x<0 || p.y<0 || p.x>=a.width || p.y>=a.height || q.x<0 || q.y<0 || q.x>=b.width || q.y>=b.height) return false
            val ac=a.getPixel(p.x.toInt(),p.y.toInt());val bc=b.getPixel(q.x.toInt(),q.y.toInt())
            for(channel in 0..2) difference[channel]+=abs((ac ushr (channel*8) and 255)-(bc ushr (channel*8) and 255))
            count++
        }
        return count>=12 && difference.all { it/count<28 }
    }

    /** Integer background translation on a small lattice, excluding face regions in both frames. */
    private suspend fun register(a: Bitmap,b: Bitmap,af: List<Face>,bf: List<Face>): Point? {
        val registrationScale=256f/max(a.width,a.height)
        val w=max(32,(a.width*registrationScale).roundToInt());val h=max(32,(a.height*registrationScale).roundToInt())
        val aa=Bitmap.createScaledBitmap(a,w,h,true); val bb=Bitmap.createScaledBitmap(b,w,h,true)
        try {
            val ap=IntArray(w*h);val bp=IntArray(w*h)
            aa.getPixels(ap,0,w,0,0,w,h);bb.getPixels(bp,0,w,0,0,w,h)
            val sx=w.toFloat()/a.width;val sy=h.toFloat()/a.height
            fun masked(x:Int,y:Int,faces:List<Face>)=faces.any { x/sx in (it.bounds.left-it.bounds.width()*.15f)..(it.bounds.right+it.bounds.width()*.15f) &&
                y/sy in (it.bounds.top-it.bounds.height()*.15f)..(it.bounds.bottom+it.bounds.height()*.15f) }
            fun luma(c:Int)=((c shr 16 and 255)*77+(c shr 8 and 255)*150+(c and 255)*29)/65280.0
            fun error(dx:Int,dy:Int):Pair<Double,Double> {
                var sa=0.0;var sb=0.0;var aa2=0.0;var bb2=0.0;var ab=0.0;var n=0
                for(y in 12 until h-12 step 3) for(x in 12 until w-12 step 3) {
                    if(y+dy !in 0 until h || x+dx !in 0 until w || masked(x,y,af) || masked(x+dx,y+dy,bf)) continue
                    val av=luma(ap[y*w+x]);val bv=luma(bp[(y+dy)*w+x+dx])
                    sa+=av;sb+=bv;aa2+=av*av;bb2+=bv*bv;ab+=av*bv;n++
                }
                if(n<400) return Double.POSITIVE_INFINITY to 0.0
                val va=max(0.0,aa2-sa*sa/n);val vb=max(0.0,bb2-sb*sb/n)
                return max(0.0,(va+vb-2*(ab-sa*sb/n))/n)+(max(0.0,abs(sa-sb)/n-.06)).pow(2) to (va+vb)/(2*n)
            }
            var best=0 to 0;var score=Double.POSITIVE_INFINITY
            val scores=mutableListOf<Pair<Pair<Int,Int>,Double>>()
            for(dy in -10..10 step 2) {
                currentCoroutineContext().ensureActive()
                for(dx in -10..10 step 2) { val e=error(dx,dy).first;scores+=(dx to dy) to e;if(e<score){score=e;best=dx to dy} }
            }
            val coarse=best
            for(dy in coarse.second-1..coarse.second+1) for(dx in coarse.first-1..coarse.first+1) {
                val e=error(dx,dy).first;if(e<score){score=e;best=dx to dy}
            }
            val variance=error(best.first,best.second).second
            if(!score.isFinite() || variance<.0005 || score>.0064 || score/variance>.45 || abs(best.first)>=10 || abs(best.second)>=10) return null
            if(scores.any { abs(it.first.first-best.first)+abs(it.first.second-best.second)>4 && it.second<=score*1.08+.00001 }) return null
            return Point(best.first/sx,best.second/sy)
        } finally { if(aa!==a) aa.recycle();if(bb!==b) bb.recycle() }
    }

    private fun map(p:Point,a:Face,b:Face):Point {
        val ax=a.right.x-a.left.x;val ay=a.right.y-a.left.y;val d=ax*ax+ay*ay
        val x=p.x-a.left.x;val y=p.y-a.left.y
        val u=(x*ax+y*ay)/d;val v=(-x*ay+y*ax)/d
        val bx=b.right.x-b.left.x;val by=b.right.y-b.left.y
        return Point(b.left.x+u*bx-v*by,b.left.y+u*by+v*bx)
    }
    private fun scaled(face:Face,sx:Float,sy:Float)=Face(RectF(face.bounds.left*sx,face.bounds.top*sy,face.bounds.right*sx,face.bounds.bottom*sy),
        Point(face.left.x*sx,face.left.y*sy),Point(face.right.x*sx,face.right.y*sy),face.outline.map { Point(it.x*sx,it.y*sy) },face.yaw,face.pitch,face.roll)
    private fun inside(p:Point,polygon:List<Point>):Boolean {
        var yes=false;var j=polygon.lastIndex
        for(i in polygon.indices) { val a=polygon[i];val b=polygon[j]
            if((a.y>p.y)!=(b.y>p.y) && p.x<(b.x-a.x)*(p.y-a.y)/(b.y-a.y)+a.x) yes=!yes
            j=i
        };return yes
    }
    private fun distance(p:Point,polygon:List<Point>):Float=polygon.indices.minOf { i ->
        val a=polygon[i];val b=polygon[(i+1)%polygon.size];val dx=b.x-a.x;val dy=b.y-a.y
        val t=if(dx*dx+dy*dy==0f)0f else ((p.x-a.x)*dx+(p.y-a.y)*dy)/(dx*dx+dy*dy)
        hypot(p.x-a.x-dx*t.coerceIn(0f,1f),p.y-a.y-dy*t.coerceIn(0f,1f))
    }

    suspend fun render(context:Context,session:Session,choices:List<Int>,originalResolution:Boolean):Bitmap {
        var owned:Bitmap?=null
        session.acquire()
        try {
            val result=withContext(Dispatchers.IO) {
                require(choices.size==session.options.size)
                val output=if(originalResolution)decode(context,session.photos[0],null)
                    else session.frames[0].copy(Bitmap.Config.ARGB_8888,true) ?: error("Allocation failed")
                owned=output
                val sx=output.width.toFloat()/session.frames[0].width;val sy=output.height.toFloat()/session.frames[0].height
                require(abs(sx/sy-1)<.01f)
                for(slot in choices.indices) {
                    currentCoroutineContext().ensureActive()
                    val option=session.options[slot][choices[slot]]
                    if(option.frame==0) continue
                    val donor=if(originalResolution)decode(context,session.photos[option.frame],null) else session.frames[option.frame]
                    try {
                        val ref=scaled(session.faces[0][slot],sx,sy)
                        val base=session.frames[option.frame]
                        require(abs((donor.width.toFloat()/base.width)/(donor.height.toFloat()/base.height)-1)<.01f)
                        val src=scaled(session.faces[option.frame][option.face],donor.width.toFloat()/base.width,donor.height.toFloat()/base.height)
                        blend(output,donor,ref,src)
                    } finally { if(originalResolution)donor.recycle() }
                }
                currentCoroutineContext().ensureActive()
                output
            }
            owned=null;return result
        } finally { owned?.recycle();session.release() }
    }

    private suspend fun blend(output:Bitmap,donor:Bitmap,ref:Face,src:Face) {
        val left=max(0,floor(ref.bounds.left).toInt());val top=max(0,floor(ref.bounds.top).toInt())
        val right=min(output.width,ceil(ref.bounds.right).toInt());val bottom=min(output.height,ceil(ref.bounds.bottom).toInt())
        val dl=max(0,floor(src.bounds.left-src.bounds.width()*.2f).toInt());val dt=max(0,floor(src.bounds.top-src.bounds.height()*.2f).toInt())
        val dr=min(donor.width,ceil(src.bounds.right+src.bounds.width()*.2f).toInt());val db=min(donor.height,ceil(src.bounds.bottom+src.bounds.height()*.2f).toInt())
        val dw=dr-dl;val dh=db-dt
        check(SequenceMemoryBudget.snapshot().fits(0,(dw.toLong()*dh+right-left)*4+8L*1024*1024))
        val pixels=IntArray(dw*dh);donor.getPixels(pixels,0,dw,dl,dt,dw,dh)
        val row=IntArray(right-left)
        val feather=max(2f,ref.bounds.width()*.09f)
        for(y in top until bottom) {
            currentCoroutineContext().ensureActive()
            output.getPixels(row,0,row.size,left,y,row.size,1)
            for(x in left until right) {
                val p=Point(x+.5f,y+.5f)
                if(!inside(p,ref.outline)) continue
                val q=map(p,ref,src)
                val sampleX=q.x-.5f;val sampleY=q.y-.5f
                if(!inside(q,src.outline) || sampleX<dl || sampleY<dt || sampleX>dr-1 || sampleY>db-1) continue
                val alpha=min(distance(p,ref.outline)/feather,distance(q,src.outline)/(feather*src.bounds.width()/ref.bounds.width())).coerceIn(0f,1f)
                val w=alpha*alpha*(3-2*alpha)
                val ix=floor(sampleX).toInt()-dl;val iy=floor(sampleY).toInt()-dt
                val nx=min(ix+1,dw-1);val ny=min(iy+1,dh-1)
                val fx=sampleX-floor(sampleX);val fy=sampleY-floor(sampleY)
                var color=0xff000000.toInt()
                for(shift in intArrayOf(0,8,16)) {
                    fun c(i:Int)=(pixels[i] ushr shift and 255).toFloat()
                    val a=c(iy*dw+ix)*(1-fx)+c(iy*dw+nx)*fx
                    val b=c(ny*dw+ix)*(1-fx)+c(ny*dw+nx)*fx
                    val value=(row[x-left] ushr shift and 255)*(1-w)+(a*(1-fy)+b*fy)*w
                    color=color or (value.roundToInt().coerceIn(0,255) shl shift)
                };row[x-left]=color
            };output.setPixels(row,0,row.size,left,y,row.size,1)
        }
    }

    suspend fun save(context:Context,session:Session,choices:List<Int>,originalResolution:Boolean) {
        val bitmap=render(context,session,choices,originalResolution)
        try {
            currentCoroutineContext().ensureActive()
            withContext(Dispatchers.IO+NonCancellable) {
                val id=UUID.randomUUID().toString()
                try {
                    val metadata=MediaMetadata(width=bitmap.width,height=bitmap.height,dateTaken=System.currentTimeMillis(),
                        customProperties=mapOf("bestFaceReference" to session.photos[0].id,"bestFaceSources" to choices.mapIndexed { slot,choice ->
                            session.photos[session.options[slot][choice].frame].id }.joinToString(","),"bestFaceMethod" to "measured_face_patch",
                            "bestFaceResolution" to if(originalResolution)"original" else "up_to_2048"))
                    check(GalleryManager.preparePhoto(context,metadata,null,null,false,1f,includeCropRegionInOutputSize=false,photoId=id)==id)
                    GalleryManager.saveBitmapPhoto(context,id,bitmap,false,ContentRepository.getInstance(context).photoProcessor,0f,0f,0f)
                    val file=GalleryManager.getPhotoFile(context,id)
                    val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
                    BitmapFactory.decodeFile(file.absolutePath,bounds)
                    check(file.length()>0 && bounds.outWidth==bitmap.width && bounds.outHeight==bitmap.height)
                } catch(failure:Exception) {GalleryManager.deletePhotoOnly(context,id);throw failure}
            }
        } finally { bitmap.recycle() }
    }
}
