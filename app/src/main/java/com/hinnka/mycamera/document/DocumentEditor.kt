package com.hinnka.mycamera.document

import com.hinnka.mycamera.processor.SequenceMemoryBudget
import com.hinnka.mycamera.gallery.GalleryManager
import com.hinnka.mycamera.gallery.MediaMetadata
import com.hinnka.mycamera.data.ContentRepository
import java.util.UUID
import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.*

object DocumentEditor {
    data class Corner(val x:Float,val y:Float)
    val frame=listOf(Corner(.04f,.04f),Corner(.96f,.04f),Corner(.96f,.96f),Corner(.04f,.96f))
    fun valid(c:List<Corner>):Boolean {
        if(c.size!=4)return false
        val cross=c.indices.map { i -> val a=c[i];val b=c[(i+1)%4];val d=c[(i+2)%4];(b.x-a.x)*(d.y-b.y)-(b.y-a.y)*(d.x-b.x) }
        return cross.all { it>.002f }
    }
    fun requireSourceBudget(width:Int,height:Int,context:Context?=null) {
        check(width in 1..100000 && height in 1..100000)
        // Worst-case perspective edges may reach the source diagonal on both axes.
        val pixels=width.toLong()*height
        val destination=width.toLong()*width+height.toLong()*height
        check(SequenceMemoryBudget.snapshot(context).fits((pixels+destination)*4+16L*1024*1024,2L*1024*1024))
    }
    suspend fun render(source:Bitmap,c:List<Corner>,bw:Boolean):Bitmap {
        var owned:Bitmap?=null
        try {
            val result=withContext(Dispatchers.Default) {
                check(valid(c))
                fun distance(a:Corner,b:Corner)=hypot((a.x-b.x)*source.width,(a.y-b.y)*source.height)
                val w=max(distance(c[0],c[1]),distance(c[3],c[2])).roundToInt().coerceAtLeast(1)
                val h=max(distance(c[0],c[3]),distance(c[1],c[2])).roundToInt().coerceAtLeast(1)
                check(SequenceMemoryBudget.snapshot().fits(source.allocationByteCount.toLong()+w.toLong()*h*4+16L*1024*1024,2L*1024*1024))
                val output=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);owned=output
                val matrix=Matrix();check(matrix.setPolyToPoly(c.flatMap{listOf(it.x*source.width,it.y*source.height)}.toFloatArray(),0,floatArrayOf(0f,0f,w.toFloat(),0f,w.toFloat(),h.toFloat(),0f,h.toFloat()),0,4))
                val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                val color=ColorMatrix();if(bw)color.setSaturation(0f)
                color.postConcat(ColorMatrix(floatArrayOf(1.18f,0f,0f,0f,-18f,0f,1.18f,0f,0f,-18f,0f,0f,1.18f,0f,-18f,0f,0f,0f,1f,0f)))
                paint.colorFilter=ColorMatrixColorFilter(color);Canvas(output).drawBitmap(source,matrix,paint)
                currentCoroutineContext().ensureActive();output
            }
            owned=null;return result
        }finally{owned?.recycle()}
    }
    suspend fun recognize(bitmap:Bitmap,hindi:Boolean):String=withContext(Dispatchers.Default) {
        // The owned snapshot survives dismissal while ML Kit finishes its native task.
        check(SequenceMemoryBudget.snapshot().fits(bitmap.allocationByteCount.toLong()*2+16L*1024*1024,4L*1024*1024))
        val owned=bitmap.copy(Bitmap.Config.ARGB_8888,false) ?: error("Snapshot unavailable")
        val client=try { if(hindi)TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build()) else TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
            catch(failure:Throwable){owned.recycle();throw failure}
        suspendCancellableCoroutine { continuation ->
            try {
                client.process(InputImage.fromBitmap(owned,0)).addOnCompleteListener { task ->
                    client.close();owned.recycle()
                    if(continuation.isActive) { if(task.isSuccessful)continuation.resume(task.result.text) else continuation.resumeWithException(task.exception ?: IllegalStateException("OCR failed")) }
                }
            }catch(failure:Exception){client.close();owned.recycle();if(continuation.isActive)continuation.resumeWithException(failure)}
        }
    }
    suspend fun pdf(context:Context,uri:Uri,bitmap:Bitmap,text:String,onWritten:()->Unit)=withContext(Dispatchers.IO) {
        // PdfDocument retains page display lists until write; allow an additional image payload.
        check(SequenceMemoryBudget.snapshot(context).fits(bitmap.allocationByteCount.toLong()*2+16L*1024*1024,4L*1024*1024+text.length.toLong()*8))
        val pdf=PdfDocument()
        try {
            val width=595;val height=842
            val page=pdf.startPage(PdfDocument.PageInfo.Builder(width,height,1).create())
            val scale=min(width.toFloat()/bitmap.width,height.toFloat()/bitmap.height)
            val imageWidth=bitmap.width*scale;val imageHeight=bitmap.height*scale
            val left=(width-imageWidth)/2f;val top=(height-imageHeight)/2f
            page.canvas.drawColor(Color.WHITE)
            page.canvas.drawBitmap(bitmap,null,RectF(left,top,left+imageWidth,top+imageHeight),Paint(Paint.FILTER_BITMAP_FLAG));pdf.finishPage(page)
            // Actual selectable OCR text is on following pages; no invisible-layer claim.
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.BLACK;textSize=12f;typeface=Typeface.create("sans-serif",Typeface.NORMAL) }
            val lines=mutableListOf<String>()
            text.lines().forEach { raw -> var left=raw; if(left.isEmpty())lines.add("");while(left.isNotEmpty()){val count=paint.breakText(left,true,515f,null).coerceAtLeast(1);lines.add(left.take(count));left=left.drop(count)} }
            lines.chunked(44).forEachIndexed { index, chunk ->
                currentCoroutineContext().ensureActive()
                val textPage=pdf.startPage(PdfDocument.PageInfo.Builder(595,842,index+2).create())
                chunk.forEachIndexed { row,line -> textPage.canvas.drawText(line,40f,50f+row*17f,paint) };pdf.finishPage(textPage)
            }
            currentCoroutineContext().ensureActive()
            context.contentResolver.openOutputStream(uri,"w")!!.use { pdf.writeTo(it) }
            // Record durable completion before cancellation can discard the withContext result.
            onWritten()
        }finally{pdf.close()}
    }
    // Caller must pass only the new URI returned by its CreateDocument request.
    suspend fun deleteCreatedPdf(context:Context,newUri:Uri)=withContext(NonCancellable+Dispatchers.IO) {
        val deleted=runCatching { DocumentsContract.deleteDocument(context.contentResolver,newUri) }.getOrDefault(false)
        if(!deleted){
            val count=runCatching { context.contentResolver.delete(newUri,null,null) }.getOrDefault(0)
            if(count<=0)Log.w("DocumentEditor","Provider could not remove an incomplete newly created PDF")
        }
    }
    suspend fun save(context:Context,sourceId:String,bitmap:Bitmap)=withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        withContext(NonCancellable) {
            val id=UUID.randomUUID().toString()
            try {
                val metadata=MediaMetadata(width=bitmap.width,height=bitmap.height,dateTaken=System.currentTimeMillis(),customProperties=mapOf("documentSource" to sourceId,"documentTool" to "perspective"))
                check(GalleryManager.preparePhoto(context,metadata,null,null,false,1f,includeCropRegionInOutputSize=false,photoId=id)==id)
                GalleryManager.saveBitmapPhoto(context,id,bitmap,false,ContentRepository.getInstance(context).photoProcessor,0f,0f,0f)
                val file=GalleryManager.getPhotoFile(context,id);val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
                BitmapFactory.decodeFile(file.absolutePath,bounds)
                check(file.length()>0 && bounds.outWidth==bitmap.width && bounds.outHeight==bitmap.height)
            } catch(failure:Throwable) {GalleryManager.deletePhotoOnly(context,id);throw failure}
        }
    }
}
