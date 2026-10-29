package com.hinnka.mycamera.ui.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hinnka.mycamera.R
import com.hinnka.mycamera.gallery.*
import com.hinnka.mycamera.precapture.FullSourceRing
import com.hinnka.mycamera.processor.SequenceMemoryBudget
import kotlinx.coroutines.*
import java.util.UUID
import kotlin.math.max

/** The real shutter starts admission, then stops it and drains the outstanding source save. */
@Composable
fun FullSourcePreCaptureOverlay(
    captureFrame: suspend (String) -> String,
    photoProcessor: PhotoProcessor,
    topControlsInset: Dp,
    bottomControlsInset: Dp,
    onCaptureActionChanged: ((() -> Unit)?) -> Unit,
    onSaved: (String) -> Unit,
    onSessionClosed: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ring = remember { FullSourceRing(context, UUID.randomUUID().toString()) }
    var running by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var stopping by remember { mutableStateOf(false) }
    var frames by remember { mutableStateOf(emptyList<FullSourceRing.Frame>()) }
    var selected by remember { mutableStateOf<String?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtimeNanos()) }
    var cadenceMs by remember { mutableLongStateOf(0L) }
    val capture by rememberUpdatedState(captureFrame)
    fun dismiss() { ring.stop(); onDismiss() }
    BackHandler { if (!saving) dismiss() }
    LaunchedEffect(Unit) { while (true) { now = SystemClock.elapsedRealtimeNanos(); delay(250) } }
    fun shutter() {
        if (saving) return
        if (started) { ring.stop(); stopping = true; return }
        started = true; running = true
        scope.launch {
            try {
                var previous = 0L
                while (ring.stopNanos == null) {
                    check(context.cacheDir.usableSpace > FullSourceRing.MAX_BYTES + 32L * 1024 * 1024) {
                        context.getString(R.string.group_disk_failed)
                    }
                    // Keep the accepted camera operation alive until its save callback settles.
                    withContext(NonCancellable) {
                        val id = capture(ring.sessionId)
                        val completed = SystemClock.elapsedRealtimeNanos()
                        ring.accept(id, completed)
                        if (previous != 0L) cadenceMs = (completed - previous) / 1_000_000
                        previous = completed
                    }
                    frames = ring.snapshot()
                    yield()
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { failure = e.localizedMessage; ring.stop(); stopping = true }
            catch (e: OutOfMemoryError) { failure = context.getString(R.string.group_memory_failed); ring.stop(); stopping = true }
            finally { running = false; frames = ring.snapshot(); selected = frames.lastOrNull()?.id }
        }
    }
    fun save() {
        val frame = frames.firstOrNull { it.id == selected } ?: return
        if (running || saving || ring.stopNanos == null) return
        saving = true; failure = null
        scope.launch {
            var decoded: Bitmap? = null
            var rendered: Bitmap? = null
            try {
                val outputId = withContext(Dispatchers.IO + NonCancellable) {
                    val metadata = GalleryManager.loadMetadata(context, frame.id) ?: error(context.getString(R.string.group_source_failed))
                    check(metadata.customProperties["addMeSessionId"] == ring.sessionId)
                    val source = GalleryManager.getOriginalImageFile(context, frame.id) ?: error(context.getString(R.string.group_source_failed))
                    decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(source)) { decoder, info, _ ->
                        val pixels = info.size.width.toLong() * info.size.height
                        check(SequenceMemoryBudget.snapshot(context).fits(pixels * 24L, pixels * 4L)) {
                            context.getString(R.string.group_memory_failed)
                        }
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.setTargetColorSpace(android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB))
                    }
                    rendered = photoProcessor.processBitmap(context, frame.id, decoded!!, metadata,
                        useComputationalAperture = false, applyFrameWatermark = false)
                    val bitmap = rendered!!
                    val id = UUID.randomUUID().toString()
                    val finalMetadata = MediaMetadata(width = bitmap.width, height = bitmap.height,
                        customProperties = mapOf("sequenceOperation" to "full_source_pre_capture", "sourceRecipeRendered" to "true",
                            "preCaptureAgeMs" to ((ring.stopNanos!! - frame.completedNanos) / 1_000_000).toString(),
                            "sequenceResolution" to "full_source"))
                    try {
                        check(GalleryManager.preparePhoto(context, finalMetadata, null, null, false, 1f,
                            includeCropRegionInOutputSize = false, photoId = id) == id)
                        GalleryManager.saveBitmapPhoto(context, id, bitmap, false, photoProcessor, 0f, 0f, 0f, photoQuality = 100)
                        val saved = GalleryManager.getOriginalImageFile(context, id)
                        check(saved != null && saved.length() > 0)
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(saved!!.absolutePath, bounds)
                        check(bounds.outWidth == bitmap.width && bounds.outHeight == bitmap.height)
                    } catch (e: Throwable) { GalleryManager.deletePhotoOnly(context, id); throw e }
                    runCatching { ring.clear() }
                    id
                }
                onSaved(outputId)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { failure = e.localizedMessage ?: context.getString(R.string.group_source_failed) }
            catch (e: OutOfMemoryError) { failure = context.getString(R.string.group_memory_failed) }
            finally {
                rendered?.let { if (it !== decoded && !it.isRecycled) it.recycle() }
                decoded?.let { if (!it.isRecycled) it.recycle() }
                saving = false
            }
        }
    }
    val action by rememberUpdatedState<() -> Unit>({ shutter() })
    DisposableEffect(Unit) {
        onCaptureActionChanged { action() }
        onDispose { ring.close(); onCaptureActionChanged(null); onSessionClosed(ring.sessionId) }
    }
    Column(Modifier.fillMaxWidth().padding(top = topControlsInset, bottom = bottomControlsInset)
        .background(Color.Black.copy(alpha = .68f)).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.full_precapture_title), color = Color.White)
        Text(stringResource(if (!started) R.string.full_precapture_start else if (!stopping)
            R.string.full_precapture_stop else if (running) R.string.full_precapture_drain else R.string.full_precapture_review), color = Color.White)
        Text(stringResource(R.string.full_precapture_stats, frames.size, cadenceMs,
            frames.lastOrNull()?.let { ((now - it.completedNanos) / 1_000_000).coerceAtLeast(0) } ?: 0L), color = Color.White)
        if (!running && started) {
            if (frames.isEmpty()) Text(stringResource(R.string.full_precapture_empty), color = Color.White)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(frames, key = { it.id }) { frame ->
                    val thumbnail by produceState<Bitmap?>(null, frame.id) {
                        value = withContext(Dispatchers.IO) {
                            GalleryManager.getOriginalImageFile(context, frame.id)?.let { file ->
                                ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                                    val scale = 192.0 / max(info.size.width, info.size.height)
                                    decoder.setTargetSize(max(1, (info.size.width * scale).toInt()), max(1, (info.size.height * scale).toInt()))
                                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                                }
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        TextButton(onClick = { selected = frame.id }, enabled = !saving) {
                            thumbnail?.let { Image(it.asImageBitmap(), null, Modifier.size(88.dp)) }
                        }
                        Text(stringResource(R.string.full_precapture_age,
                            ((ring.stopNanos!! - frame.completedNanos) / 1_000_000).coerceAtLeast(0)), color = if (selected == frame.id) Color.Yellow else Color.White)
                    }
                }
            }
            Button(onClick = ::save, enabled = selected != null && !saving) { Text(stringResource(R.string.composite_save)) }
        }
        if (running || saving) LinearProgressIndicator(Modifier.fillMaxWidth())
        failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        TextButton(onClick = ::dismiss, enabled = !saving) { Text(stringResource(android.R.string.cancel)) }
    }
}
