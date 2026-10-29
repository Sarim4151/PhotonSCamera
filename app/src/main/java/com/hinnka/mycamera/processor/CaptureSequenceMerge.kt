package com.hinnka.mycamera.processor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.hinnka.mycamera.gallery.GalleryManager
import com.hinnka.mycamera.gallery.MediaMetadata
import com.hinnka.mycamera.gallery.PhotoProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.UUID

/** Produces an independent rendered JPEG; every source remains untouched. */
object CaptureSequenceMerge {
    suspend fun merge(
        context: Context,
        ids: List<String>,
        operation: PhotoSequenceOperation,
        photoProcessor: PhotoProcessor,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
        detailInputMaxEdge: Int = 0,
        shouldAutoSave: Boolean = false,
        astroHorizon: Float? = null,
    ): String = withContext(Dispatchers.IO) {
        require(ids.size in 2..15 && ids.distinct().size == ids.size)
        val highDetail = operation == PhotoSequenceOperation.SUPER_RESOLUTION
        require(detailInputMaxEdge == 0 || detailInputMaxEdge in 640..8192)
        // Only explicit High Detail user choices limit input size. Other merges use native sources.
        val edge = if (highDetail && detailInputMaxEdge > 0) detailInputMaxEdge else Int.MAX_VALUE
        val inputs = mutableListOf<DiskSequenceFrame>()
        val directory = java.io.File(context.cacheDir, "sequence-${UUID.randomUUID()}")
        check(directory.mkdirs()) { context.getString(com.hinnka.mycamera.R.string.advanced_merge_disk) }
        try {
            for ((index, id) in ids.withIndex()) {
                currentCoroutineContext().ensureActive()
                val metadata = GalleryManager.loadMetadata(context, id) ?: error("Missing source metadata")
                // The renderer still owns a source and destination bitmap for one frame. Budget
                // that real pipeline floor before decoding; never hide a failure by shrinking.
                val sourceFile = GalleryManager.getOriginalImageFile(context, id)
                    ?: error(context.getString(com.hinnka.mycamera.R.string.advanced_source_missing))
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(sourceFile.absolutePath, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0) {
                    context.getString(com.hinnka.mycamera.R.string.advanced_source_bounds)
                }
                val scale = minOf(1.0, edge.toDouble() / maxOf(bounds.outWidth, bounds.outHeight))
                val pixels = (bounds.outWidth * scale).toLong().coerceAtLeast(1) *
                    (bounds.outHeight * scale).toLong().coerceAtLeast(1)
                val budget = SequenceMemoryBudget.snapshot(context)
                // Source, rendered destination and transient renderer bitmap storage are native.
                // Disk rows and metadata remain small Java allocations.
                val needed = pixels * 12L + 32L * 1024 * 1024
                val javaNeeded = bounds.outWidth.toLong() * 32L + 16L * 1024 * 1024
                require(budget.fits(needed, javaNeeded)) {
                    context.getString(com.hinnka.mycamera.R.string.advanced_merge_memory,
                        needed / (1024 * 1024), budget.nativeBytes / (1024 * 1024))
                }
                val original = GalleryManager.loadOriginalBitmap(context, id, maxEdge = edge.takeIf { it < Int.MAX_VALUE })
                    ?: error("Cannot decode source photo")
                var rendered: Bitmap? = null
                try {
                    rendered = photoProcessor.processBitmap(context, id, original, metadata,
                        useComputationalAperture = false, applyFrameWatermark = false)
                    val longest = maxOf(rendered.width, rendered.height)
                    val bounded = if (longest > edge) {
                        Bitmap.createScaledBitmap(rendered,
                            maxOf(1, rendered.width * edge / longest),
                            maxOf(1, rendered.height * edge / longest), true)
                    } else rendered
                    try {
                        check(directory.usableSpace > bounded.width.toLong() * bounded.height * 4L + 16L * 1024 * 1024) {
                            context.getString(com.hinnka.mycamera.R.string.advanced_merge_disk)
                        }
                        inputs += DiskSequenceFrame.write(java.io.File(directory, "$index.argb"), bounded)
                    } finally {
                        if (bounded !== rendered && !bounded.isRecycled) bounded.recycle()
                    }
                } finally {
                    if (rendered !== original) rendered?.let { if (!it.isRecycled) it.recycle() }
                    if (!original.isRecycled) original.recycle()
                }
                onProgress(index + 1, ids.size)
            }
            val result = PhotoSequenceTools.processFrames(inputs, operation, onProgress, astroHorizon)
            try {
                val metadata = MediaMetadata(width = result.bitmap.width, height = result.bitmap.height,
                    customProperties = mapOf(
                        "sequenceOperation" to operation.name,
                        "astroHorizon" to if (operation == PhotoSequenceOperation.ASTRO_STACK) (astroHorizon?.toString() ?: "") else "",
                        "sequenceSources" to ids.joinToString(","),
                        "sequenceResolution" to if (highDetail) {
                            val input = if (detailInputMaxEdge == 0) "original_source" else "explicit_input_max_${detailInputMaxEdge}"
                            input + if (result.reconstructionType == "drizzle_2x") "_2x_reconstruction" else "_native_reconstruction"
                        } else "original_source_dimensions",
                        "sequenceReconstructionType" to (result.reconstructionType ?: ""),
                        "sequenceFallbackReason" to (result.fallbackReason ?: ""),
                        "detailInputMaxEdge" to detailInputMaxEdge.toString(),
                        "sequenceAcceptedFrames" to result.acceptedFrameCount.toString(),
                        "sequenceRejectedFrames" to result.rejectedFrameIndices.joinToString(","),
                    ))
                currentCoroutineContext().ensureActive()
                // Allocation and publication share cancellation protection: no orphan placeholders.
                val outputId = withContext(NonCancellable) {
                    val id = UUID.randomUUID().toString()
                    try {
                        check(GalleryManager.preparePhoto(context, metadata, null, null, false, 1f,
                            includeCropRegionInOutputSize = false, photoId = id) != null) {
                            "Cannot prepare merged photo"
                        }
                        GalleryManager.saveBitmapPhoto(context, id, result.bitmap, shouldAutoSave, photoProcessor, 0f, 0f, 0f, photoQuality = 100)
                        check(GalleryManager.getOriginalImageFile(context, id)?.length()?.let { it > 0L } == true) {
                            "Merged photo was not saved"
                        }
                        id
                    } catch (failure: Throwable) {
                        GalleryManager.deletePhotoOnly(context, id)
                        throw failure
                    }
                }
                currentCoroutineContext().ensureActive()
                outputId
            } finally {
                result.bitmap.recycle()
            }
        } finally {
            inputs.forEach { runCatching { it.close() } }
            directory.deleteRecursively()
        }
    }
}
