package com.hinnka.mycamera.ui.camera

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.hinnka.mycamera.R
import com.hinnka.mycamera.camera.FocusBracketState
import com.hinnka.mycamera.camera.FocusBracketOutcome

@Composable
fun FocusBracketDialog(
    state: FocusBracketState, minimumFocusDistance: Float, canStart: Boolean,
    onStart: (Float, Float, Int, Boolean) -> Unit, onStop: () -> Unit, onDismiss: () -> Unit,
) {
    val limit = minimumFocusDistance.takeIf { it.isFinite() && it > 0f } ?: 0f
    var near by remember(limit) { mutableFloatStateOf(limit) }
    var far by remember(limit) { mutableFloatStateOf(0f) }
    var count by remember { mutableIntStateOf(5) }
    var autoMerge by remember { mutableStateOf(true) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.focus_bracket_title)) },
        text = { Column {
            Text(stringResource(R.string.auto_focus_stack_description))
            Text(stringResource(R.string.focus_bracket_progress, state.accepted, state.total))
            val outcomeResource = when (state.outcome) {
                FocusBracketOutcome.IDLE -> R.string.focus_bracket_idle
                FocusBracketOutcome.RUNNING -> R.string.focus_bracket_running
                FocusBracketOutcome.MERGING -> R.string.auto_focus_stack_merging
                FocusBracketOutcome.COMPLETE -> if (state.mergedPhotoId != null) R.string.auto_focus_stack_saved else R.string.focus_bracket_complete
                FocusBracketOutcome.STOPPED -> R.string.focus_bracket_stopped
                FocusBracketOutcome.FAILED -> if (state.mergeFailed) R.string.auto_focus_stack_failed else R.string.focus_bracket_failed
            }
            Text(stringResource(outcomeResource))
            Row {
                Checkbox(checked = autoMerge, onCheckedChange = { autoMerge = it }, enabled = !state.isRunning)
                Text(stringResource(R.string.auto_focus_stack_enabled))
            }
            if (limit > 0f) {
                Text(stringResource(R.string.focus_bracket_near, near))
                Slider(value = near, onValueChange = { near = it }, valueRange = 0f..limit, enabled = !state.isRunning)
                Text(stringResource(R.string.focus_bracket_far, far))
                Slider(value = far, onValueChange = { far = it }, valueRange = 0f..limit, enabled = !state.isRunning)
            }
            TextButton(enabled = !state.isRunning, onClick = {
                val counts = listOf(3, 5, 9, 15)
                count = counts[(counts.indexOf(count) + 1) % counts.size]
            }) { Text(stringResource(R.string.focus_bracket_count, count)) }
            if (!canStart || near <= far) Text(stringResource(R.string.focus_bracket_not_ready))
        } },
        confirmButton = {
            TextButton(enabled = state.isRunning || (canStart && near > far), onClick = {
                if (state.isRunning) onStop() else onStart(near, far, count, autoMerge)
            }) { Text(stringResource(if (state.isRunning) R.string.interval_capture_stop else R.string.interval_capture_start)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.interval_capture_close)) } },
    )
}
