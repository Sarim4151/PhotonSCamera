package com.hinnka.mycamera.ui.camera

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hinnka.mycamera.R
import com.hinnka.mycamera.camera.IntervalCaptureState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntervalCaptureDialog(
    state: IntervalCaptureState,
    canStart: Boolean,
    onStart: (Int, Int) -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
    onFocusBracket: (() -> Unit)? = null,
    onAdvancedCapture: (() -> Unit)? = null,
    onGroupCapture: (() -> Unit)? = null,
    onPhotographicStyle: (() -> Unit)? = null,
) {
    var interval by rememberSaveable { mutableIntStateOf(5) }
    var slots by rememberSaveable { mutableIntStateOf(30) }
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
            .padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.capture_tools_title), style = MaterialTheme.typography.headlineSmall)
            if (!state.isRunning) {
                onGroupCapture?.let { CaptureToolCard(R.string.group_title, R.string.capture_tools_group, canStart, it) }
                onAdvancedCapture?.let { CaptureToolCard(R.string.advanced_capture_title, R.string.capture_tools_advanced, true, it) }
                onFocusBracket?.let { CaptureToolCard(R.string.focus_bracket_title, R.string.capture_tools_focus, true, it) }
                onPhotographicStyle?.let { CaptureToolCard(R.string.photographic_style, R.string.capture_tools_style, true, it) }
                HorizontalDivider()
            }
            Text(stringResource(R.string.interval_capture_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.interval_capture_description), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.plannedSlots > 0) Text(stringResource(R.string.interval_capture_progress,
                state.completedSlots, state.plannedSlots, state.acceptedCaptures, state.skippedSlots))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(2, 5, 10, 30, 60).forEach { value ->
                    FilterChip(selected = interval == value, enabled = !state.isRunning,
                        onClick = { interval = value }, label = { Text(stringResource(R.string.interval_capture_gap, value)) })
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 30, 60, 120).forEach { value ->
                    FilterChip(selected = slots == value, enabled = !state.isRunning,
                        onClick = { slots = value }, label = { Text(stringResource(R.string.interval_capture_slots, value)) })
                }
            }
            if (!canStart && !state.isRunning) Text(stringResource(R.string.interval_capture_not_ready),
                color = MaterialTheme.colorScheme.error)
            Button(modifier = Modifier.fillMaxWidth(), enabled = state.isRunning || canStart, onClick = {
                if (state.isRunning) onStop() else { onStart(interval, slots); onDismiss() }
            }) { Text(stringResource(if (state.isRunning) R.string.interval_capture_stop else R.string.interval_capture_start)) }
            TextButton(modifier = Modifier.fillMaxWidth(), onClick = onDismiss) { Text(stringResource(R.string.interval_capture_close)) }
        }
    }
}

@Composable
private fun CaptureToolCard(title: Int, description: Int, enabled: Boolean, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(description), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
