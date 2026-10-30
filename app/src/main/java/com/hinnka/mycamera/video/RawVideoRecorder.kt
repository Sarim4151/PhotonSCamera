package com.hinnka.mycamera.video

import android.content.ContentValues
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Surface
import org.json.JSONObject
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicInteger

/** Sensor RAW frames stored as individual DNGs; no video container or audio track. */
data class RawVideoState(
    val recording: Boolean = false, val stopping: Boolean = false,
    val savedFrames: Int = 0, val droppedFrames: Int = 0,
    val requestedFps: Int = 1, val targetFps: Int = 1, val error: String? = null
)
data class RawVideoResult(val directory: String, val savedFrames: Int, val droppedFrames: Int, val error: String?)

class RawVideoRecorder {
    private lateinit var context: Context
    private lateinit var handler: Handler
    private lateinit var characteristics: CameraCharacteristics
    private lateinit var stateCallback: (RawVideoState) -> Unit
    private lateinit var finishedCallback: (RawVideoResult) -> Unit
    private var reader: ImageReader? = null
    private var session: CameraCaptureSession? = null
    private var requestBuilder: CaptureRequest.Builder? = null
    private var configureSettings: (CaptureRequest.Builder) -> Unit = {}
    private var frameWidth = 0
    private var frameHeight = 0
    private var stopping = false
    private var finalizing = false
    private var requested = 1
    private var target = 1
    private var directory = ""
    private var error: String? = null
    private val saved = AtomicInteger()
    private val dropped = AtomicInteger()
    private val rateSkipped = AtomicInteger()
    private var lastSelectedTimestamp: Long? = null
    private var sequence = 0L
    private val images = LinkedHashMap<Long, Image>()
    private val results = LinkedHashMap<Long, Pair<Long, TotalCaptureResult>>()
    private val writer = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue<Runnable>(2))
    private var firstTimestamp: Long? = null
    private var lastTimestamp: Long? = null
    private var metadataUri: Uri? = null
    private var metadata: java.io.BufferedWriter? = null
    private val startedAt = System.currentTimeMillis()

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(s: CameraCaptureSession, r: CaptureRequest, result: TotalCaptureResult) {
            if (stopping) return
            val index = sequence++
            val timestamp = result.get(CaptureResult.SENSOR_TIMESTAMP)
            if (timestamp == null) { dropped.incrementAndGet(); return }
            results[timestamp] = index to result
            pair(timestamp)
            while (results.size > 32) { results.remove(results.keys.first()); dropped.incrementAndGet() }
        }
        override fun onCaptureFailed(s: CameraCaptureSession, r: CaptureRequest, failure: CaptureFailure) {
            sequence++
            dropped.incrementAndGet()
            publish()
        }
    }

    fun start(context: Context, device: CameraDevice, characteristics: CameraCharacteristics,
              previewSurface: Surface, handler: Handler, captureFps: Int,
              onState: (RawVideoState) -> Unit, onFinished: (RawVideoResult) -> Unit,
              configureRequest: (CaptureRequest.Builder) -> Unit = {}) {
        this.context = context.applicationContext
        this.characteristics = characteristics
        this.handler = handler
        stateCallback = onState
        finishedCallback = onFinished
        configureSettings = configureRequest
        requested = captureFps.coerceIn(1, 30)
        directory = "Download/PhotonCamera/RawVideo/${startedAt}/"
        handler.post {
            try {
                check(characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                    ?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW) == true) { "RAW capture unavailable" }
                val map = requireNotNull(characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP))
                val size = requireNotNull(map.getOutputSizes(ImageFormat.RAW_SENSOR)?.maxByOrNull { it.width.toLong() * it.height })
                frameWidth = size.width
                frameHeight = size.height
                val minimum = map.getOutputMinFrameDuration(ImageFormat.RAW_SENSOR, size)
                target = if (minimum > 0) minOf(requested, (1_000_000_000L / minimum).toInt().coerceAtLeast(1)) else requested
                reader = ImageReader.newInstance(size.width, size.height, ImageFormat.RAW_SENSOR, 8).also {
                    it.setOnImageAvailableListener({ source ->
                        try {
                            while (true) {
                                val image = source.acquireNextImage() ?: break
                                if (stopping) { image.close(); continue }
                                images.put(image.timestamp, image)?.close()
                                pair(image.timestamp)
                                while (images.size > 2) {
                                    val oldest = images.entries.first()
                                    images.remove(oldest.key)
                                    oldest.value.close()
                                    if (results.remove(oldest.key) != null) dropped.incrementAndGet()
                                }
                            }
                        } catch (e: Exception) { fail(e) }
                    }, handler)
                }
                @Suppress("DEPRECATION")
                device.createCaptureSession(listOf(previewSurface, reader!!.surface), object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(configured: CameraCaptureSession) {
                        session = configured
                        if (stopping) { configured.close(); return }
                        try {
                            requestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                                addTarget(previewSurface)
                                addTarget(reader!!.surface)
                                set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                            }
                            applySettingsAndRepeat()
                            publish()
                        } catch (e: Exception) { fail(e) }
                    }
                    override fun onConfigureFailed(s: CameraCaptureSession) { session = s; fail(IllegalStateException("RAW stream configuration failed")) }
                    override fun onClosed(s: CameraCaptureSession) {
                        if (!stopping) { error = error ?: "RAW capture session closed"; stopping = true; publish() }
                        finish()
                    }
                }, handler)
            } catch (e: Exception) { fail(e) }
        }
    }

    /** Updates controls without replacing the RAW reader, session, or frame timeline. */
    fun updateSettings(configure: (CaptureRequest.Builder) -> Unit) {
        if (!::handler.isInitialized) return
        val update = Runnable {
            if (!stopping && !finalizing) {
                configureSettings = configure
                try { applySettingsAndRepeat() } catch (e: Exception) { fail(e) }
            }
        }
        if (Looper.myLooper() == handler.looper) update.run() else handler.post(update)
    }

    private fun applySettingsAndRepeat() {
        val builder = requestBuilder ?: return
        val active = session ?: return
        configureSettings(builder)
        val ranges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES).orEmpty()
        val range = ranges.filter { it.contains(target) }.minByOrNull { it.upper - it.lower }
            ?: ranges.minByOrNull { kotlin.math.abs(it.upper - target) }
        if (range != null) builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, range)
        // Preserve long manual exposures; FPS is a ceiling, never a promise.
        val exposure = builder.get(CaptureRequest.SENSOR_EXPOSURE_TIME) ?: 0L
        builder.set(CaptureRequest.SENSOR_FRAME_DURATION, maxOf(1_000_000_000L / target, exposure))
        active.setRepeatingRequest(builder.build(), captureCallback, handler)
    }

    private fun pair(timestamp: Long) {
        val image = images[timestamp] ?: return
        val (index, result) = results.remove(timestamp) ?: return
        images.remove(timestamp)
        val previous = lastSelectedTimestamp
        if (previous != null && timestamp - previous < 980_000_000L / target) {
            rateSkipped.incrementAndGet()
            image.close()
            return
        }
        lastSelectedTimestamp = timestamp
        try {
            writer.execute {
                try {
                    if (metadata == null) {
                        metadataUri = createFile("frames.jsonl", "application/x-ndjson")
                        metadata = requireNotNull(context.contentResolver.openOutputStream(metadataUri!!)).bufferedWriter()
                    }
                    val uri = createFile("frame_%08d.dng".format(java.util.Locale.US, index), "image/x-adobe-dng")
                    try {
                        requireNotNull(context.contentResolver.openOutputStream(uri)).use { out ->
                            DngCreator(characteristics, result).use { it.writeImage(out, image) }
                        }
                        publishFile(uri)
                    } catch (e: Exception) { context.contentResolver.delete(uri, null, null); throw e }
                    metadata!!.apply {
                        write(JSONObject().put("frame", index).put("sensorTimestampNs", timestamp)
                            .put("exposureTimeNs", result.get(CaptureResult.SENSOR_EXPOSURE_TIME))
                            .put("sensitivityIso", result.get(CaptureResult.SENSOR_SENSITIVITY))
                            .put("frameDurationNs", result.get(CaptureResult.SENSOR_FRAME_DURATION)).toString())
                        newLine()
                    }
                    if (firstTimestamp == null) firstTimestamp = timestamp
                    lastTimestamp = timestamp
                    saved.incrementAndGet()
                    handler.post { publish() }
                } catch (e: Exception) { dropped.incrementAndGet(); handler.post { fail(e) } }
                finally { image.close() }
            }
        } catch (_: RejectedExecutionException) { image.close(); dropped.incrementAndGet(); publish() }
    }

    fun stop() {
        if (!::handler.isInitialized) return
        val action = Runnable {
            if (!stopping) {
                stopping = true
                publish()
                try { session?.stopRepeating(); session?.abortCaptures() } catch (_: Exception) { }
                val active = session
                if (active != null) active.close() else finish()
            }
        }
        if (Looper.myLooper() == handler.looper) action.run() else if (!handler.post(action)) action.run()
    }
    private fun fail(e: Exception) { if (error == null) error = e.message ?: e.javaClass.simpleName; stop() }
    private fun publish() = stateCallback(RawVideoState(!stopping, stopping, saved.get(), dropped.get(), requested, target, error))
    private fun finish() {
        if (finalizing) return
        finalizing = true
        images.values.forEach { it.close() }
        dropped.addAndGet(results.size)
        images.clear(); results.clear()
        writer.shutdown()
        Thread({
            try {
                while (!writer.awaitTermination(1, TimeUnit.SECONDS)) { /* bounded queue drains before reader closes */ }
                metadata?.close()
                metadataUri?.let { publishFile(it) }
                val elapsed = (lastTimestamp ?: 0L) - (firstTimestamp ?: 0L)
                val manifest = JSONObject().put("format", "DNG frame sequence").put("audio", false)
                    .put("sensorOrientationDegrees", characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION))
                    .put("frameWidth", frameWidth).put("frameHeight", frameHeight)
                    .put("requestedFps", requested).put("deviceLimitedTargetFps", target)
                    .put("savedFrames", saved.get()).put("droppedFrames", dropped.get())
                    .put("rateLimitedFrames", rateSkipped.get())
                    .put("actualSavedFps", if (elapsed > 0) (saved.get() - 1) * 1e9 / elapsed else 0.0)
                    .put("startedAtEpochMs", startedAt).put("firstSensorTimestampNs", firstTimestamp)
                    .put("lastSensorTimestampNs", lastTimestamp).put("error", error)
                    .put("timingsFile", if (metadataUri != null) "frames.jsonl" else JSONObject.NULL)
                val uri = createFile("manifest.json", "application/json")
                requireNotNull(context.contentResolver.openOutputStream(uri)).bufferedWriter().use { it.write(manifest.toString(2)) }
                publishFile(uri)
            } catch (e: Exception) { error = error ?: e.message }
            finally {
                val complete = Runnable {
                    reader?.close(); reader = null; session = null; requestBuilder = null
                    finishedCallback(RawVideoResult(directory, saved.get(), dropped.get(), error))
                }
                if (!handler.post(complete)) complete.run()
            }
        }, "RawVideoFinalize").start()
    }
    private fun createFile(name: String, mime: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, directory)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        return requireNotNull(context.contentResolver.insert(collection, values))
    }
    private fun publishFile(uri: Uri) { context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null) }
}
