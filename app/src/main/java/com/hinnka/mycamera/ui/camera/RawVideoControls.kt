package com.hinnka.mycamera.ui.camera

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hinnka.mycamera.R
import com.hinnka.mycamera.video.RawVideoState

@Composable
fun RawVideoControls(
    state: RawVideoState,
    selectedFps: Int,
    supported: Boolean,
    onFpsChanged: (Int) -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface.copy(alpha = .92f)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.raw_video_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.raw_video_description), style = MaterialTheme.typography.bodySmall)
            if (!supported) Text(stringResource(R.string.raw_video_unavailable))
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(1, 5, 10, 15, 24, 30).forEach { fps ->
                    FilterChip(selected = selectedFps == fps, onClick = { onFpsChanged(fps) },
                        enabled = supported && !state.recording && !state.stopping,
                        label = { Text(stringResource(R.string.raw_video_fps, fps)) })
                }
            }
            if (state.recording || state.stopping) {
                Text(stringResource(R.string.raw_video_counts, state.savedFrames, state.droppedFrames, state.targetFps))
            }
            state.error?.let { Text(stringResource(R.string.raw_video_error, it), color = MaterialTheme.colorScheme.error) }
            Button(onClick = onToggle, enabled = supported && !state.stopping) {
                Text(stringResource(if (state.stopping) R.string.raw_video_saving else if (state.recording) R.string.raw_video_stop else R.string.raw_video_start))
            }
        }
    }
}
