package com.hinnka.mycamera.ui.camera

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hinnka.mycamera.R
import com.hinnka.mycamera.video.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VideoAudioCleanupControls(
    noiseEnabled: Boolean, available: Boolean, wind: VideoWindReduction,
    status: VideoNoiseReductionStatus, locked: Boolean,
    onNoiseChange: (Boolean) -> Unit, onWindChange: (VideoWindReduction) -> Unit,
) {
    HorizontalDivider()
    Row(verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.video_noise_reduction))
            Text(stringResource(if(available) R.string.video_noise_device_hint else R.string.video_noise_unavailable),
                style=MaterialTheme.typography.bodySmall)
        }
        Switch(checked=noiseEnabled,onCheckedChange=onNoiseChange,
            enabled=!locked && (available || noiseEnabled))
    }
    Text(stringResource(R.string.video_wind_reduction))
    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        VideoWindReduction.entries.forEach { level ->
            val label=when(level) {
                VideoWindReduction.OFF -> R.string.video_audio_filter_off
                VideoWindReduction.LOW -> R.string.video_audio_filter_low
                VideoWindReduction.MEDIUM -> R.string.video_audio_filter_medium
                VideoWindReduction.HIGH -> R.string.video_audio_filter_high
            }
            FilterChip(selected=wind==level,onClick={onWindChange(level)},enabled=!locked,
                label={Text(stringResource(label))})
        }
    }
    Text(stringResource(R.string.video_wind_hint),style=MaterialTheme.typography.bodySmall)
    if(locked) {
        Text(stringResource(when(status) {
            VideoNoiseReductionStatus.ACTIVE -> R.string.video_noise_status_active
            VideoNoiseReductionStatus.OFF -> R.string.video_noise_status_off
            VideoNoiseReductionStatus.UNAVAILABLE -> R.string.video_noise_status_unavailable
            VideoNoiseReductionStatus.FAILED -> R.string.video_noise_status_failed
            VideoNoiseReductionStatus.NO_AUDIO -> R.string.video_noise_status_no_audio
        }),style=MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.video_audio_locked),style=MaterialTheme.typography.bodySmall)
    }
}
