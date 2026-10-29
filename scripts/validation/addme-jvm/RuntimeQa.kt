package com.hinnka.mycamera.processor

import android.graphics.Bitmap
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val w = 160; val h = 120
    val first = Bitmap(w, h)
    val second = Bitmap(w, h)
    val firstValues = FloatArray(w*h)
    val secondValues = FloatArray(w*h)
    for (y in 0 until h) for (x in 0 until w) {
        val v = (x*13+y*7+x*y)%160 + 30
        var base = (255 shl 24) or (v shl 16) or (v shl 8) or v
        if (x in 25..40 && y in 30..85) { base = 0xffff0000.toInt(); firstValues[y*w+x] = 1f }
        first.pixels[y*w+x] = base
        second.pixels[y*w+x] = base
        secondValues[y*w+x] = firstValues[y*w+x]
        if (x in 105..123 && y in 25..90) {
            second.pixels[y*w+x] = 0xff00ff00.toInt()
            secondValues[y*w+x] = 1f
        }
    }
    val ma = PersonCompositeTools.Mask(w,h,firstValues)
    val mb = PersonCompositeTools.Mask(w,h,secondValues)
    val fit = PersonCompositeTools.align(first,second,ma,mb)
    check(fit.acceptable && fit.dx == 0f && fit.dy == 0f) { "Static background registration failed: $fit" }
    // The group moves slightly between exposures; existing people must keep shot one's pixels.
    second.pixels[50*w+32] = 0xff0000ff.toInt()
    val added = PersonCompositeTools.addedPersonMask(ma,mb,fit)
    val combined = PersonCompositeTools.render(first,second,added,fit,CompositeRegion(width=1f,height=1f,feather=.03f))
    check(combined.getPixel(32,50) == first.getPixel(32,50)) { "First-shot person lost" }
    check(combined.getPixel(114,50) == second.getPixel(114,50)) { "Second-shot person was not added" }
    check(combined.getPixel(75,50) == first.getPixel(75,50)) { "Background replaced" }
    check(!first.isRecycled && !second.isRecycled) { "Original inputs recycled" }
    val excluded = PersonCompositeTools.render(first,second,mb,fit,CompositeRegion(x=.2f,y=.5f,width=.3f,height=.9f))
    check(excluded.getPixel(114,50) == first.getPixel(114,50)) { "ROI exclusion failed" }
    val unrelated = Bitmap(w,h,IntArray(w*h){0xff000000.toInt()})
    check(!PersonCompositeTools.align(first,unrelated,ma,mb).acceptable) { "Unrelated background accepted" }
    val detailed = Bitmap(640,480)
    val shifted = Bitmap(640,480)
    val random = java.util.Random(73)
    for (i in detailed.pixels.indices) {
        val v = random.nextInt(256)
        detailed.pixels[i] = 0xff000000.toInt() or (v shl 16) or (v shl 8) or v
    }
    for (y in 0 until 480) for (x in 0 until 640) {
        shifted.pixels[y*640+x] = detailed.getPixel((x-3).coerceAtLeast(0),y)
    }
    val empty = PersonCompositeTools.Mask(1,1,floatArrayOf(0f))
    val precise = PersonCompositeTools.align(detailed,shifted,empty,empty)
    check(precise.acceptable && kotlin.math.abs(precise.dx * 640 + 3) < 1.1f) {
        "Three-pixel camera translation was not recovered: $precise"
    }
    println("PASS: new donor person added; changed existing donor person excluded; background retained; ROI excludes donor; unrelated scene rejected; three-pixel camera translation recovered")
}
