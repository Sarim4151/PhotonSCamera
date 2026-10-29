package com.hinnka.mycamera.ui.camera

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hinnka.mycamera.R
import com.hinnka.mycamera.gallery.*
import com.hinnka.mycamera.processor.*
import com.hinnka.mycamera.utils.PLog
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max

/** Lives in the viewfinder, in the same window as the real camera shutter. */
@Composable
fun GroupCaptureOverlay(
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
    val lock = remember { Mutex() }
    val sessionId = remember { java.util.UUID.randomUUID().toString() }
    val sourceIds = remember { mutableSetOf<String>() }
    val owned = remember { mutableListOf<Bitmap>() }
    var firstId by remember { mutableStateOf<String?>(null) }
    var secondId by remember { mutableStateOf<String?>(null) }
    var first by remember { mutableStateOf<Bitmap?>(null) }
    var second by remember { mutableStateOf<Bitmap?>(null) }
    var mask by remember { mutableStateOf<PersonCompositeTools.Mask?>(null) }
    var alignment by remember { mutableStateOf<PersonCompositeTools.Alignment?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var roi by remember { mutableStateOf(CompositeRegion(width = 1f, height = 1f, feather = .03f)) }
    var renderedRoi by remember { mutableStateOf<CompositeRegion?>(null) }
    var captureBusy by remember { mutableStateOf(false) }
    var rendering by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val busy = captureBusy || rendering || saving
    var progress by remember { mutableIntStateOf(R.string.group_loading) }
    var reviewed by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var fullResolution by remember { mutableStateOf(true) }
    var refine by remember { mutableStateOf(false) }
    var inspectPhoto by remember { mutableStateOf(false) }

    suspend fun discardSources(ids: Collection<String>) = withContext(Dispatchers.IO + NonCancellable) {
        for (id in ids.toList()) {
            // Never delete an arbitrary gallery image, even if a callback returns the wrong ID.
            val metadata = GalleryManager.loadMetadata(context, id)
            if (metadata?.customProperties?.get("addMeSessionId") == sessionId) {
                GalleryManager.deletePhotoOnly(context, id)
                sourceIds.remove(id)
            }
        }
    }

    fun report(e: Exception) {
        PLog.e("AddMe", "Stage failed: first=$firstId second=$secondId progress=$progress", e)
        failure = e.localizedMessage ?: context.getString(R.string.group_source_failed)
    }
    suspend fun load(id: String, maxEdge: Int = 2048): Bitmap {
        var decoded: Bitmap? = null
        var rendered: Bitmap? = null
        try {
            // Native/GPU readers cannot be cancelled midway through bitmap access.
            withContext(Dispatchers.IO + NonCancellable) {
                val metadata = GalleryManager.loadMetadata(context, id)
                    ?: error(context.getString(R.string.group_source_failed))
                val file = GalleryManager.getOriginalImageFile(context, id)
                    ?: error(context.getString(R.string.group_source_failed))
                decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                    val edge = max(info.size.width, info.size.height)
                    val scale = if (maxEdge == 0) 1.0 else minOf(1.0, maxEdge.toDouble() / edge)
                    val width = max(1, (info.size.width * scale).toInt())
                    val height = max(1, (info.size.height * scale).toInt())
                    if (maxEdge == 0) check(context.cacheDir.usableSpace > width.toLong() * height * 4L + 32L * 1024 * 1024) {
                        context.getString(R.string.group_disk_failed)
                    }
                    if (maxEdge == 0) {
                        val runtime = Runtime.getRuntime()
                        val available = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())
                        PLog.d("AddMe", "Full source ${width}x$height; available heap ${available / (1024 * 1024)} MiB")
                    }
                    try { PersonCompositeTools.preflight(width, height, includeRecipeProcessing = true) }
                    catch (e: IllegalArgumentException) {
                        throw IllegalStateException(context.getString(R.string.group_memory_failed), e)
                    }
                    if (scale < 1.0) decoder.setTargetSize(width, height)
                    decoder.setTargetColorSpace(android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB))
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = false
                }
                val source = decoded ?: error(context.getString(R.string.group_source_failed))
                rendered = photoProcessor.processBitmap(context, id, source, metadata,
                    useComputationalAperture = false, applyFrameWatermark = false)
                if (rendered !== decoded) { decoded?.let { if (!it.isRecycled) it.recycle() }; decoded = null }
            }
            currentCoroutineContext().ensureActive()
            return rendered ?: error(context.getString(R.string.group_source_failed))
        } catch (e: Throwable) {
            if (rendered !== decoded) rendered?.let { if (!it.isRecycled) it.recycle() }
            decoded?.let { if (!it.isRecycled) it.recycle() }
            throw e
        }
    }

    fun capture() {
        // Set synchronously: fast consecutive taps cannot enqueue two capture coroutines.
        if (captureBusy || rendering || saving || second != null) return
        captureBusy = true
        failure = null
        scope.launch {
            try { lock.withLock {
                if (first == null) {
                    if (firstId == null) {
                        progress = R.string.group_capturing_first
                        firstId = captureFrame(sessionId).also { sourceIds.add(it) }
                        PLog.d("AddMe", "First source saved: $firstId")
                    }
                    progress = R.string.group_loading
                    first = load(firstId!!).also { owned.add(it) }
                } else {
                    if (secondId == null) {
                        progress = R.string.group_capturing_second
                        secondId = captureFrame(sessionId).also { sourceIds.add(it) }
                        PLog.d("AddMe", "Second source saved: $secondId")
                    }
                    progress = R.string.group_loading
                    val bitmap = load(secondId!!).also { owned.add(it) }
                    try {
                        val a = first!!
                        progress = R.string.group_segmenting
                        val ma = PersonCompositeTools.segment(a)
                        val mb = PersonCompositeTools.segment(bitmap)
                        progress = R.string.group_aligning
                        val fit = PersonCompositeTools.align(a, bitmap, ma, mb)
                        require(fit.acceptable) { context.getString(R.string.group_scene_changed) }
                        val added = PersonCompositeTools.addedPersonMask(ma, mb, fit)
                        require(added.values.count { it > .6f } > added.values.size / 500) {
                            context.getString(R.string.group_no_additional_person)
                        }
                        mask = added
                        alignment = fit; second = bitmap
                    } finally {
                        if (second !== bitmap) { owned.remove(bitmap); bitmap.recycle() }
                    }
                }
            } } catch (e: CancellationException) { throw e }
            catch (e: Exception) { report(e) }
            catch (e: OutOfMemoryError) {
                PLog.e("AddMe", "Insufficient memory", e)
                failure = context.getString(R.string.group_memory_failed)
            }
            finally { captureBusy = false }
        }
    }
    BackHandler { if (!busy) onDismiss() }
    LaunchedEffect(Unit) {
        try { awaitCancellation() }
        finally { withContext(NonCancellable) { lock.withLock {
            owned.forEach { if (!it.isRecycled) it.recycle() }; owned.clear()
            discardSources(sourceIds.toList())
        } } }
    }
    LaunchedEffect(second, roi) {
        val a = first; val b = second; val m = mask; val fit = alignment
        if (a != null && b != null && m != null && fit != null) {
            reviewed = false; rendering = true; failure = null
            try {
                delay(150)
                lock.withLock {
                    val output = PersonCompositeTools.render(a, b, m, fit, roi)
                    owned.add(output)
                    preview = output; renderedRoi = roi
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { report(e) }
            catch (e: OutOfMemoryError) {
                PLog.e("AddMe", "Insufficient memory", e)
                failure = context.getString(R.string.group_memory_failed)
            }
            finally { rendering = false }
        }
    }

    LaunchedEffect(first, second, preview) {
        // Release obsolete previews and retaken donors only after a replacement frame is drawn.
        withFrameNanos { }; withFrameNanos { }
        lock.withLock {
            val unused = owned.filter { it !== first && it !== second && it !== preview }
            unused.forEach { owned.remove(it); if (!it.isRecycled) it.recycle() }
        }
    }

    fun save() {
        if (captureBusy || rendering || saving || !reviewed || preview == null || renderedRoi != roi || secondId == null) return
        saving = true; progress = if (fullResolution) R.string.group_full_first else R.string.group_saving; failure = null
        scope.launch {
            var fullOutput: Bitmap? = null
            try { lock.withLock {
                val bitmap = if (fullResolution) {
                    FullResolutionPersonComposite.render(context.cacheDir, firstId!!, secondId!!,
                        loadSource = { load(it, maxEdge = 0) }, mask = mask!!, alignment = alignment!!, region = roi,
                        onStage = { stage ->
                            progress = when (stage) {
                                FullResolutionPersonComposite.Stage.FIRST_SOURCE -> R.string.group_full_first
                                FullResolutionPersonComposite.Stage.SECOND_SOURCE -> R.string.group_full_second
                                FullResolutionPersonComposite.Stage.BLEND -> R.string.group_full_blend
                            }
                            PLog.d("AddMe", "Full source stage: $stage")
                        }).also { fullOutput = it }
                } else preview!!
                progress = R.string.group_saving
                val metadata = MediaMetadata(width = bitmap.width, height = bitmap.height,
                    customProperties = mapOf("sequenceOperation" to "guided_person_composite",
                        "sequenceSources" to "$firstId,$secondId",
                        "sequenceResolution" to if (fullResolution) "full_source" else "max_edge_2048",
                        "sourceRecipeRendered" to "true", "sourceRawPreserved" to "false",
                        "sequenceVariant" to "${alignment};$roi"))
                // Once pixels exist, publish exactly one verified final image atomically.
                // Prepare must share cancellation protection with save or its placeholder can leak.
                val id = withContext(Dispatchers.IO + NonCancellable) {
                    val outputId = java.util.UUID.randomUUID().toString()
                    try {
                        check(GalleryManager.preparePhoto(context, metadata, null, null, false, 1f,
                            includeCropRegionInOutputSize = false, photoId = outputId) == outputId) {
                            context.getString(R.string.group_source_failed)
                        }
                        GalleryManager.saveBitmapPhoto(context, outputId, bitmap, false, photoProcessor,
                            0f, 0f, 0f, photoQuality = 100)
                        val saved = GalleryManager.getOriginalImageFile(context, outputId)
                        check(saved?.length()?.let { it > 0 } == true)
                        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        android.graphics.BitmapFactory.decodeFile(saved!!.absolutePath, bounds)
                        check(bounds.outWidth == bitmap.width && bounds.outHeight == bitmap.height)
                    } catch (failure: Throwable) {
                        GalleryManager.deletePhotoOnly(context, outputId)
                        throw failure
                    }
                    // A cleanup failure must never roll back a verified final image after
                    // some private sources have already been removed. Session teardown retries.
                    try { discardSources(sourceIds.toList()) }
                    catch (cleanup: Exception) { PLog.w("AddMe", "Final image saved; private-source cleanup will retry", cleanup) }
                    outputId
                }
                onSaved(id)
            } } catch (e: CancellationException) { throw e }
            catch (e: Exception) { report(e) }
            catch (e: OutOfMemoryError) {
                PLog.e("AddMe", "Insufficient memory", e)
                failure = context.getString(R.string.group_memory_failed)
            }
            finally { fullOutput?.let { if (!it.isRecycled) it.recycle() }; saving = false }
        }
    }

    val currentShutterAction by rememberUpdatedState<() -> Unit>({
        if (second != null) save() else capture()
    })
    DisposableEffect(Unit) {
        onCaptureActionChanged { currentShutterAction() }
        onDispose { onCaptureActionChanged(null); onSessionClosed(sessionId) }
    }

    if (inspectPhoto) preview?.let { bitmap ->
        Dialog(onDismissRequest = { inspectPhoto = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
                Image(bitmap.asImageBitmap(), stringResource(R.string.composite_preview),
                    Modifier.weight(1f).fillMaxWidth(), contentScale = ContentScale.Fit)
                TextButton(onClick = { inspectPhoto = false }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.group_back_to_review))
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        val image = preview ?: first
        image?.let {
            Image(it.asImageBitmap(), stringResource(if (preview != null) R.string.composite_preview else R.string.group_alignment_ghost),
                Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
                alpha = if (preview != null) 1f else .32f)
        }
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = topControlsInset + 12.dp)) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = .82f))) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.group_title), color = Color.White, style = MaterialTheme.typography.titleMedium)
                        TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
                    }
                    Text(stringResource(when { first == null -> R.string.group_step_first; second == null -> R.string.group_step_second; else -> R.string.group_step_review }),
                        color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    Text(stringResource(if (first == null) R.string.group_first else if (second == null) R.string.group_swap else R.string.group_review),
                        color = Color.White, style = MaterialTheme.typography.bodySmall)
                    if (busy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(stringResource(if (saving) progress else if (rendering) R.string.group_rendering else progress), color = Color.White)
                    }
                    failure?.let { Text(stringResource(R.string.group_failed, it), color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        Card(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = bottomControlsInset + 12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = .86f))) {
            Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if ((first == null || second != null) && !busy) {
                    Text(stringResource(R.string.group_output_quality), color = Color.White, style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !fullResolution, onClick = { fullResolution = false; failure = null }, label = { Text(stringResource(R.string.group_quality_standard)) })
                        FilterChip(selected = fullResolution, onClick = { fullResolution = true; failure = null }, label = { Text(stringResource(R.string.group_quality_full)) })
                    }
                    Text(stringResource(R.string.group_quality_note), color = Color.White, style = MaterialTheme.typography.bodySmall)
                }
                if (second == null) {
                    Text(stringResource(if (failure == null) R.string.group_use_shutter else R.string.group_retry_hint), color = Color.White)
                    if (failure != null) Button(enabled = !busy, onClick = ::capture) { Text(stringResource(R.string.group_retry)) }
                    if (secondId != null) TextButton(enabled = !busy, onClick = {
                        val discarded = secondId
                        secondId = null; failure = null
                        scope.launch { lock.withLock { discarded?.let { discardSources(listOf(it)) } } }
                    }) {
                        Text(stringResource(R.string.group_retake_second))
                    }
                } else {
                    OutlinedButton(enabled = !busy && preview != null && renderedRoi == roi,
                        onClick = { inspectPhoto = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.group_inspect_photo))
                    }
                    TextButton(enabled = !busy, onClick = { refine = !refine }) { Text(stringResource(R.string.group_refine)) }
                    if (refine && !captureBusy && !saving) {
                        fun change(value: CompositeRegion) { reviewed = false; roi = value }
                        GroupSlider(R.string.composite_x, roi.x, 0f..1f) { change(roi.copy(x = it)) }
                        GroupSlider(R.string.composite_y, roi.y, 0f..1f) { change(roi.copy(y = it)) }
                        GroupSlider(R.string.composite_width, roi.width, .05f..1f) { change(roi.copy(width = it)) }
                        GroupSlider(R.string.composite_height, roi.height, .05f..1f) { change(roi.copy(height = it)) }
                        GroupSlider(R.string.composite_feather, roi.feather, .005f.. .2f) { change(roi.copy(feather = it)) }
                    }
                    Text(stringResource(R.string.group_review_shutter), color = Color.White, style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(reviewed, { reviewed = it }, enabled = !busy && preview != null && renderedRoi == roi)
                        Text(stringResource(R.string.group_review_confirm), color = Color.White, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(modifier = Modifier.fillMaxWidth(), enabled = reviewed && !busy && preview != null && renderedRoi == roi, onClick = ::save) {
                        Text(stringResource(R.string.composite_save))
                    }
                    TextButton(enabled = !busy, onClick = {
                        val discarded = secondId
                        // No image is recycled until this composition has presented its replacement.
                        preview = null; second = null; secondId = null; mask = null; alignment = null
                        renderedRoi = null; reviewed = false; failure = null
                        scope.launch { lock.withLock { discarded?.let { discardSources(listOf(it)) } } }
                    }) { Text(stringResource(R.string.group_retake_second)) }
                }
            }
        }
    }
}

@Composable
private fun GroupSlider(label: Int, value: Float, range: ClosedFloatingPointRange<Float>, change: (Float) -> Unit) {
    Text(stringResource(label), color = Color.White)
    Slider(value, change, valueRange = range)
}
