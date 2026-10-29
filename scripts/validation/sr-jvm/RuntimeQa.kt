package com.hinnka.mycamera.processor
import android.graphics.Bitmap
import kotlinx.coroutines.runBlocking
import java.io.*
data class PhotoSequenceResult(val bitmap:Bitmap,val alignmentOffsets:List<Pair<Int,Int>> = emptyList(),val rejectedFrameIndices:List<Int> = emptyList(),val acceptedFrameCount:Int=0,val bestFrameIndex:Int?=null,val reconstructionType:String?=null,val fallbackReason:String?=null)
fun main(args:Array<String>)= runBlocking {
 val dir=File(args[0]);for(name in listOf("static","moving","native_motion")) {
 val frames=(0..3).map{DataInputStream(File(dir,"${name}_$it.bin").inputStream().buffered()).use{input->val w=input.readInt();val h=input.readInt();Bitmap(w,h,IntArray(w*h){input.readInt()})}}
 val result=SuperResolutionProcessor.process(frames){_,_->}
 DataOutputStream(File(dir,"${name}_output.bin").outputStream().buffered()).use{out->out.writeInt(result.bitmap.width);out.writeInt(result.bitmap.height);result.bitmap.pixels.forEach{out.writeInt(it)}}
 check(result.acceptedFrameCount==4);check(result.rejectedFrameIndices.isEmpty());check(frames.none{it.isRecycled})
 println("$name: ${result.bitmap.width}x${result.bitmap.height}, accepted=${result.acceptedFrameCount}, rejected=${result.rejectedFrameIndices}, inputsOwned=true")
 check(result.reconstructionType==if(name=="native_motion") "aligned_native" else "drizzle_2x")
 if(name=="static") {
 val native=SuperResolutionProcessor.process(List(4){frames[0]}){_,_->}
 check(native.bitmap.width==frames[0].width && native.bitmap.height==frames[0].height)
 check(native.reconstructionType=="aligned_native" && native.fallbackReason!!.contains("independent subpixel"))
 check(native.bitmap.pixels.contentEquals(frames[0].pixels))
 println("identical burst: aligned_native, unchanged pixels")
 val blank=Bitmap(frames[0].width,frames[0].height,IntArray(frames[0].width*frames[0].height){0xff808080.toInt()})
 val reference=SuperResolutionProcessor.process(List(4){blank}){_,_->}
 check(reference.reconstructionType=="reference_native" && reference.acceptedFrameCount==1)
 check(reference.bitmap.pixels.contentEquals(blank.pixels))
 }
 }
}
