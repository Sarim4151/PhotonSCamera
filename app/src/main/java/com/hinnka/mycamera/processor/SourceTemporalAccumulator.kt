package com.hinnka.mycamera.processor

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import com.hinnka.mycamera.camera.AdvancedCameraMode
import com.hinnka.mycamera.gallery.GalleryManager
import com.hinnka.mycamera.gallery.MediaMetadata
import com.hinnka.mycamera.gallery.StreamingJpegEncoder
import com.hinnka.mycamera.gallery.PhotoProcessor
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.util.UUID
import kotlin.math.pow

/** Full source dimensions; only row-sized float arrays are held in RAM. A completed checkpoint
 * contains its own source IDs. Atomic replacement prevents partial frames becoming recoverable. */
class SourceTemporalAccumulator(private val context: Context) {
    private val directory = File(context.filesDir, "source-temporal").apply { mkdirs() }
    private val checkpoint = File(directory, "checkpoint.bin")
    val recoverable get() = checkpoint.isFile
    private val linear = FloatArray(256) { val v = it / 255f; if (v <= .04045f) v / 12.92f else ((v + .055f) / 1.055f).pow(2.4f) }
    private var freshSession = false
    /** Preserve the previous recoverable checkpoint until the first new frame is fully committed. */
    fun clear() { freshSession = true; File(directory, "pending.bin").delete() }
    private fun requireMemory(pixels: Long, nativeBytesPerPixel: Int, width: Int) {
        val budget = SequenceMemoryBudget.snapshot(context)
        val needed = pixels * nativeBytesPerPixel + 32L * 1024 * 1024
        val javaNeeded = width.toLong() * 32L + 8L * 1024 * 1024
        require(pixels > 0 && pixels < Int.MAX_VALUE / 4 && budget.fits(needed, javaNeeded)) {
            context.getString(com.hinnka.mycamera.R.string.advanced_source_memory,
                needed / (1024 * 1024), budget.nativeBytes / (1024 * 1024))
        }
    }
    private fun header(input: DataInputStream, onSize: ((Int) -> Unit)? = null): JSONObject {
        val size = input.readInt()
        require(size in 1..4_000_000)
        val bytes = ByteArray(size); input.readFully(bytes)
        onSize?.invoke(size)
        return JSONObject(String(bytes, Charsets.UTF_8))
    }
    data class Summary(val mode: AdvancedCameraMode, val frames: Int, val elapsedSeconds: Int)
    suspend fun summary(): Summary = withContext(Dispatchers.IO) {
        DataInputStream(BufferedInputStream(FileInputStream(checkpoint))).use { input ->
            val h = header(input)
            Summary(AdvancedCameraMode.valueOf(h.getString("mode")), h.getJSONArray("sources").length(),
                ((h.getLong("time") - h.optLong("startedAt", h.getLong("time"))) / 1000L).toInt().coerceAtLeast(0))
        }
    }
    suspend fun offer(id: String, mode: AdvancedCameraMode, intensity: Float, processor: PhotoProcessor) = withContext(Dispatchers.IO) {
        val metadata = GalleryManager.loadMetadata(context, id) ?: error(context.getString(com.hinnka.mycamera.R.string.advanced_source_metadata_missing))
        val sourceFile = GalleryManager.getOriginalImageFile(context, id) ?: error(context.getString(com.hinnka.mycamera.R.string.advanced_source_missing))
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(sourceFile.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { context.getString(com.hinnka.mycamera.R.string.advanced_source_bounds) }
        requireMemory(bounds.outWidth.toLong() * bounds.outHeight, 12, bounds.outWidth)
        val original = GalleryManager.loadOriginalBitmap(context, id, maxEdge = null) ?: error(context.getString(com.hinnka.mycamera.R.string.advanced_source_missing))
        var rendered: Bitmap? = null
        val pending = File(directory, "pending.bin")
        try {
            val bitmap = processor.processBitmap(context, id, original, metadata,
                useComputationalAperture = false, applyFrameWatermark = false).also { rendered = it }
            check(directory.usableSpace > bitmap.width.toLong() * bitmap.height * 24L + 16L * 1024 * 1024) { context.getString(com.hinnka.mycamera.R.string.advanced_source_disk) }
            val old = if (!freshSession && checkpoint.exists()) DataInputStream(BufferedInputStream(FileInputStream(checkpoint))) else null
            old.use { previous ->
                val h = previous?.let { header(it) }
                check(h == null || (h.getInt("width") == bitmap.width && h.getInt("height") == bitmap.height && h.getString("mode") == mode.name)) { context.getString(com.hinnka.mycamera.R.string.advanced_source_changed) }
                val ids = h?.getJSONArray("sources") ?: JSONArray()
                check((0 until ids.length()).none { ids.getString(it) == id })
                ids.put(id)
                val next = JSONObject().put("width", bitmap.width).put("height", bitmap.height).put("mode", mode.name)
                    .put("sources", ids).put("intensity", intensity).put("time", System.currentTimeMillis())
                    .put("startedAt", h?.let { it.optLong("startedAt", it.optLong("time")) } ?: System.currentTimeMillis())
                    .put("sourceRendering", "capture_metadata_once")
                    .put("firstSourceMetadata", h?.optString("firstSourceMetadata") ?: metadata.toString())
                FileOutputStream(pending).use { file ->
                    val output = DataOutputStream(BufferedOutputStream(file))
                    val json = next.toString().toByteArray(Charsets.UTF_8)
                    output.writeInt(json.size); output.write(json)
                    val row = IntArray(bitmap.width)
                    for (y in 0 until bitmap.height) {
                        currentCoroutineContext().ensureActive()
                        bitmap.getPixels(row, 0, row.size, 0, y, row.size, 1)
                        for (pixel in row) for (ch in 0..2) {
                            val v = linear[(pixel ushr (16 - ch * 8)) and 255]
                            val base = previous?.readFloat() ?: v
                            val accumulated = previous?.readFloat() ?: v
                            val merged = if (mode == AdvancedCameraMode.LIVE_ND) accumulated + (v - accumulated) / ids.length()
                                else maxOf(accumulated, base + maxOf(0f, v - base) * intensity.coerceIn(.1f, 8f))
                            output.writeFloat(base); output.writeFloat(merged)
                        }
                    }
                    output.flush(); file.fd.sync()
                }
                check(pending.renameTo(checkpoint)) { context.getString(com.hinnka.mycamera.R.string.advanced_checkpoint_failed) }
                freshSession = false
            }
        } finally {
            rendered?.let { if (it !== original && !it.isRecycled) it.recycle() }
            if (!original.isRecycled) original.recycle()
            pending.delete()
        }
    }
    suspend fun render(maxEdge: Int = Int.MAX_VALUE): Bitmap {
        var owned: Bitmap? = null
        try {
            return withContext(Dispatchers.IO) { renderNow(maxEdge).also { owned = it } }
        } catch (failure: Throwable) {
            owned?.let { if (!it.isRecycled) it.recycle() }
            throw failure
        }
    }
    private suspend fun renderNow(maxEdge: Int): Bitmap = run {
        require(maxEdge > 0)
        val fileInput = FileInputStream(checkpoint)
        DataInputStream(BufferedInputStream(fileInput)).use { input ->
            var headerBytes = 0
            val h = header(input) { headerBytes = it }
            val w = h.getInt("width"); val height = h.getInt("height")
            // FileInputStream.skip may seek beyond EOF. Validate this open file before skipping.
            require(w > 0 && height > 0 && w.toLong() * height < Int.MAX_VALUE / 4)
            if (fileInput.channel.size() != 4L + headerBytes + w.toLong() * height * 24L) {
                throw EOFException("Incomplete temporal checkpoint")
            }
            val step = maxOf(1, (maxOf(w, height).toLong() + maxEdge - 1L).div(maxEdge).toInt())
            val outWidth = (w + step - 1) / step; val outHeight = (height + step - 1) / step
            requireMemory(outWidth.toLong() * outHeight, 4, outWidth)
            val out = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
            try {
                val row = IntArray(out.width)
                for (y in 0 until height) {
                    currentCoroutineContext().ensureActive()
                    // A checkpoint pixel is three (reference, accumulated) float pairs.
                    // For small previews, seek past unsampled data instead of decoding every float.
                    if (y % step != 0) {
                        skipCheckpointBytes(input, w.toLong() * 24L)
                        continue
                    }
                    for (x in 0 until w step step) {
                        var pixel = 0xff000000.toInt()
                        for (ch in 0..2) {
                            input.readFloat(); val v = input.readFloat().coerceIn(0f, 1f)
                            val encoded = if (v <= .0031308f) v * 12.92f else 1.055f * v.pow(1f / 2.4f) - .055f
                            pixel = pixel or ((encoded * 255 + .5f).toInt().coerceIn(0, 255) shl (16 - ch * 8))
                        }
                        row[x / step] = pixel
                        skipCheckpointBytes(input, (minOf(step, w - x) - 1).toLong() * 24L)
                    }
                    out.setPixels(row, 0, row.size, 0, y / step, row.size, 1)
                }
                out
            } catch (e: Throwable) { out.recycle(); throw e }
        }
    }
    private fun skipCheckpointBytes(input: DataInputStream, count: Long) {
        var remaining = count
        while (remaining > 0L) {
            val skipped = input.skip(remaining)
            if (skipped > 0L) remaining -= skipped
            else {
                // A stream may return zero before EOF. Force progress or report truncation.
                input.readByte()
                remaining--
            }
        }
    }
    suspend fun publish(processor: PhotoProcessor, shouldAutoSave: Boolean = false): String = withContext(Dispatchers.IO) {
        // Keep this descriptor open for header and samples: an atomic checkpoint replacement
        // cannot change the image or metadata halfway through publication.
        val jpeg = File(context.cacheDir, "temporal-publish-${UUID.randomUUID()}.jpg")
        try {
            val fileInput = FileInputStream(checkpoint)
            val h = DataInputStream(BufferedInputStream(fileInput)).use { input ->
                var headerBytes = 0
                val snapshot = header(input) { headerBytes = it }
                check(snapshot.getJSONArray("sources").length() >= 2) {
                    context.getString(com.hinnka.mycamera.R.string.advanced_need_two_frames)
                }
                val width = snapshot.getInt("width"); val height = snapshot.getInt("height")
                require(width in 1..StreamingJpegEncoder.MAX_DIMENSION && height in 1..StreamingJpegEncoder.MAX_DIMENSION)
                require(width.toLong() * height < Int.MAX_VALUE / 4)
                if (fileInput.channel.size() != 4L + headerBytes + width.toLong() * height * 24L) {
                    throw EOFException("Incomplete temporal checkpoint")
                }
                val budget = SequenceMemoryBudget.snapshot(context)
                check(budget.fits(16L * 1024 * 1024 + width.toLong() * 64L,
                    8L * 1024 * 1024 + width.toLong() * 32L)) {
                    context.getString(com.hinnka.mycamera.R.string.advanced_stream_memory)
                }
                check(context.cacheDir.usableSpace > width.toLong() * height * 4L + 16L * 1024 * 1024) {
                    context.getString(com.hinnka.mycamera.R.string.advanced_merge_disk)
                }
                val rows = CheckpointSrgbRows(input, width, height, currentCoroutineContext())
                check(StreamingJpegEncoder.write(context, jpeg, width, height, quality = 100, fillRow = rows::fill) && rows.rowsRead == height) {
                    context.getString(com.hinnka.mycamera.R.string.advanced_temporal_encode_failed)
                }
                check(input.read() == -1) { "Unexpected trailing checkpoint samples" }
                snapshot
            }
            currentCoroutineContext().ensureActive()
            val id = UUID.randomUUID().toString()
            // Publication is the commit point. Cancellation during encoding leaves the checkpoint
            // recoverable and deletes only our temporary JPEG; failed commits roll back the row.
            withContext(NonCancellable) {
                try {
                    val metadata = MediaMetadata(width = h.getInt("width"), height = h.getInt("height"), customProperties = mapOf(
                        "sequenceOperation" to h.getString("mode"), "sequenceSources" to h.getJSONArray("sources").toString(),
                        "sequenceResolution" to "original_source_dimensions", "linearLight" to "true", "checkpoint" to h.toString(),
                        "sourceRendering" to "capture_metadata_baked_once", "sourceMetadataSnapshot" to h.optString("firstSourceMetadata"),
                        "sequencePublication" to "streamed_checkpoint_srgb_jpeg"))
                    check(GalleryManager.preparePhoto(context, metadata, null, null, false, 1f,
                        includeCropRegionInOutputSize = false, photoId = id) != null)
                    GalleryManager.saveEncodedJpegPhoto(context, id, jpeg, shouldAutoSave, processor)
                    check(GalleryManager.getOriginalImageFile(context, id)?.length()?.let { it > 0 } == true)
                } catch (failure: Throwable) {
                    GalleryManager.deletePhotoOnly(context, id)
                    throw failure
                }
            }
            id
        } finally { jpeg.delete() }
    }
}
