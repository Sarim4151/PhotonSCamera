package com.hinnka.mycamera.ui.camera

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hinnka.mycamera.R

/** Snapshotted by the recorder at start; changing controls cannot mutate an active clip. */
data class CinemaRecordingSettings(val playbackFps: Int = 0, val stopOnGap: Boolean = false, val monitorAudio: Boolean = false) {
    companion object {
        fun read(context: Context): CinemaRecordingSettings {
            val p = context.getSharedPreferences("cinema_recording", Context.MODE_PRIVATE)
            return CinemaRecordingSettings(p.getInt("playback_fps", 0).takeIf { it in listOf(0,24,25,30,50,60) } ?: 0,
                p.getBoolean("stop_gap", false), p.getBoolean("monitor_audio", false))
        }
    }
    fun save(context: Context) { context.getSharedPreferences("cinema_recording", Context.MODE_PRIVATE).edit()
        .putInt("playback_fps",playbackFps).putBoolean("stop_gap",stopOnGap).putBoolean("monitor_audio",monitorAudio).apply() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CinemaRecordingControls(recording: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(CinemaRecordingSettings.read(context)) }
    fun update(next: CinemaRecordingSettings) { settings=next; next.save(context) }
    Column(modifier, verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.cinema_playback_fps),style=MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf(0,24,25,30,50,60).forEach { fps ->
                FilterChip(selected=settings.playbackFps==fps,enabled=!recording,
                    onClick={update(settings.copy(playbackFps=fps))},
                    label={Text(if(fps==0) stringResource(R.string.cinema_capture_speed) else stringResource(R.string.cinema_fps_value,fps))})
            }
        }
        Text(stringResource(R.string.cinema_offspeed_hint),style=MaterialTheme.typography.bodySmall)
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text(stringResource(R.string.cinema_stop_gap),Modifier.weight(1f))
            Switch(settings.stopOnGap,{update(settings.copy(stopOnGap=it))},enabled=!recording)
        }
        Text(stringResource(R.string.cinema_gap_hint),style=MaterialTheme.typography.bodySmall)
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text(stringResource(R.string.cinema_audio_monitor),Modifier.weight(1f))
            Switch(settings.monitorAudio,{update(settings.copy(monitorAudio=it))},enabled=!recording)
        }
        Text(stringResource(R.string.cinema_audio_monitor_hint),style=MaterialTheme.typography.bodySmall)
    }
}
