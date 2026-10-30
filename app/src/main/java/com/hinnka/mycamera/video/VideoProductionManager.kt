package com.hinnka.mycamera.video

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.*
import com.hinnka.mycamera.ui.camera.CinemaRecordingSettings
import com.hinnka.mycamera.utils.PLog
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Clip slate and editorial proxies. Timecode is descriptive metadata, not an MP4 SMPTE track. */
object VideoProductionManager {
    data class Slate(
        val scene: String = "", val take: String = "1", val reel: String = "A001",
        val camera: String = "A", val goodTake: Boolean = false,
        val timeOfDay: Boolean = false, val proxyEnabled: Boolean = false,
    )
    enum class Status { IDLE, WORKING, SAVED, FAILED }
    private data class Session(
        val slate: Slate, val startedMs: Long, val captureFps: Int, val playbackFps: Int,
    ) {
        val nominalFps: Int get() = playbackFps.takeIf { it > 0 } ?: captureFps
        val offSpeed: Boolean get() = nominalFps != captureFps
    }
    private fun snapshot(context: Context, fps: Int): Session = Session(
        read(context), System.currentTimeMillis(), fps.coerceAtLeast(1),
        CinemaRecordingSettings.read(context).playbackFps,
    )
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Mutex()
    private var active: Session? = null
    private val mutableStatus = MutableStateFlow(Status.IDLE)
    val status = mutableStatus.asStateFlow()
    private fun prefs(context: Context) = context.getSharedPreferences("video_production", Context.MODE_PRIVATE)
    fun read(context: Context): Slate = prefs(context).let {
        Slate(it.getString("scene", "") ?: "", it.getString("take", "1") ?: "1",
            it.getString("reel", "A001") ?: "A001", it.getString("camera", "A") ?: "A",
            it.getBoolean("good", false), it.getBoolean("tod", false), it.getBoolean("proxy", false))
    }
    fun save(context: Context, slate: Slate) {
        prefs(context).edit().putString("scene", slate.scene).putString("take", slate.take)
            .putString("reel", slate.reel).putString("camera", slate.camera)
            .putBoolean("good", slate.goodTake).putBoolean("tod", slate.timeOfDay)
            .putBoolean("proxy", slate.proxyEnabled).apply()
    }
    @Synchronized fun onRecordingStarted(context: Context, fps: Int) {
        active = snapshot(context, fps)
    }
    @Synchronized fun cancelRecording() { active = null }

    /** Application-owned job survives screen recreation. Source recording is never modified. */
    @Synchronized fun onVideoSaved(context: Context, uri: Uri, fps: Int): Job {
        val app = context.applicationContext
        val session = active ?: snapshot(app, fps)
        active = null
        return scope.launch {
            queue.withLock {
                mutableStatus.value = Status.WORKING
                try {
                    val name = app.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
                        ?.use { if (it.moveToFirst()) it.getString(0) else null }
                        ?.substringBeforeLast('.') ?: "clip_${session.startedMs}"
                    val safeName = name.replace(Regex("[^A-Za-z0-9_.-]"), "_")
                    val json = metadata(uri, session).put("proxyStatus", if (session.slate.proxyEnabled) "pending" else "disabled")
                    val sidecar = createSidecar(app, "$safeName.slate.json", json)
                    if (session.slate.proxyEnabled) {
                        check(Build.VERSION.SDK_INT >= 31) { "Proxy export requires Android 12" }
                        try {
                            val proxy = createProxy(app, uri, "${safeName}_proxy.mp4")
                            json.put("proxyStatus", "complete").put("proxyUri", proxy.toString())
                        } catch (error: Exception) {
                            json.put("proxyStatus", "failed")
                            try { writeJson(app, sidecar, json) }
                            catch (metadataError: Exception) { error.addSuppressed(metadataError) }
                            throw error
                        }
                        writeJson(app, sidecar, json)
                    }
                    mutableStatus.value = Status.SAVED
                } catch (error: Exception) {
                    mutableStatus.value = Status.FAILED
                    PLog.e("VideoProduction", "Clip companion export failed; source remains saved", error)
                }
            }
        }
    }
    private fun metadata(uri: Uri, session: Session): JSONObject {
        val s = session.slate
        val local = Instant.ofEpochMilli(session.startedMs).atZone(ZoneId.systemDefault())
        val frames = (local.nano / 1_000_000_000.0 * session.nominalFps).toInt()
        val timecode = if (s.timeOfDay) local.format(DateTimeFormatter.ofPattern("HH:mm:ss")) + ":%02d".format(frames) else "00:00:00:00"
        return JSONObject().put("schemaVersion", 1).put("sourceUri", uri.toString())
            .put("scene", s.scene).put("take", s.take).put("reel", s.reel).put("camera", s.camera)
            .put("goodTake", s.goodTake).put("recordingStartedAt", Instant.ofEpochMilli(session.startedMs).toString())
            .put("timecodeMode", if (s.timeOfDay) "time_of_day" else "record_run")
            .put("timecodeStart", timecode).put("nominalFps", session.nominalFps)
            .put("captureFps", session.captureFps).put("playbackFps", session.nominalFps)
            .put("offSpeed", session.offSpeed)
            .put("dropFrame", false).put("embeddedSmpteTrack", false)
    }
    private fun createSidecar(context: Context, name: String, json: JSONObject): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/PhotonCamera/Slates")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = checkNotNull(context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        try {
            writeJson(context, uri, json)
            check(context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null) > 0)
            return uri
        } catch (error: Exception) { context.contentResolver.delete(uri, null, null); throw error }
    }
    private fun writeJson(context: Context, uri: Uri, json: JSONObject) {
        checkNotNull(context.contentResolver.openOutputStream(uri, "wt")).bufferedWriter().use { it.write(json.toString(2)) }
    }
    @OptIn(UnstableApi::class)
    private suspend fun createProxy(context: Context, source: Uri, name: String): Uri {
        val file = File.createTempFile("proxy_", ".mp4", context.cacheDir)
        // Transformer requires a path that does not already exist.
        file.delete()
        try {
            withTimeout(30 * 60 * 1000L) { withContext(Dispatchers.Main) {
                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(DefaultEncoderFactory.Builder(context)
                        .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(3_000_000).build()).build())
                    .build()
                val item = EditedMediaItem.Builder(MediaItem.fromUri(source))
                    .setEffects(Effects(emptyList(), listOf(Presentation.createForHeight(720))))
                    .build()
                suspendCancellableCoroutine<Unit> { continuation ->
                    transformer.addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (continuation.isActive) continuation.resume(Unit)
                        }
                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            if (continuation.isActive) continuation.resumeWithException(exportException)
                        }
                    })
                    continuation.invokeOnCancellation { scope.launch(Dispatchers.Main) { transformer.cancel() } }
                    try { transformer.start(item, file.absolutePath) }
                    catch (error: Exception) { if (continuation.isActive) continuation.resumeWithException(error) }
                }
            }
            }
            check(file.length() > 0)
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/PhotonCamera/Proxies")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = checkNotNull(context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values))
            try {
                checkNotNull(context.contentResolver.openOutputStream(uri)).use { output -> file.inputStream().use { it.copyTo(output) } }
                check(context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null) > 0)
                return uri
            } catch (error: Exception) { context.contentResolver.delete(uri, null, null); throw error }
        } finally { file.delete() }
    }
}
