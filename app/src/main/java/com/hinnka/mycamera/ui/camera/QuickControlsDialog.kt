package com.hinnka.mycamera.ui.camera

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hinnka.mycamera.R

@Composable
fun QuickControlsDialog(
    slots: List<ProQuickControl>,
    onSlotsChange: (List<ProQuickControl>) -> Unit,
    showInViewfinder: Boolean,
    onShowInViewfinderChange: (Boolean) -> Unit,
    presets: Map<String, List<ProQuickControl>>,
    onSavePreset: (String) -> Unit,
    onLoadPreset: (String) -> Unit,
    onDeletePreset: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    fun move(index: Int, delta: Int) {
        val result = slots.toMutableList()
        val item = result.removeAt(index)
        result.add(index + delta, item)
        onSlotsChange(result)
    }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quick_controls_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.quick_controls_hint))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = showInViewfinder, onCheckedChange = onShowInViewfinderChange)
                    Text(stringResource(R.string.quick_show_in_viewfinder), Modifier.weight(1f))
                }
                Text(stringResource(R.string.workspace_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.workspace_layout_only), style = MaterialTheme.typography.bodySmall)
                CameraWorkspace.entries.forEach { workspace ->
                    TextButton(onClick = { onSlotsChange(workspace.slots) }) {
                        Text(stringResource(workspace.label))
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                val ordered = slots + ProQuickControl.entries.filterNot { it in slots }
                ordered.forEach { control ->
                    val index = slots.indexOf(control)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = index >= 0,
                            enabled = index >= 0 || slots.size < ProQuickControl.MAX_SLOTS,
                            onCheckedChange = { checked -> onSlotsChange(if (checked) slots + control else slots - control) })
                        Text(stringResource(control.label), Modifier.weight(1f))
                        if (index >= 0) {
                            IconButton(onClick = { move(index, -1) }, enabled = index > 0) {
                                Icon(Icons.Default.KeyboardArrowUp, stringResource(R.string.quick_move_earlier))
                            }
                            IconButton(onClick = { move(index, 1) }, enabled = index < slots.lastIndex) {
                                Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.quick_move_later))
                            }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                OutlinedTextField(value = name, onValueChange = { name = it.take(40) },
                    label = { Text(stringResource(R.string.quick_preset_name)) }, singleLine = true)
                TextButton(onClick = { onSavePreset(name.trim()); name = "" }, enabled = name.isNotBlank()) {
                    Text(stringResource(R.string.quick_save_preset))
                }
                presets.keys.forEach { preset ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(preset, Modifier.weight(1f))
                        TextButton(onClick = { onLoadPreset(preset) }) { Text(stringResource(R.string.quick_load_preset)) }
                        TextButton(onClick = { onDeletePreset(preset) }) { Text(stringResource(R.string.quick_delete_preset)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.quick_done)) } })
}
