package com.hinnka.mycamera.ui.camera

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hinnka.mycamera.R
import com.hinnka.mycamera.camera.AdvancedCameraMode
import com.hinnka.mycamera.camera.AdvancedCaptureSelection
import com.hinnka.mycamera.camera.AdvancedCaptureOptions
import com.hinnka.mycamera.camera.AdvancedCameraState

@Composable
fun AdvancedCaptureDialog(
    state: AdvancedCameraState, canStart: Boolean,
    onSelect: (AdvancedCameraMode, Int, Int, AdvancedCaptureOptions) -> Unit,
    onSave: () -> Unit = {}, onStop: () -> Unit = {}, onDismiss: () -> Unit,
    onRecover: () -> Unit = {},
    selection: AdvancedCaptureSelection? = null,
    onFullSourcePreCapture: (() -> Unit)? = null,
    onPhotoSphere: (() -> Unit)? = null,
) {
    var selected by rememberSaveable(selection) { mutableStateOf(selection?.mode ?: AdvancedCameraMode.PRE_CAPTURE) }
    var frames by rememberSaveable(selection) { mutableIntStateOf(selection?.frames ?: 6) }
    var gap by rememberSaveable(selection) { mutableIntStateOf(selection?.gapSeconds ?: 2) }
    var seconds by rememberSaveable(selection) { mutableIntStateOf(selection?.options?.bufferSeconds ?: 3) }
    var fps by rememberSaveable(selection) { mutableIntStateOf(selection?.options?.bufferFps ?: 4) }
    var edge by rememberSaveable(selection) { mutableIntStateOf(selection?.options?.bufferMaxEdge ?: 1280) }
    var memory by rememberSaveable(selection) { mutableIntStateOf(selection?.options?.bufferMemoryMb ?: 32) }
    var ev by rememberSaveable(selection) { mutableFloatStateOf(selection?.options?.exposureSpacingEv ?: 1.5f) }
    var fuse by rememberSaveable(selection) { mutableStateOf(selection?.options?.fuseExposureBracket ?: true) }
    var duration by rememberSaveable(selection) { mutableIntStateOf(selection?.options?.durationSeconds ?: 60) }
    var intensity by rememberSaveable(selection) { mutableFloatStateOf(selection?.options?.intensity ?: 1f) }
    var detailEdge by rememberSaveable(selection) { mutableIntStateOf(selection?.options?.detailInputMaxEdge ?: 0) }
    var astroHorizon by rememberSaveable(selection) { mutableStateOf(selection?.options?.astroHorizon) }
    val previewMode = selected in listOf(AdvancedCameraMode.PRE_CAPTURE, AdvancedCameraMode.PREVIEW_COMPOSITE, AdvancedCameraMode.PREVIEW_ND)
    val busy = state.running || state.saving
    val modes = listOf(
        AdvancedCameraMode.PRE_CAPTURE to R.string.advanced_pre_capture,
        AdvancedCameraMode.LIVE_COMPOSITE to R.string.advanced_live_composite,
        AdvancedCameraMode.LIGHT_GRAFFITI to R.string.advanced_light_graffiti,
        AdvancedCameraMode.LIVE_ND to R.string.advanced_live_nd,
        AdvancedCameraMode.NIGHT to R.string.advanced_night,
        AdvancedCameraMode.ASTRO to R.string.advanced_astro,
        AdvancedCameraMode.STAR_TRAILS to R.string.advanced_star_trails,
        AdvancedCameraMode.MOTION_HDR to R.string.advanced_hdr,
        AdvancedCameraMode.SOURCE_BURST to R.string.advanced_source_burst,
        AdvancedCameraMode.HIGH_DETAIL to R.string.advanced_high_detail,
        AdvancedCameraMode.SMART to R.string.advanced_smart_capture,
        AdvancedCameraMode.PREVIEW_COMPOSITE to R.string.advanced_preview_composite,
        AdvancedCameraMode.PREVIEW_ND to R.string.advanced_preview_nd)
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.advanced_capture_title)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.advanced_capture_description))
            if (!busy) modes.forEach { (mode, label) ->
                TextButton(onClick = { selected = mode; if (mode in listOf(AdvancedCameraMode.HIGH_DETAIL, AdvancedCameraMode.SOURCE_BURST)) { gap = 0; frames = frames.coerceAtLeast(6) } }) {
                    Text((if (selected == mode) "✓ " else "") + stringResource(label))
                }
            }
            Text(stringResource(if (previewMode) R.string.advanced_preview_limit else if (selected in listOf(AdvancedCameraMode.LIVE_COMPOSITE, AdvancedCameraMode.LIGHT_GRAFFITI, AdvancedCameraMode.LIVE_ND)) R.string.advanced_full_source_note else R.string.advanced_source_limit))
            if (!previewMode && !busy) {
                TextButton(onClick = { val values = if (selected == AdvancedCameraMode.HIGH_DETAIL) listOf(6, 9, 12, 15) else listOf(3, 6, 9, 12); frames = values[(values.indexOf(frames) + 1) % values.size] }) {
                    Text(stringResource(R.string.advanced_frame_count, if (selected == AdvancedCameraMode.HIGH_DETAIL) frames.coerceAtLeast(6) else frames))
                }
                if (selected != AdvancedCameraMode.HIGH_DETAIL) TextButton(onClick = { val values = listOf(0, 2, 5, 15, 30); gap = values[(values.indexOf(gap) + 1) % values.size] }) {
                    Text(stringResource(R.string.advanced_frame_gap, gap))
                }
            }
            if (state.mode != null) Text(stringResource(R.string.advanced_progress, state.frames, state.elapsedSeconds))
            if (state.status.isNotEmpty()) Text(state.status)
            state.preview?.let { Image(it.asImageBitmap(), contentDescription = stringResource(R.string.advanced_preview), modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)) }
            if (!busy) {
                if (selected == AdvancedCameraMode.PRE_CAPTURE) {
                    TextButton(onClick = { seconds = if (seconds >= 10) 1 else seconds + 1 }) { Text(stringResource(R.string.advanced_buffer_seconds, seconds)) }
                    TextButton(onClick = { fps = if (fps >= 8) 1 else fps + 1 }) { Text(stringResource(R.string.advanced_buffer_fps, fps)) }
                    TextButton(onClick = { edge = if (edge == 640) 1280 else if (edge == 1280) 1536 else 640 }) { Text(stringResource(R.string.advanced_buffer_edge, edge)) }
                    TextButton(onClick = { memory = if (memory >= 128) 16 else memory * 2 }) { Text(stringResource(R.string.advanced_buffer_memory, memory)) }
                }
                if (selected == AdvancedCameraMode.MOTION_HDR) {
                    TextButton(onClick = { ev = if (ev >= 3f) .5f else ev + .5f }) { Text(stringResource(R.string.advanced_ev_spacing, ev)) }
                    Row { Checkbox(fuse, { fuse = it }); Text(stringResource(R.string.advanced_fuse_bracket)) }
                }
                if (selected in listOf(AdvancedCameraMode.LIVE_COMPOSITE, AdvancedCameraMode.LIGHT_GRAFFITI, AdvancedCameraMode.LIVE_ND)) {
                    Text(stringResource(R.string.advanced_full_source_note))
                    TextButton(onClick = { duration = if (duration >= 600) 15 else duration * 2 }) { Text(stringResource(R.string.advanced_duration, duration)) }
                    if (selected == AdvancedCameraMode.LIVE_COMPOSITE || selected == AdvancedCameraMode.LIGHT_GRAFFITI) TextButton(onClick = { intensity = if (intensity >= 4f) .5f else intensity + .5f }) { Text(stringResource(R.string.advanced_intensity, intensity)) }
                }
                if (selected == AdvancedCameraMode.ASTRO) {
                    Row {
                        Checkbox(astroHorizon != null, { astroHorizon = if (it) .65f else null })
                        Text(stringResource(R.string.advanced_astro_foreground))
                    }
                    if (astroHorizon != null) {
                        Text(stringResource(R.string.advanced_astro_horizon_percent, (astroHorizon!! * 100 + .5f).toInt()))
                        Slider(astroHorizon!!, { astroHorizon = it }, valueRange = .15f.. .9f)
                        Text(stringResource(R.string.advanced_astro_horizon_help))
                    }
                }
                if (selected == AdvancedCameraMode.HIGH_DETAIL) {
                    Text(stringResource(R.string.advanced_high_detail_note))
                    TextButton(onClick = { val choices = listOf(1280, 2048, 3072, 0); detailEdge = choices[(choices.indexOf(detailEdge) + 1) % choices.size] }) {
                        Text(if (detailEdge == 0) stringResource(R.string.advanced_detail_full) else stringResource(R.string.advanced_detail_working_edge, detailEdge, detailEdge * 2))
                    }
                }
                if (selected == AdvancedCameraMode.SOURCE_BURST) Text(stringResource(R.string.advanced_source_burst_note))
                onFullSourcePreCapture?.let { open -> TextButton(onClick=open) { Text(stringResource(R.string.full_precapture_title)) } }
                onPhotoSphere?.let { open -> TextButton(onClick=open) { Text(stringResource(R.string.sphere_title)) } }
                TextButton(onClick = onRecover) { Text(stringResource(R.string.advanced_recover)) }
            }
        } },
        confirmButton = {
            TextButton(enabled = !busy && canStart, onClick = {
                onSelect(selected, frames, gap, AdvancedCaptureOptions(seconds, fps, edge, memory, ev, fuse, duration, intensity, detailEdge, astroHorizon))
                onDismiss()
            }) { Text(stringResource(R.string.advanced_use_mode)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.advanced_close)) } })
}
