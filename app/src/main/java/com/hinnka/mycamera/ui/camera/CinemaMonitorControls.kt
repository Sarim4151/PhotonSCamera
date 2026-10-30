package com.hinnka.mycamera.ui.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.hinnka.mycamera.R
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CinemaMonitorControls(recording: Boolean, modifier: Modifier = Modifier, elapsedMs: Long? = null) {
    val preferences = CinemaMonitorPreferences.get(LocalContext.current)
    val settings by preferences.settings.collectAsState()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.cinema_monitor_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.cinema_display_only), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.cinema_frame_guide))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CinemaFrameGuide.entries.forEach { guide ->
                FilterChip(settings.guide == guide, { preferences.update(settings.copy(guide = guide)) },
                    label = { Text(stringResource(guide.label)) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(settings.safeAreaEnabled, { preferences.update(settings.copy(safeAreaEnabled = it)) })
            Text(stringResource(R.string.cinema_safe_area, settings.safeAreaPercent))
        }
        if (settings.safeAreaEnabled) {
            Slider(settings.safeAreaPercent.toFloat(), { preferences.update(settings.copy(safeAreaPercent = it.toInt())) },
                valueRange = 5f..100f, steps = 94)
        }
        Text(stringResource(R.string.cinema_anamorphic))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CinemaMonitorPreferences.anamorphicFactors.forEach { factor ->
                FilterChip(settings.anamorphicFactor == factor,
                    { preferences.update(settings.copy(anamorphicFactor = factor)) },
                    label = { Text(if (factor == 1f) stringResource(R.string.cinema_guide_off) else stringResource(R.string.cinema_factor, factor)) })
            }
        }
        Text(stringResource(R.string.cinema_clock_title))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CinemaClockMode.entries.forEach { mode ->
                FilterChip(settings.clock == mode, { preferences.update(settings.copy(clock = mode)) },
                    label = { Text(stringResource(mode.label)) })
            }
        }
        CinemaClockDisplay(recording, elapsedMs = elapsedMs)
    }
}

/** Can also be placed directly in the viewfinder. Elapsed display has second precision, not SMPTE timecode. */
@Composable
fun CinemaClockDisplay(recording: Boolean, modifier: Modifier = Modifier, elapsedMs: Long? = null) {
    val preferences = CinemaMonitorPreferences.get(LocalContext.current)
    val settings by preferences.settings.collectAsState()
    var wallTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            wallTime = System.currentTimeMillis()
            delay(200)
        }
    }
    if (settings.clock != CinemaClockMode.OFF) {
        val value = if (settings.clock == CinemaClockMode.TIME_OF_DAY) {
            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(wallTime))
        } else {
            val seconds = (elapsedMs ?: 0L).coerceAtLeast(0L) / 1000
            String.format(Locale.getDefault(), "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
        }
        Text(stringResource(R.string.cinema_clock_value, stringResource(settings.clock.label), value),
            modifier, fontFamily = FontFamily.Monospace)
    }
}

/** Attach to actual displayed preview bounds, outside its desqueeze transform. No pointer input. */
@Composable
fun CinemaGuideOverlay(modifier: Modifier = Modifier, previewAspectRatio: Float? = null) {
    val preferences = CinemaMonitorPreferences.get(LocalContext.current)
    val settings by preferences.settings.collectAsState()
    Canvas(modifier) {
        // With an explicit source aspect, match CameraPreviewGL's fit in both orientations.
        val imageAspect = previewAspectRatio?.takeIf { it.isFinite() && it > 0f }
            ?.times(settings.anamorphicFactor)
        val imageWidth = if (imageAspect != null) minOf(size.width, size.height * imageAspect) else size.width
        val imageHeight = if (imageAspect != null) imageWidth / imageAspect else size.height * settings.previewScaleY
        val imageLeft = (size.width - imageWidth) / 2f
        val imageTop = (size.height - imageHeight) / 2f
        val ratio = settings.guide.ratio
        var width = imageWidth
        var height = imageHeight
        if (ratio != null) {
            if (width / height > ratio) width = height * ratio else height = width / ratio
        }
        val left = (size.width - width) / 2f
        val top = imageTop + (imageHeight - height) / 2f
        if (ratio != null) {
            val shade = Color.Black.copy(alpha = 0.42f)
            drawRect(shade, Offset(imageLeft, imageTop), Size(imageWidth, top - imageTop))
            drawRect(shade, Offset(imageLeft, top + height), Size(imageWidth, imageTop + imageHeight - top - height))
            drawRect(shade, Offset(imageLeft, top), Size(left - imageLeft, height))
            drawRect(shade, Offset(left + width, top), Size(left - imageLeft, height))
            drawRect(Color.White.copy(alpha = .85f), Offset(left, top), Size(width, height), style = Stroke(1.dp.toPx()))
        }
        if (settings.safeAreaEnabled) {
            val fraction = settings.safeAreaPercent / 100f
            drawRect(Color.Yellow.copy(alpha = .85f),
                Offset(left + width * (1f - fraction) / 2f, top + height * (1f - fraction) / 2f),
                Size(width * fraction, height * fraction), style = Stroke(1.dp.toPx()))
        }
    }
}
