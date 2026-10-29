package com.hinnka.mycamera.processor
import android.graphics.Bitmap
import com.hinnka.mycamera.camera.AdvancedCameraMode
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
fun main()=runBlocking {
 val w=320;val h=240
 fun base(x:Int,y:Int):Int { val a=((x/9*71+y/7*53+(x/23)*(y/19)*17) and 255);return 0xff000000.toInt() or (a*0x010101) }
 fun frame(dx:Int,dy:Int,light:Int=-1)=Bitmap(w,h,IntArray(w*h){ val x=it%w;val y=it/w; if(light>=0 && x in light..light+3 && y in 80..85) -1 else base(x-dx,y-dy) })
 val ref=frame(0,0);val reg=PreviewFrameRegistration(ref)
 for ((x,y) in listOf(0 to 0,1 to -1,3 to 2,7 to -6,-12 to 9,16 to 0)) {val s=reg.estimate(frame(x,y));println("shift $x,$y => $s");check(s==PreviewFrameRegistration.Shift(x,y))}
 for(mode in listOf(AdvancedCameraMode.PREVIEW_ND,AdvancedCameraMode.PREVIEW_COMPOSITE)) {
 val b=TemporalPreviewBuffer(mode)
 for ((i,p) in listOf(0 to 0,1 to -1,3 to 2,7 to -6,-12 to 9).withIndex()) b.offer(frame(p.first,p.second),i.toLong(),i.toLong())
 val out=b.snapshot()!!.bitmap
 var error=0L;for(y in 20 until h-20)for(x in 20 until w-20)error+=abs((out.pixels[y*w+x] and 255)-(ref.pixels[y*w+x] and 255))
 println("$mode interior error=$error");check(error==0L);b.close()
 }
 val trails=TemporalPreviewBuffer(AdvancedCameraMode.PREVIEW_COMPOSITE)
 for(i in 0..5)trails.offer(frame(0,0,30+i*30),i.toLong(),i.toLong())
 val out=trails.snapshot()!!.bitmap
 for(i in 0..5)check(out.pixels[82*w+31+i*30]==-1)
 println("Moving light trails retained")
 val reject=reg.estimate(Bitmap(w,h,IntArray(w*h){-1}));check(reject==null);println("Unregistrable frame rejected")
 val large=Bitmap(1536,1536,IntArray(1536*1536){base(it%1536,it/1536)})
 val lr=PreviewFrameRegistration(large);val start=System.nanoTime();repeat(12){check(lr.estimate(large)==PreviewFrameRegistration.Shift(0,0))};println("1536 square registration ms/frame="+(System.nanoTime()-start)/12/1e6)
 val lb=TemporalPreviewBuffer(AdvancedCameraMode.PREVIEW_ND);val begin=System.nanoTime();repeat(12){lb.offer(large.copy(Bitmap.Config.ARGB_8888,false),it.toLong(),it.toLong())};println("1536 square full integration ms/frame="+(System.nanoTime()-begin)/12/1e6);lb.close()
}
