package com.hinnka.mycamera.processor

import android.graphics.Bitmap
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.*

fun main(args: Array<String>) = runBlocking {
    // The old full-source guard fails this on an empty 512 MiB heap, before any capture decoding.
    val cameraAndPreview = ByteArray(96 * 1024 * 1024)
    PersonCompositeTools.preflight(4000, 3000, includeRecipeProcessing = true)
    check(cameraAndPreview[0] == 0.toByte())
    val root = Files.createTempDirectory("addme-full-qa-").toFile()
    try {
        val expected = File(args[0]).readLines().filter { it.isNotBlank() }
        val w = 37; val h = 29
        val a = Bitmap(w,h,IntArray(w*h){ i -> 0xff000000.toInt() or ((i*71671) and 0xffffff) })
        val b = Bitmap(w,h,IntArray(w*h){ i -> 0xff000000.toInt() or ((i*35747+314159) and 0xffffff) })
        val mask = PersonCompositeTools.Mask(11,9,FloatArray(99){ (it%13)/12f })
        val rois = listOf(CompositeRegion(width=1f,height=1f,feather=.03f),
            CompositeRegion(x=.21f,y=.77f,width=.4f,height=.55f,dx=.04f,dy=-.08f,feather=.1f),
            CompositeRegion(x=.7f,y=.2f,width=.6f,height=.8f,dx=-.07f,dy=.03f,feather=.005f),
            CompositeRegion(x=0f,y=1f,width=.5f,height=.5f))
        for ((i,roi) in rois.withIndex()) {
            val output = FullResolutionPersonComposite.render(root,"a","b",
                { id -> (if (id=="a") a else b).copy(Bitmap.Config.ARGB_8888,false) }, mask,
                PersonCompositeTools.Alignment((i-1)*.0125f,(1-i)*.01875f,0f,500),roi,{})
            val bytes=ByteBuffer.allocate(w*h*4); for(v in output.pixels) bytes.putInt(v)
            val hash=MessageDigest.getInstance("SHA-256").digest(bytes.array()).joinToString(""){"%02x".format(it)}
            check(hash == expected[i]) { "Blend changed from v1.28.6: case $i" }
            output.recycle()
            check(root.listFiles()!!.isEmpty())
        }
        println("PASS: four full-source spool outputs match pre-change pixel goldens exactly")

        var previous: Bitmap? = null
        val stages = mutableListOf<FullResolutionPersonComposite.Stage>()
        val output = FullResolutionPersonComposite.render(root,"first","second", { id ->
            check(previous == null || previous!!.isRecycled) { "Retained two full source bitmaps" }
            Bitmap(4000,3000,IntArray(12_000_000) { i ->
                val x=i%4000; val y=i/4000
                when {
                    id=="second" && x in 2200..3200 && y in 800..2500 -> 0xff00ff00.toInt()
                    x in 300..900 && y in 1000..2100 -> 0xffff0000.toInt()
                    (x+y)%2==0 -> 0xff202020.toInt()
                    else -> 0xffe0e0e0.toInt()
                }
            }).also { previous=it }
        }, PersonCompositeTools.Mask(40,30,FloatArray(1200){ i ->
            if(i%40 in 22..31 && i/40 in 8..24) 1f else 0f
        }), PersonCompositeTools.Alignment(0f,0f,0f,500), CompositeRegion(width=1f,height=1f,feather=.03f),
            { stages.add(it) })
        check(output.width==4000 && output.height==3000)
        check(output.getPixel(2800,1500)==0xff00ff00.toInt())
        check(output.getPixel(600,1500)==0xffff0000.toInt())
        for(y in 2900..2907) for(x in 3900..3907)
            check(output.getPixel(x,y)==if((x+y)%2==0) 0xff202020.toInt() else 0xffe0e0e0.toInt())
        check(previous!!.isRecycled)
        check(stages==FullResolutionPersonComposite.Stage.values().toList())
        check(root.listFiles()!!.isEmpty())
        output.recycle(); previous=null
        println("PASS: true 4000x3000 output with original single-pixel detail; sources staged/recycled sequentially; cache removed")

        try {
            FullResolutionPersonComposite.render(root,"a","b",{ id -> Bitmap(if(id=="a") 16 else 17,12) },
                mask,PersonCompositeTools.Alignment(0f,0f,0f,500),rois[0],{})
            error("Mismatched dimensions accepted")
        } catch(expectedError:IllegalArgumentException) { check(root.listFiles()!!.isEmpty()) }
        val cancelled=File(root,"cancelled.argb")
        try {
            CompositeArgbSpool.write(cancelled,16,12) { y,row ->
                if(y==3) throw CancellationException("QA cancel")
                row.fill(0xff123456.toInt())
            }
            error("Cancellation ignored")
        } catch(expectedError:CancellationException) { check(root.listFiles()!!.isEmpty()) }
        val truncated=File(root,"truncated.argb")
        CompositeArgbSpool.write(truncated,16,12) { _,row -> row.fill(0xffabcdef.toInt()) }
        RandomAccessFile(truncated,"rw").use { it.setLength(it.length()-1) }
        try { CompositeArgbSpool.open(truncated).use { error("Truncated cache accepted") } }
        catch(expectedError:IllegalArgumentException) { }
        truncated.delete()
        println("PASS: cancellation removes partial cache; mismatched sources and truncated rows rejected")
    } finally { root.deleteRecursively() }
}
