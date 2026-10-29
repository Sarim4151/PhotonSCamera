package android.graphics
class Bitmap(val width:Int,val height:Int,val pixels:IntArray=IntArray(width*height)) {
 enum class Config { ARGB_8888 }
 var isRecycled=false
 fun recycle(){isRecycled=true}
 fun getPixels(dst:IntArray,offset:Int,stride:Int,x:Int,y:Int,w:Int,h:Int){check(!isRecycled);for(row in 0 until h)System.arraycopy(pixels,(y+row)*width+x,dst,offset+row*stride,w)}
 fun setPixels(src:IntArray,offset:Int,stride:Int,x:Int,y:Int,w:Int,h:Int){check(!isRecycled);for(row in 0 until h)System.arraycopy(src,offset+row*stride,pixels,(y+row)*width+x,w)}
 companion object {
 fun createBitmap(w:Int,h:Int,config:Config)=Bitmap(w,h)
 fun createBitmap(src:Bitmap,x:Int,y:Int,w:Int,h:Int):Bitmap {if(x==0&&y==0&&w==src.width&&h==src.height)return src;return Bitmap(w,h).also{src.getPixels(it.pixels,0,w,x,y,w,h)}}
 }
}
