package com.hinnka.mycamera.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.hinnka.mycamera.R
import com.hinnka.mycamera.model.PhotographicStyle
import com.hinnka.mycamera.model.WhiteBalanceIntent

@Composable
fun PhotographicStyleDialog(value: PhotographicStyle, onChange: (PhotographicStyle) -> Unit, onDismiss: () -> Unit,
    whiteBalanceIntent: WhiteBalanceIntent = WhiteBalanceIntent.AUTO,
    onWhiteBalanceIntentChange: (WhiteBalanceIntent) -> Unit = {},
) {
    val style = value.normalized()
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.photographic_style)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.photographic_style_description))
            Text(stringResource(R.string.photographic_white_balance))
            listOf(WhiteBalanceIntent.AUTO to R.string.white_balance_auto,
                WhiteBalanceIntent.KEEP_ATMOSPHERE to R.string.white_balance_atmosphere,
                WhiteBalanceIntent.NEUTRAL_WHITES to R.string.white_balance_neutral).forEach { (intent, label) ->
                TextButton(onClick = { onWhiteBalanceIntentChange(intent) }) {
                    Text((if (intent == whiteBalanceIntent) "✓ " else "") + stringResource(label))
                }
            }
            Text(stringResource(R.string.photographic_white_balance_description))
            StyleControl(R.string.photographic_tone, style.tone, -1f..1f) { onChange(style.copy(tone = it)) }
            StyleControl(R.string.photographic_warmth, style.warmth, -1f..1f) { onChange(style.copy(warmth = it)) }
            StyleControl(R.string.photographic_colour, style.colour, -1f..1f) { onChange(style.copy(colour = it)) }
            StyleControl(R.string.photographic_detail, style.detail, -1f..1f) { onChange(style.copy(detail = it)) }
            StyleControl(R.string.photographic_fineness, style.fineness, 0.5f..3f) { onChange(style.copy(fineness = it)) }
            StyleControl(R.string.photographic_threshold, style.threshold, 0f..0.05f) { onChange(style.copy(threshold = it)) }
        } }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = { TextButton(onClick = { onChange(PhotographicStyle()) }) { Text(stringResource(R.string.photographic_reset)) } })
}

@Composable
private fun StyleControl(label: Int, value: Float, range: ClosedFloatingPointRange<Float>, change: (Float) -> Unit) {
    Text(stringResource(label) + " %.3f".format(java.util.Locale.ROOT, value))
    Slider(value = value, onValueChange = change, valueRange = range)
}
