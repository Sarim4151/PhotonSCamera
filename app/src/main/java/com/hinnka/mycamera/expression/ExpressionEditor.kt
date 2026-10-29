package com.hinnka.mycamera.expression

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Matrix
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceLandmark
import com.hinnka.mycamera.utils.PLog
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.hinnka.mycamera.data.ContentRepository
import com.hinnka.mycamera.gallery.GalleryManager
import com.hinnka.mycamera.gallery.MediaMetadata
import com.hinnka.mycamera.processor.SequenceMemoryBudget
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import kotlin.math.*

object ExpressionEditor {
    private val worker = Mutex()
    suspend fun detect(context: Context, source: Bitmap): List<ExpressionWarp.Face> = withContext(Dispatchers.IO) {
        worker.withLock {
            val detectionEdge = if (max(source.width, source.height) > 1024) 1536f else 1024f
            val scale = min(1f, detectionEdge / max(source.width, source.height))
            // MediaPipe requires an owned ARGB_8888 bitmap; scaling alone preserves F16/hardware config.
            val small = Bitmap.createBitmap((source.width * scale).toInt().coerceAtLeast(1),
                (source.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            try {
                val readable = if (source.config == Bitmap.Config.HARDWARE)
                    source.copy(Bitmap.Config.ARGB_8888, false) ?: error("Unable to read photo") else source
                try {
                    Canvas(small).drawBitmap(readable, null, android.graphics.Rect(0, 0, small.width, small.height),
                        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                } finally { if (readable !== source) readable.recycle() }
                val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath("face_landmarker.task").build())
                    .setRunningMode(RunningMode.IMAGE).setNumFaces(8)
                    .setMinFaceDetectionConfidence(.35f).setMinFacePresenceConfidence(.35f).build()
                try {
                    FaceLandmarker.createFromOptions(context, options).use { model ->
                        for (angle in intArrayOf(0, 90, -90, 180)) {
                            currentCoroutineContext().ensureActive()
                            val matrix = Matrix().apply { postRotate(angle.toFloat()) }
                            val rotated = if (angle == 0) small else Bitmap.createBitmap(small, 0, 0,
                                small.width, small.height, matrix, true)
                            try {
                                val image = BitmapImageBuilder(rotated).build()
                                val found = try {
                                    model.detect(image).faceLandmarks().mapNotNull { marks ->
                                        if (marks.size <= 291) null else {
                                            fun p(i: Int) = originalPoint(marks[i].x() * rotated.width,
                                                marks[i].y() * rotated.height, angle, small, source)
                                            validFace(p(61), p(291), p(13), p(14), source)
                                        }
                                    }
                                } finally { image.close() }
                                currentCoroutineContext().ensureActive()
                                if (found.isNotEmpty()) return@withLock found.sortedBy { it.left.x }
                            } finally { if (rotated !== small) rotated.recycle() }
                        }
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { PLog.w("ExpressionEditor", "Landmarker unavailable; trying bundled face contours", failure) }
                // Independent bundled detector uses measured lip contours, never guessed mouth geometry.
                val detector = FaceDetection.getClient(FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                    .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL).setMinFaceSize(.05f).build())
                try {
                    for (angle in intArrayOf(0, 90, -90, 180)) {
                        currentCoroutineContext().ensureActive()
                        val rotated = if (angle == 0) small else Bitmap.createBitmap(small, 0, 0,
                            small.width, small.height, Matrix().apply { postRotate(angle.toFloat()) }, true)
                        try {
                            val found = Tasks.await(detector.process(InputImage.fromBitmap(rotated, 0))).mapNotNull { face ->
                                val a = face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position ?: return@mapNotNull null
                                val b = face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position ?: return@mapNotNull null
                                val upper = face.getContour(FaceContour.UPPER_LIP_BOTTOM)?.points ?: return@mapNotNull null
                                val lower = face.getContour(FaceContour.LOWER_LIP_TOP)?.points ?: return@mapNotNull null
                                if (upper.isEmpty() || lower.isEmpty()) return@mapNotNull null
                                fun p(x: Float, y: Float) = originalPoint(x, y, angle, small, source)
                                val u = upper[upper.size / 2]; val l = lower[lower.size / 2]
                                validFace(p(a.x,a.y), p(b.x,b.y), p(u.x,u.y), p(l.x,l.y), source)
                            }
                            currentCoroutineContext().ensureActive()
                            if (found.isNotEmpty()) return@withLock found.sortedBy { it.left.x }
                        } finally { if (rotated !== small) rotated.recycle() }
                    }
                    emptyList()
                } finally { detector.close() }
            } finally { small.recycle() }
        }
    }

    private fun originalPoint(x: Float, y: Float, angle: Int, small: Bitmap, source: Bitmap): ExpressionWarp.Point {
        val (sx, sy) = when (angle) {
            90 -> y to (small.height - x)
            -90 -> (small.width - y) to x
            180 -> (small.width - x) to (small.height - y)
            else -> x to y
        }
        return ExpressionWarp.Point(sx * source.width / small.width, sy * source.height / small.height)
    }

    private fun validFace(a: ExpressionWarp.Point, b: ExpressionWarp.Point, upper: ExpressionWarp.Point,
        lower: ExpressionWarp.Point, source: Bitmap): ExpressionWarp.Face? {
        if (listOf(a,b,upper,lower).any { !it.x.isFinite() || !it.y.isFinite() ||
                it.x < 0 || it.y < 0 || it.x >= source.width || it.y >= source.height }) return null
        if (hypot(a.x-b.x,a.y-b.y) < 8f) return null
        return ExpressionWarp.Face(if (a.x < b.x) a else b, if (a.x < b.x) b else a, upper, lower)
    }

    suspend fun render(source: Bitmap, face: ExpressionWarp.Face, strength: Float, relax: Boolean): Bitmap =
        renderOwned(source, face, strength, relax)

    private suspend fun renderOwned(source: Bitmap, face: ExpressionWarp.Face, strength: Float, relax: Boolean): Bitmap {
        var owned: Bitmap? = null
        var readableOwned: Bitmap? = null
        try {
            val result = withContext(Dispatchers.Default) {
            worker.withLock {
                val width = source.width; val height = source.height
                val radius = hypot(face.right.x-face.left.x, face.right.y-face.left.y) * .34f
                val x0 = floor(min(face.left.x,face.right.x)-radius).toInt().coerceIn(0,width-1)
                val x1 = ceil(max(face.left.x,face.right.x)+radius).toInt().coerceIn(0,width-1)
                val y0 = floor(min(face.left.y,face.right.y)-radius).toInt().coerceIn(0,height-1)
                val y1 = ceil(max(face.left.y,face.right.y)+radius).toInt().coerceIn(0,height-1)
                val pad = ceil(radius * .3f).toInt() + 2
                val bx0=(x0-pad).coerceAtLeast(0); val by0=(y0-pad).coerceAtLeast(0)
                val bx1=(x1+pad).coerceAtMost(width-1); val by1=(y1+pad).coerceAtMost(height-1)
                val bw=bx1-bx0+1; val bh=by1-by0+1
                // API30+ bitmap pixels are native; only patch arrays count toward Java headroom.
                val nativeNeeded = width.toLong() * height *
                    (if (source.config == Bitmap.Config.HARDWARE) 8L else 4L) + 16L * 1024 * 1024
                val javaNeeded = bw.toLong() * bh * 4L + (x1-x0+1) * 4L + 4L * 1024 * 1024
                check(SequenceMemoryBudget.snapshot().fits(nativeNeeded, javaNeeded)) {
                    "Not enough memory; select 2048 px export"
                }
                if (source.config == Bitmap.Config.HARDWARE) {
                    readableOwned = source.copy(Bitmap.Config.ARGB_8888, false) ?: error("Unable to read photo")
                }
                val readable = readableOwned ?: source
                val outputBitmap = source.copy(Bitmap.Config.ARGB_8888, true)
                    ?: error("Unable to allocate expression output")
                owned = outputBitmap
                val input=IntArray(bw*bh)
                readable.getPixels(input,0,bw,bx0,by0,bw,bh)
                val row=IntArray(x1-x0+1)
                try {
                for (y in y0..y1) {
                    readable.getPixels(row,0,row.size,x0,y,row.size,1)
                    currentCoroutineContext().ensureActive()
                    for (x in x0..x1) {
                        val d = ExpressionWarp.displacement(x.toFloat(),y.toFloat(),face,strength,relax)
                        if (d.x == 0f && d.y == 0f) continue
                        val sx = (x-d.x).coerceIn(0f,(width-1).toFloat())
                        val sy = (y-d.y).coerceIn(0f,(height-1).toFloat())
                        val ix = sx.toInt()-bx0; val iy = sy.toInt()-by0; val fx = sx-sx.toInt(); val fy = sy-sy.toInt()
                        val a=input[iy*bw+ix]; val b=input[iy*bw+min(ix+1,bw-1)]
                        val c=input[min(iy+1,bh-1)*bw+ix]; val e=input[min(iy+1,bh-1)*bw+min(ix+1,bw-1)]
                        var pixel=0
                        for (shift in intArrayOf(0,8,16,24)) {
                            val top=((a ushr shift) and 255)*(1-fx)+((b ushr shift) and 255)*fx
                            val bot=((c ushr shift) and 255)*(1-fx)+((e ushr shift) and 255)*fx
                            pixel = pixel or ((top*(1-fy)+bot*fy).roundToInt().coerceIn(0,255) shl shift)
                        }
                        row[x-x0]=pixel
                    }
                    outputBitmap.setPixels(row,0,row.size,x0,y,row.size,1)
                }
                outputBitmap
                } catch (failure: Throwable) { throw failure }
            }
            }
            owned = null
            return result
        } finally { owned?.recycle(); readableOwned?.recycle() }
    }

    suspend fun save(context: Context, sourceId: String, bitmap: Bitmap) = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        withContext(NonCancellable) {
            val id=UUID.randomUUID().toString()
            try {
                val metadata=MediaMetadata(width=bitmap.width,height=bitmap.height,dateTaken=System.currentTimeMillis(),
                    customProperties=mapOf("expressionSource" to sourceId,"expressionTool" to "local_mouth_warp"))
                check(GalleryManager.preparePhoto(context,metadata,null,null,false,1f,
                    includeCropRegionInOutputSize=false,photoId=id)==id)
                GalleryManager.saveBitmapPhoto(context,id,bitmap,false,ContentRepository.getInstance(context).photoProcessor,0f,0f,0f)
                val file=GalleryManager.getPhotoFile(context,id)
                val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
                BitmapFactory.decodeFile(file.absolutePath,bounds)
                check(file.length()>0 && bounds.outWidth==bitmap.width && bounds.outHeight==bitmap.height)
            } catch (failure: Exception) { GalleryManager.deletePhotoOnly(context,id); throw failure }
        }
    }
}
