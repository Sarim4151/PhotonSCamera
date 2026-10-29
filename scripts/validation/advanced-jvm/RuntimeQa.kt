package com.hinnka.mycamera.processor
import android.graphics.Bitmap
import kotlinx.coroutines.runBlocking
import kotlin.math.*
fun main() = runBlocking {
 val w=256; val h=192
 val a=Bitmap(w,h); val b=Bitmap(w,h)
 for(y in 0 until h) for(x in 0 until w) {
  val v=(110+40*sin(x*.13)+40*cos(y*.17)+30*sin((x+y)*.08)+25*sin((x*3-y)*.053)).toInt().coerceIn(0,255)
  val base=(0xff shl 24) or (v shl 16) or (v shl 8) or v
  a.pixels[y*w+x]=if(x in 95..103 && y in 85..98) 0xffeeeeee.toInt() else base
  b.pixels[y*w+x]=if(x in 125..133 && y in 85..98) 0xffeeeeee.toInt() else base
 }
 val result=AdvancedSequenceProcessor.process(listOf(a,b),PhotoSequenceOperation.NIGHT_STACK){_,_->}
 var maxDifference=0
 for(y in 88..95) for(x in 97..101) maxDifference=max(maxDifference,abs((result.bitmap.pixels[y*w+x] and 255)-(a.pixels[y*w+x] and 255)))
 println("Moving edge maximum reference difference: $maxDifference")
 check(maxDifference<=2) { "Night stack ghosts a moving subject: difference=$maxDifference" }
 val still=AdvancedSequenceProcessor.process(listOf(a,a),PhotoSequenceOperation.NIGHT_STACK){_,_->}
 check(still.bitmap.pixels.contentEquals(a.pixels))
 println("PASS: moving subject preserved and static image remains exact")
}
