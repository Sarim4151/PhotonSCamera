package com.hinnka.mycamera.video

import android.content.ContentValues
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.hinnka.mycamera.utils.PLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

object VideoMediaStoreWriter {
    private const val TAG = "VideoMediaStoreWriter"

    // The muxer only ever sees this private, seekable file. No public document
    // exists until the completed container has been reopened and validated.
    class PendingVideo internal constructor(
        val file: File,
        internal val dateTakenMs: Long,
        internal val recordingPath: VideoRecordingPath,
        internal val recordingTreeUri: String?
    ) {
        internal val publicationClaimed = AtomicBoolean(false)
    }

    fun createPendingVideo(
        context: Context,
        dateTakenMs: Long = System.currentTimeMillis(),
        recordingPath: VideoRecordingPath = VideoRecordingPath.DCIM_PHOTON,
        recordingTreeUri: String? = null
    ): PendingVideo? = try {
        PendingVideo(File.createTempFile("recording_", ".mp4", context.cacheDir),
            dateTakenMs, recordingPath, recordingTreeUri)
    } catch (e: Exception) {
        PLog.e(TAG, "Failed to create private video staging file", e)
        null
    }

    suspend fun publishPendingVideo(context: Context, pendingVideo: PendingVideo): Uri? {
        if (!pendingVideo.publicationClaimed.compareAndSet(false, true)) return null
        return publishVideo(context, pendingVideo.file, pendingVideo.dateTakenMs,
            pendingVideo.recordingPath, pendingVideo.recordingTreeUri)
    }

    fun discardPendingVideo(context: Context, pendingVideo: PendingVideo) {
        if (!pendingVideo.file.delete() && pendingVideo.file.exists()) {
            PLog.w(TAG, "Failed to delete private recording staging file")
        }
    }

    private fun hasVideoSample(file: File): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            } ?: return false
            extractor.selectTrack(track)
            // A codec configuration/metadata track alone is not a recording.
            val size = extractor.sampleSize
            if (size <= 0 || size > Int.MAX_VALUE ||
                extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC == 0) return false
            extractor.readSampleData(ByteBuffer.allocate(size.toInt()), 0) > 0
        } catch (e: Exception) {
            PLog.w(TAG, "Completed recording has no readable video sample: ${e.message}")
            false
        } finally {
            try { extractor.release() } catch (e: Exception) {
                PLog.w(TAG, "Failed to release video validation extractor: ${e.message}")
            }
        }
    }

    suspend fun publishVideo(
        context: Context,
        sourceFile: File,
        dateTakenMs: Long = System.currentTimeMillis(),
        recordingPath: VideoRecordingPath = VideoRecordingPath.DCIM_PHOTON,
        recordingTreeUri: String? = null
    ): Uri? = withContext(Dispatchers.IO) {
        if (!hasVideoSample(sourceFile)) return@withContext null
        val resolver = context.contentResolver
        val tree = recordingPath == VideoRecordingPath.EXTERNAL_TREE
        var createdUri: Uri? = null
        try {
            val uri = if (tree) {
                require(!recordingTreeUri.isNullOrBlank()) { "External recording requires a tree URI" }
                val treeUri = Uri.parse(recordingTreeUri)
                val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri,
                    DocumentsContract.getTreeDocumentId(treeUri))
                DocumentsContract.createDocument(resolver, parent, "video/mp4", buildFileName(dateTakenMs))
            } else {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, buildFileName(dateTakenMs))
                    put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, recordingPath.relativePath)
                    put(MediaStore.MediaColumns.DATE_ADDED, dateTakenMs / 1000)
                    put(MediaStore.MediaColumns.DATE_MODIFIED, dateTakenMs / 1000)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            } ?: throw IllegalStateException("Cannot create video destination")
            createdUri = uri // Own rollback before attempting to open the provider.
            val descriptor = resolver.openFileDescriptor(uri, "w")
                ?: throw IllegalStateException("Cannot open video destination")
            ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use { output ->
                sourceFile.inputStream().use { input -> input.copyTo(output) }
                output.flush()
                // SAF providers may supply pipes; seekability and fsync are not required.
                try { output.fd.sync() } catch (e: Exception) {
                    PLog.w(TAG, "Video destination does not support fsync: ${e.message}")
                }
            }
            if (!tree) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                    put(MediaStore.MediaColumns.DATE_MODIFIED, System.currentTimeMillis() / 1000)
                }
                check(resolver.update(uri, values, null, null) > 0) { "Video publication updated no rows" }
            }
            uri
        } catch (e: Exception) {
            PLog.e(TAG, "Failed to publish completed video", e)
            createdUri?.let { uri ->
                try {
                    if (tree) DocumentsContract.deleteDocument(resolver, uri)
                    else resolver.delete(uri, null, null)
                } catch (rollback: Exception) {
                    PLog.w(TAG, "Failed to roll back video destination: ${rollback.message}")
                }
            }
            null
        }
    }

    private fun buildFileName(dateTakenMs: Long): String =
        "PhotonCamera_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date(dateTakenMs))}.mp4"
}
