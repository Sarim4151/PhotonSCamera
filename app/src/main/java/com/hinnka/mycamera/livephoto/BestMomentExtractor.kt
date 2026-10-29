package com.hinnka.mycamera.livephoto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import com.hinnka.mycamera.data.ContentRepository
import com.hinnka.mycamera.gallery.GalleryManager
import com.hinnka.mycamera.gallery.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Each request owns its decoder; cancelled requests never publish or leak their bitmap. */
object BestMomentExtractor {
    data class SuggestedMoment(val timeMs: Long, val score: Int)

    /** Bounded technical ranking; no expression or artistic-quality inference. */
    suspend fun suggestions(file: File, durationMs: Long): List<SuggestedMoment> = withContext(Dispatchers.Default) {
        data class Sample(val time: Long, val edge: Double, val clipped: Double, val motion: Double)
        val samples = mutableListOf<Sample>()
        val decoder = MediaMetadataRetriever()
        var previous: IntArray? = null
        try {
            decoder.setDataSource(file.absolutePath)
            val count = (durationMs / 150 + 1).coerceIn(2, 24).toInt()
            repeat(count) { index ->
                currentCoroutineContext().ensureActive()
                val time = (durationMs - 1).coerceAtLeast(0) * index / (count - 1)
                val bitmap = decoder.getScaledFrameAtTime(time * 1000,
                    MediaMetadataRetriever.OPTION_CLOSEST, 192, 192)
                if (bitmap != null) try {
                    val pixels = IntArray(bitmap.width * bitmap.height)
                    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    val luma = IntArray(pixels.size) { p -> val c = pixels[p]
                        ((c shr 16 and 255) * 77 + (c shr 8 and 255) * 150 + (c and 255) * 29) shr 8 }
                    val prior = previous?.takeIf { it.size == luma.size }
                    var edge = 0.0; var clipped = 0; var motion = 0.0
                    for (y in 1 until bitmap.height - 1) {
                        currentCoroutineContext().ensureActive()
                        for (x in 1 until bitmap.width - 1) {
                            val p = y * bitmap.width + x
                            val lap = 4 * luma[p] - luma[p-1] - luma[p+1] - luma[p-bitmap.width] - luma[p+bitmap.width]
                            edge += lap.toDouble() * lap
                        }
                    }
                    luma.forEachIndexed { p, value ->
                        if (value <= 5 || value >= 250) clipped++
                        if (prior != null) motion += kotlin.math.abs(value - prior[p])
                    }
                    samples += Sample(time, edge / luma.size, clipped.toDouble() / luma.size,
                        if (prior == null) Double.NaN else motion / luma.size / 255)
                    previous = luma
                } finally { bitmap.recycle() }
            }
        } finally { decoder.release() }
        currentCoroutineContext().ensureActive()
        val maxEdge = samples.maxOfOrNull { it.edge }?.coerceAtLeast(1.0) ?: return@withContext emptyList()
        val ranked = samples.mapIndexed { index, sample ->
            val motion = listOfNotNull(sample.motion.takeIf { it.isFinite() },
                samples.getOrNull(index+1)?.motion?.takeIf { it.isFinite() }).average().takeIf { it.isFinite() } ?: 0.0
            SuggestedMoment(sample.time, (100 * (.75 * sample.edge / maxEdge + .15 * (1-sample.clipped) +
                .10 * (1-motion))).toInt().coerceIn(0,100))
        }.sortedByDescending { it.score }
        val selected = mutableListOf<SuggestedMoment>()
        for (candidate in ranked) {
            if (selected.all { kotlin.math.abs(it.timeMs-candidate.timeMs) >= durationMs/8 }) selected += candidate
            if (selected.size == 3) break
        }
        selected
    }

    suspend fun duration(file: File): Long = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: error("Missing video duration")).also { check(it > 0) }
        } finally { retriever.release() }
    }

    suspend fun frame(file: File, timeMs: Long, preview: Boolean): Bitmap {
        var owned: Bitmap? = null
        try {
            val result = withContext(Dispatchers.IO) {
                val retriever = MediaMetadataRetriever()
                try {
                    currentCoroutineContext().ensureActive()
                    retriever.setDataSource(file.absolutePath)
                    // Android's retriever applies video rotation; do not rotate it twice.
                    owned = if (preview) retriever.getScaledFrameAtTime(timeMs * 1000,
                        MediaMetadataRetriever.OPTION_CLOSEST, 1280, 1280)
                    else retriever.getFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
                    currentCoroutineContext().ensureActive()
                    owned ?: error("Video frame unavailable")
                } finally { retriever.release() }
            }
            // Transfer only after the cancellable dispatcher handoff has completed.
            owned = null
            return result
        } finally { owned?.recycle() }
    }

    suspend fun save(context: Context, file: File, sourceId: String, timeMs: Long): Unit =
        withContext(Dispatchers.IO) {
            val bitmap = frame(file, timeMs, preview = false)
            val id = UUID.randomUUID().toString()
            try {
                currentCoroutineContext().ensureActive()
                // Save/rollback form one transaction even if the dialog leaves composition.
                withContext(NonCancellable) {
                    try {
                        val metadata = MediaMetadata(width = bitmap.width, height = bitmap.height,
                            dateTaken = System.currentTimeMillis(), customProperties = mapOf(
                                "bestMomentSource" to sourceId, "bestMomentTimeMs" to timeMs.toString(),
                                "bestMomentResolution" to "video_frame"))
                        check(GalleryManager.preparePhoto(context, metadata, null, null, false, 1f,
                            includeCropRegionInOutputSize = false, photoId = id) == id)
                        GalleryManager.saveBitmapPhoto(context, id, bitmap, false,
                            ContentRepository.getInstance(context).photoProcessor, 0f, 0f, 0f)
                        val output = GalleryManager.getPhotoFile(context, id)
                        check(output.length() > 0)
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(output.absolutePath, bounds)
                        check(bounds.outWidth == bitmap.width && bounds.outHeight == bitmap.height)
                    } catch (failure: Exception) {
                        GalleryManager.deletePhotoOnly(context, id)
                        throw failure
                    }
                }
            } finally { bitmap.recycle() }
        }
}
