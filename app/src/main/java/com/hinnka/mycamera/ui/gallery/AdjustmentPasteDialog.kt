package com.hinnka.mycamera.ui.gallery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.hinnka.mycamera.R
import com.hinnka.mycamera.gallery.AdjustmentPasteSelection

/** Capture a choice for this operation before executing any editor or storage mutation. */
@Composable
fun rememberAdjustmentPasteLauncher(): ((AdjustmentPasteSelection) -> Unit) -> Unit {
    var pending by remember { mutableStateOf<((AdjustmentPasteSelection) -> Unit)?>(null) }
    var selection by remember { mutableStateOf(AdjustmentPasteSelection()) }
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.paste_adjustments_title)) },
            text = {
                Column {
                    AdjustmentPasteOption(R.string.paste_adjustments_color, selection.color) { selection = selection.copy(color = it) }
                    AdjustmentPasteOption(R.string.paste_adjustments_detail, selection.detail) { selection = selection.copy(detail = it) }
                    AdjustmentPasteOption(R.string.paste_adjustments_frame, selection.frame) { selection = selection.copy(frame = it) }
                    AdjustmentPasteOption(R.string.paste_adjustments_geometry, selection.geometry) { selection = selection.copy(geometry = it) }
                    AdjustmentPasteOption(R.string.paste_adjustments_portrait, selection.portrait) { selection = selection.copy(portrait = it) }
                }
            },
            confirmButton = {
                TextButton(enabled = !selection.isEmpty, onClick = {
                    val action = pending
                    pending = null
                    action?.invoke(selection)
                }) { Text(stringResource(R.string.paste_settings)) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(android.R.string.cancel)) } }
        )
    }
    return { action -> selection = AdjustmentPasteSelection(); pending = action }
}

@Composable
private fun AdjustmentPasteOption(label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(stringResource(label))
    }
}
