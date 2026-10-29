package com.hinnka.mycamera.ui.settings

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.ColorSpace
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hinnka.mycamera.R
import com.hinnka.mycamera.processor.PairedColorCalibration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun ColorCalibrationDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf<Bitmap?>(null) }
    var target by remember { mutableStateOf<Bitmap?>(null) }
    var sourcePoint by remember { mutableStateOf<Offset?>(null) }
    var targetPoint by remember { mutableStateOf<Offset?>(null) }
    var patches by remember { mutableStateOf(emptyList<PairedColorCalibration.Patch>()) }
    var result by remember { mutableStateOf<PairedColorCalibration.Result?>(null) }
    var pendingExport by remember { mutableStateOf<PairedColorCalibration.Result?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirmed by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    fun reset() { patches = emptyList(); result = null; preview = null; sourcePoint = null; targetPoint = null; confirmed = false; status = "" }
    fun failure(error: Exception) {
        val measuredError = error as? PairedColorCalibration.Invalid
        status = if (measuredError?.reason == "residual") context.getString(
            R.string.cc_error_residual,
            String.format(Locale.getDefault(), "%.4f", measuredError.rmse),
            String.format(Locale.getDefault(), "%.4f", measuredError.worst)
        ) else context.getString(when (measuredError?.reason) {
            "count" -> R.string.cc_error_count
            "range", "rank" -> R.string.cc_error_range
            "patch", "edge" -> R.string.cc_error_patch
            else -> R.string.cc_error_io
        })
    }
    fun load(uri: android.net.Uri, isSource: Boolean) {
        busy = true
        scope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                        val ratio = minOf(1.0, 1000.0 / maxOf(info.size.width, info.size.height))
                        decoder.setTargetSize(maxOf(1, (info.size.width * ratio).toInt()), maxOf(1, (info.size.height * ratio).toInt()))
                    }
                }
                reset(); if (isSource) source = bitmap else target = bitmap
            } catch (e: Exception) { failure(e) } finally { busy = false }
        }
    }
    val sourcePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { load(it, true) } }
    val targetPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { load(it, false) } }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val measured = pendingExport
        pendingExport = null
        if (uri == null || measured == null) busy = false
        if (uri != null && measured != null) {
            busy = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).bufferedWriter().use { measured.writeCube(it) }
                    }
                    status = context.getString(R.string.cc_exported)
                } catch (e: Exception) { failure(e) } finally { busy = false }
            }
        }
    }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(stringResource(R.string.cc_title)) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.cc_instructions))
                Text(stringResource(R.string.cc_domain), style = MaterialTheme.typography.bodySmall)
                TextButton(enabled = !busy, onClick = { sourcePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text(stringResource(R.string.cc_source)) }
                source?.let { PatchImage(it, sourcePoint, !busy) { sourcePoint = it } }
                TextButton(enabled = !busy, onClick = { targetPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text(stringResource(R.string.cc_target)) }
                target?.let { PatchImage(it, targetPoint, !busy) { targetPoint = it } }
                Row { Checkbox(checked = confirmed, enabled = !busy, onCheckedChange = { confirmed = it; result = null; preview = null }); Text(stringResource(R.string.cc_confirm)) }
                Text(stringResource(R.string.cc_pairs, patches.size))
                TextButton(enabled = !busy && sourcePoint != null && targetPoint != null, onClick = {
                    try {
                        val s = requireNotNull(sourcePoint); val t = requireNotNull(targetPoint)
                        val pair = PairedColorCalibration.Patch(PairedColorCalibration.sample(requireNotNull(source), s.x, s.y), PairedColorCalibration.sample(requireNotNull(target), t.x, t.y))
                        if (patches.any { old -> (0..2).sumOf { (old.source[it] - pair.source[it]) * (old.source[it] - pair.source[it]) } < .0001 || (0..2).sumOf { (old.target[it] - pair.target[it]) * (old.target[it] - pair.target[it]) } < .0001 }) throw PairedColorCalibration.Invalid("range")
                        patches = patches + pair; result = null; preview = null; sourcePoint = null; targetPoint = null; status = ""
                    } catch (e: Exception) { failure(e) }
                }) { Text(stringResource(R.string.cc_add)) }
                TextButton(enabled = !busy && patches.isNotEmpty(), onClick = { patches = patches.dropLast(1); result = null; preview = null; status = "" }) { Text(stringResource(R.string.cc_undo)) }
                Button(enabled = !busy && confirmed && patches.size >= 12, onClick = {
                    busy = true
                    val measuredPatches = patches.toList(); val bitmap = source
                    scope.launch {
                        try {
                            val fitted = withContext(Dispatchers.Default) { PairedColorCalibration.fit(measuredPatches) }
                            val rendered = withContext(Dispatchers.Default) { fitted.preview(requireNotNull(bitmap)) }
                            result = fitted; preview = rendered; status = ""
                        } catch (e: Exception) { failure(e) } finally { busy = false }
                    }
                }) { Text(stringResource(R.string.cc_fit)) }
                if (busy) CircularProgressIndicator()
                result?.let { Text(stringResource(R.string.cc_report, String.format(Locale.getDefault(), "%.4f", it.validationRmse), String.format(Locale.getDefault(), "%.4f", it.maxError))) }
                preview?.let { Text(stringResource(R.string.cc_preview)); Image(it.asImageBitmap(), stringResource(R.string.cc_preview), Modifier.fillMaxWidth().aspectRatio(it.width.toFloat() / it.height)) }
                if (status.isNotEmpty()) Text(status)
                Text(stringResource(R.string.cc_limits), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(enabled = result != null && !busy, onClick = {
            pendingExport = result
            busy = true
            try { exporter.launch("measured-srgb-calibration.cube") } catch (e: Exception) { pendingExport = null; busy = false; failure(e) }
        }) { Text(stringResource(R.string.cc_export)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.cc_close)) } })
}

@Composable
private fun PatchImage(bitmap: Bitmap, selected: Offset?, enabled: Boolean, onPick: (Offset) -> Unit) {
    Box(Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height).pointerInput(bitmap, enabled) {
        detectTapGestures { if (enabled) onPick(Offset((it.x / size.width).coerceIn(0f, 1f), (it.y / size.height).coerceIn(0f, 1f))) }
    }) {
        Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            for (i in 1..2) {
                drawLine(Color.White.copy(alpha = .4f), Offset(size.width * i / 3, 0f), Offset(size.width * i / 3, size.height))
                drawLine(Color.White.copy(alpha = .4f), Offset(0f, size.height * i / 3), Offset(size.width, size.height * i / 3))
            }
            selected?.let { drawCircle(Color.Black, 9.dp.toPx(), Offset(it.x * size.width, it.y * size.height)); drawCircle(Color.Yellow, 6.dp.toPx(), Offset(it.x * size.width, it.y * size.height)) }
        }
    }
}
