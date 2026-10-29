package com.hinnka.mycamera.ui.gallery.detail

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import com.hinnka.mycamera.gallery.GalleryManager
import java.util.UUID
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hinnka.mycamera.R
import com.hinnka.mycamera.data.ContentRepository
import com.hinnka.mycamera.gallery.MediaMetadata
import com.hinnka.mycamera.model.ColorRecipeParams
import com.hinnka.mycamera.processor.DenoiseStrength
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Detail processing on an owned encoded look, decoded by region at native resolution. */
@Composable
fun DetailWorkspaceDialog(
    sourceId: String,
    load: suspend () -> DetailRegionSource?,
    onDismiss: () -> Unit,
    onSaved: suspend () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val processor = remember(context) { ContentRepository.getInstance(context).photoProcessor }
    var source by remember(sourceId) { mutableStateOf<DetailRegionSource?>(null) }
    var crop by remember { mutableStateOf<Bitmap?>(null) }
    var rendered by remember { mutableStateOf<Bitmap?>(null) }
    var luma by remember { mutableFloatStateOf(0f) }
    var chroma by remember { mutableFloatStateOf(0f) }
    var sharp by remember { mutableFloatStateOf(0f) }
    var x by remember { mutableFloatStateOf(.5f) }
    var y by remember { mutableFloatStateOf(.5f) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var before by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var processing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    fun metadata() = MediaMetadata(isImported = true,
        colorRecipeParams = ColorRecipeParams(sharpness = sharp),
        noiseReduction = luma, chromaNoiseReduction = chroma)
    LaunchedEffect(sourceId) {
        var acquired: DetailRegionSource? = null
        try {
            acquired = load()
            currentCoroutineContext().ensureActive()
            source = acquired
            acquired = null
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
        } catch (_: OutOfMemoryError) { failed = true
        } catch (_: Exception) { failed = true
        } finally { acquired?.close() }
        loading = false
        failed = source == null
    }
    LaunchedEffect(source, x, y, luma, chroma, sharp) {
        val original = source ?: return@LaunchedEffect
        processing = true
        delay(180)
        // A halo keeps denoise/sharpen border conditions outside the inspected crop.
        val side = minOf(768, original.width, original.height)
        val left = ((original.width - side) * x).toInt()
        val top = ((original.height - side) * y).toInt()
        val l = (left - 32).coerceAtLeast(0); val t = (top - 32).coerceAtLeast(0)
        val r = (left + side + 32).coerceAtMost(original.width)
        val b = (top + side + 32).coerceAtMost(original.height)
        var expanded: Bitmap? = null
        var output: Bitmap? = null
        var nextBefore: Bitmap? = null
        var nextAfter: Bitmap? = null
        try {
            withContext(Dispatchers.IO) {
                val input = original.decode(Rect(l, t, r, b))
                expanded = input
                val processed = processor.processBitmap(context, null, input, metadata(), applyFrameWatermark = false)
                output = processed
                nextBefore = original.decode(Rect(left, top, left + side, top + side))
                val region = Bitmap.createBitmap(processed, left-l, top-t, side, side)
                nextAfter = region.copy(Bitmap.Config.ARGB_8888, false)
                if (region !== processed) region.recycle()
            }
            currentCoroutineContext().ensureActive()
            crop?.recycle(); rendered?.recycle()
            crop = nextBefore; rendered = nextAfter
            nextBefore = null; nextAfter = null; failed = false
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
        } catch (_: OutOfMemoryError) { failed = true
        } catch (_: Exception) { failed = true
        } finally {
            if (output !== expanded) output?.recycle()
            expanded?.recycle(); nextBefore?.recycle(); nextAfter?.recycle(); processing = false
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            crop?.recycle(); rendered?.recycle()
            val owned = source
            // Independent cleanup survives dialog cancellation and never waits on a decoder on Main.
            CoroutineScope(Dispatchers.IO).launch { owned?.close() }
        }
    }
    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.detail_workspace_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.detail_workspace_explanation), style = MaterialTheme.typography.bodySmall)
                if (loading || processing) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (failed) Text(stringResource(R.string.detail_workspace_unavailable), color = MaterialTheme.colorScheme.error)
                val shown = if (before) crop else rendered
                Canvas(Modifier.fillMaxWidth().height(280.dp).pointerInput(source, saving) {
                    detectTransformGestures { _, move, scale, _ ->
                        if (saving) return@detectTransformGestures
                        zoom = (zoom * scale).coerceIn(1f, 4f)
                        source?.let { original ->
                            val side = minOf(768, original.width, original.height)
                            x = moveDetailCrop(x, move.x, size.width, original.width, side, zoom)
                            y = moveDetailCrop(y, move.y, size.height, original.height, side, zoom)
                        }
                    }
                }) {
                    shown?.let { bitmap ->
                        clipRect {
                            // Canvas coordinates are physical pixels: 1x is one source pixel per display pixel.
                            val image = bitmap.asImageBitmap()
                            val origin = Offset((size.width-bitmap.width*zoom)/2,
                                (size.height-bitmap.height*zoom)/2)
                            withTransform({ translate(origin.x.roundToInt().toFloat(), origin.y.roundToInt().toFloat()); scale(zoom, zoom, Offset.Zero) }) { drawImage(image) }
                        }
                    }
                }
                Text(stringResource(if (before) R.string.detail_workspace_before else R.string.detail_workspace_after))
                Row {
                    TextButton(onClick = { before = !before }) { Text(stringResource(R.string.detail_workspace_compare)) }
                    TextButton(onClick = { zoom = 1f }) { Text(stringResource(R.string.detail_workspace_native)) }
                }
                Text(stringResource(R.string.detail_workspace_drag_hint), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.detail_workspace_luma)); Slider(luma, { luma = it }, valueRange = DenoiseStrength.valueRange, enabled = !saving)
                Text(stringResource(R.string.detail_workspace_chroma)); Slider(chroma, { chroma = it }, valueRange = DenoiseStrength.valueRange, enabled = !saving)
                Text(stringResource(R.string.detail_workspace_sharp)); Slider(sharp, { sharp = it }, enabled = !saving)
                Row {
                    TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(android.R.string.cancel)) }
                    Button(enabled = source != null && !saving && !processing, onClick = {
                        saving = true
                        val settings = metadata()
                        scope.launch {
                            var encoded: File? = null
                            var saved = false
                            try {
                                withContext(Dispatchers.IO) {
                                    val input = requireNotNull(source)
                                    val sourceWidth = input.width
                                    val sourceHeight = input.height
                                    val target = File(context.cacheDir, "detail_export_${UUID.randomUUID()}.jpg")
                                    encoded = target
                                    input.export(context, target) { tile ->
                                        processor.processBitmap(context, null, tile, settings, applyFrameWatermark = false)
                                    }
                                    val id = UUID.randomUUID().toString()
                                    val neutral = MediaMetadata(width = sourceWidth, height = sourceHeight, isImported = true,
                                        customProperties = mapOf("detailSourceId" to sourceId, "sourceRecipeRendered" to "true",
                                            "detailLuma" to settings.noiseReduction.toString(),
                                            "detailChroma" to settings.chromaNoiseReduction.toString(),
                                            "detailSharpness" to settings.colorRecipeParams!!.sharpness.toString()))
                                    try {
                                        check(GalleryManager.preparePhoto(context, neutral, null, null, false, 1f,
                                            includeCropRegionInOutputSize = false, photoId = id) == id)
                                        GalleryManager.saveEncodedJpegPhoto(context, id, target, false, processor)
                                        val file = requireNotNull(GalleryManager.getOriginalImageFile(context, id))
                                        check(file.length() > 0)
                                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                        BitmapFactory.decodeFile(file.absolutePath, bounds)
                                        check(bounds.outWidth == sourceWidth && bounds.outHeight == sourceHeight)
                                        saved = true
                                    } catch (e: Throwable) { GalleryManager.deletePhotoOnly(context, id); throw e }
                                }
                            } catch (_: OutOfMemoryError) { failed = true
                            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
                            } catch (_: Exception) { failed = true
                            } finally { encoded?.delete(); saving = false }
                            if (saved) onSaved()
                        }
                    }) { Text(stringResource(R.string.detail_workspace_save)) }
                }
            }
        }
    }
}
