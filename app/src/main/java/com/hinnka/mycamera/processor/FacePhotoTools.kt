package com.hinnka.mycamera.processor

import android.graphics.Bitmap
import android.graphics.RectF
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max

data class FaceRegion(
    val normalizedRect: RectF,
    val leftEyeOpenProbability: Float?,
    val rightEyeOpenProbability: Float?,
    val smileProbability: Float?,
    val trackingId: Int?
)

data class FaceFrameAnalysis(val index: Int, val faces: List<FaceRegion>, val score: Double)

/**
 * Offline still-image analysis. Callers supply upright, software-readable images and keep them
 * alive until this suspending call returns. Inputs are never changed or recycled. Probabilities
 * are estimates, not guarantees; absent classifications remain null. Small/turned/occluded faces
 * can be missed. No identity recognition or correspondence across frames is performed.
 */
object FacePhotoTools {
    private val directExecutor = Executor { it.run() }

    suspend fun analyze(bitmaps: List<Bitmap>): List<FaceFrameAnalysis> = withContext(Dispatchers.Default) {
        require(bitmaps.size in 1..15) { "Expected 1 to 15 frames" }
        require(bitmaps.all { !it.isRecycled && it.config != Bitmap.Config.HARDWARE &&
            it.width > 0 && it.height > 0 && max(it.width, it.height) <= 2048 }) {
            "Frames must be readable upright bitmaps at most 2048 pixels per dimension"
        }
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(.05f)
            .build()
        val detector = FaceDetection.getClient(options)
        try {
            bitmaps.mapIndexed { index, bitmap ->
                currentCoroutineContext().ensureActive()
                val quality = imageQuality(bitmap)
                val task = detector.process(InputImage.fromBitmap(bitmap, 0))
                val detected = try {
                    task.awaitCompletion()
                } catch (cancelled: CancellationException) {
                    // ML Kit Tasks cannot be cancelled. Drain the task before callers may recycle
                    // the bitmap and before closing the detector. Do not launch detached work.
                    withContext(NonCancellable) { runCatching { task.awaitCompletion() } }
                    throw cancelled
                }
                currentCoroutineContext().ensureActive()
                val faces = detected.mapNotNull { face ->
                    val r = face.boundingBox
                    val rect = RectF(
                        (r.left.toFloat() / bitmap.width).coerceIn(0f, 1f),
                        (r.top.toFloat() / bitmap.height).coerceIn(0f, 1f),
                        (r.right.toFloat() / bitmap.width).coerceIn(0f, 1f),
                        (r.bottom.toFloat() / bitmap.height).coerceIn(0f, 1f)
                    )
                    if (rect.isEmpty) null else FaceRegion(rect,
                        validProbability(face.leftEyeOpenProbability),
                        validProbability(face.rightEyeOpenProbability),
                        validProbability(face.smilingProbability), null)
                }
                // Missing estimates contribute neutral .5, never an invented open eye or smile.
                // Average per face prevents face count alone from selecting the winning frame.
                val expression = if (faces.isEmpty()) .5 else faces.map { face ->
                    .4 * (face.leftEyeOpenProbability ?: .5f) +
                        .4 * (face.rightEyeOpenProbability ?: .5f) +
                        .2 * (face.smileProbability ?: .5f)
                }.average()
                FaceFrameAnalysis(index, faces, .4 * quality + .6 * expression)
            }
        } finally {
            detector.close()
        }
    }

    private fun validProbability(value: Float?): Float? =
        value?.takeIf { it.isFinite() && it in 0f..1f }

    private suspend fun <T> Task<T>.awaitCompletion(): T = suspendCancellableCoroutine { continuation ->
        addOnCompleteListener(directExecutor) { completed ->
            if (continuation.isActive) {
                when {
                    completed.isSuccessful -> continuation.resume(completed.result)
                    completed.isCanceled -> continuation.cancel()
                    else -> continuation.resumeWithException(
                        completed.exception ?: IllegalStateException("Face analysis failed"))
                }
            }
        }
    }

    /** Bounded detail/clipping score; only a photographic heuristic, not face confidence. */
    private suspend fun imageQuality(bitmap: Bitmap): Double {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 3 || height < 3) return 0.0
        val rows = Array(3) { IntArray(width) }
        var energy = 0.0
        var clipped = 0
        var count = 0
        val step = max(1, max(width, height) / 384)
        for (y in 1 until height - 1 step step) {
            currentCoroutineContext().ensureActive()
            for (i in 0..2) bitmap.getPixels(rows[i], 0, width, 0, y + i - 1, width, 1)
            for (x in 1 until width - 1 step step) {
                val color = rows[1][x]
                val center = luma(color)
                val lap = abs(4 * center - luma(rows[0][x]) - luma(rows[2][x]) -
                    luma(rows[1][x - 1]) - luma(rows[1][x + 1]))
                energy += lap * lap
                if (center <= .015 || ((color ushr 16) and 255) >= 250 ||
                    ((color ushr 8) and 255) >= 250 || (color and 255) >= 250) clipped++
                count++
            }
        }
        val detail = (ln(1 + 100 * energy / count) / ln(11.0)).coerceIn(0.0, 1.0)
        return detail * (1.0 - clipped.toDouble() / count)
    }

    private fun luma(color: Int): Double =
        (.2126 * ((color ushr 16) and 255) + .7152 * ((color ushr 8) and 255) +
            .0722 * (color and 255)) / 255.0
}
