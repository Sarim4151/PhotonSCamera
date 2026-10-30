package com.hinnka.mycamera.ui.camera

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hinnka.mycamera.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Distances are Camera2 diopters. The callback must enable manual AF before applying them. */
@Composable
fun CinemaFocusControls(
    cameraId: String,
    currentFocusDistance: Float,
    maxFocusDistance: Float,
    onFocusDistanceChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val context = LocalContext.current.applicationContext
    val preferences = remember(context) { context.getSharedPreferences("cinema_focus", Context.MODE_PRIVATE) }
    val limit = maxFocusDistance.takeIf { it.isFinite() && it > 0f } ?: 0f
    val current = currentFocusDistance.takeIf { it.isFinite() }?.coerceIn(0f, limit) ?: 0f
    val latestCallback by rememberUpdatedState(onFocusDistanceChange)
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var rackJob by remember { mutableStateOf<Job?>(null) }
    var running by remember { mutableStateOf(false) }
    var presets by remember(cameraId, limit) {
        mutableStateOf(List(3) { index ->
            val key = "$cameraId.position.$index"
            if (preferences.contains(key)) preferences.getFloat(key, 0f)
                .takeIf { it.isFinite() && it in 0f..limit } else null
        })
    }
    var duration by remember(cameraId) {
        mutableFloatStateOf(preferences.getFloat("$cameraId.duration", 2f)
            .takeIf { it.isFinite() }?.coerceIn(0.5f, 10f) ?: 2f)
    }
    // Disposal also runs on lens changes and lifecycle-driven removal from composition.
    DisposableEffect(cameraId, limit, enabled) {
        rackJob?.cancel()
        rackJob = null
        running = false
        onDispose { rackJob?.cancel() }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) rackJob?.cancel()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val available = enabled && limit > 0f
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.cinema_focus_title))
        if (limit <= 0f) Text(stringResource(R.string.cinema_focus_unavailable))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            presets.forEachIndexed { index, target ->
                Column {
                    OutlinedButton(enabled = available && target != null && !running, onClick = {
                        val destination = target ?: return@OutlinedButton
                        val start = current
                        rackJob?.cancel()
                        rackJob = scope.launch {
                            running = true
                            try {
                                val startTime = withFrameNanos { it }
                                val durationNanos = duration * 1_000_000_000.0
                                do {
                                    val progress = withFrameNanos { time ->
                                        ((time - startTime) / durationNanos).toFloat().coerceIn(0f, 1f)
                                    }
                                    // Smoothstep has zero velocity at both endpoints.
                                    val eased = progress * progress * (3f - 2f * progress)
                                    latestCallback((start + (destination - start) * eased).coerceIn(0f, limit))
                                } while (progress < 1f)
                            } finally { running = false }
                        }
                    }) { Text(stringResource(R.string.cinema_focus_recall, index + 1)) }
                    TextButton(enabled = available && !running, onClick = {
                        preferences.edit().putFloat("$cameraId.position.$index", current).apply()
                        presets = presets.toMutableList().also { it[index] = current }
                    }) { Text(stringResource(R.string.cinema_focus_save, index + 1)) }
                }
            }
        }
        Text(stringResource(R.string.cinema_focus_duration, duration))
        Slider(value = duration, onValueChange = { duration = (it * 2).roundToInt() / 2f },
            onValueChangeFinished = {
                preferences.edit().putFloat("$cameraId.duration", duration).apply()
            }, valueRange = 0.5f..10f, steps = 18, enabled = available && !running)
        if (running) {
            TextButton(onClick = { rackJob?.cancel() }) { Text(stringResource(R.string.cinema_focus_stop)) }
        }
    }
}
