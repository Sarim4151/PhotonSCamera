package com.hinnka.mycamera.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hinnka.mycamera.R
import com.hinnka.mycamera.model.ColorRecipeParams
import java.util.Locale
import kotlin.math.*

@Composable
internal fun LggoPanel(params: ColorRecipeParams, onChange: (ColorRecipeParams) -> Unit) {
    var selected by remember { mutableIntStateOf(0) }
    val labels = listOf(R.string.photo_grading_lift, R.string.photo_grading_gamma, R.string.photo_grading_gain, R.string.photo_grading_offset)
    var pendingWheel by remember(selected) { mutableStateOf<List<Float>?>(null) }
    val values = pendingWheel ?: when(selected) {
        0 -> listOf(params.gradeLiftRed, params.gradeLiftGreen, params.gradeLiftBlue)
        1 -> listOf(params.gradeGammaRed, params.gradeGammaGreen, params.gradeGammaBlue)
        2 -> listOf(params.gradeGainRed, params.gradeGainGreen, params.gradeGainBlue)
        else -> listOf(params.gradeOffsetRed, params.gradeOffsetGreen, params.gradeOffsetBlue)
    }
    fun update(v: List<Float>) {
        val r = v[0].coerceIn(-.5f, .5f); val g = v[1].coerceIn(-.5f, .5f); val b = v[2].coerceIn(-.5f, .5f)
        onChange(when(selected) {
            0 -> params.copy(gradeLiftRed = r, gradeLiftGreen = g, gradeLiftBlue = b)
            1 -> params.copy(gradeGammaRed = r, gradeGammaGreen = g, gradeGammaBlue = b)
            2 -> params.copy(gradeGainRed = r, gradeGainGreen = g, gradeGainBlue = b)
            else -> params.copy(gradeOffsetRed = r, gradeOffsetGreen = g, gradeOffsetBlue = b)
        })
    }
    Column {
        RecipeSectionTabs(labels.mapIndexed { i, label -> i to label }, selected, { selected = it })
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val latestValues by rememberUpdatedState(values)
            val latestUpdate by rememberUpdatedState<(List<Float>) -> Unit>(::update)
            val latestPendingWheel by rememberUpdatedState(pendingWheel)
            Canvas(Modifier.size(120.dp)
                .pointerInput(selected) {
                    fun set(position: Offset) {
                        val dx = (position.x - size.width / 2f) / (size.width / 2f)
                        val dy = (position.y - size.height / 2f) / (size.height / 2f)
                        val hue = ((atan2(dy, dx) * 180f / PI.toFloat()) + 360f) % 360f
                        val rgb = Color.hsv(hue, 1f, 1f)
                        val c = listOf(rgb.red, rgb.green, rgb.blue)
                        val mean = c.average().toFloat()
                        val master = latestValues.average().toFloat()
                        val amount = sqrt(dx * dx + dy * dy).coerceAtMost(1f) * .5f
                        pendingWheel = c.map { master + (it - mean) * amount }
                    }
                    detectDragGestures(
                        onDragStart = { set(it) },
                        onDragEnd = {
                            latestPendingWheel?.let { latestUpdate(it) }
                            pendingWheel = null
                        },
                        onDragCancel = { pendingWheel = null },
                    ) { change, _ -> change.consume(); set(change.position) }
                }
                .pointerInput(selected) { detectTapGestures(onDoubleTap = { latestUpdate(listOf(0f, 0f, 0f)) }) }
            ) {
                val radius = size.minDimension / 2f - 5f
                drawCircle(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)), radius)
                drawCircle(Brush.radialGradient(listOf(Color(0xFF777777), Color.Transparent), radius = radius), radius)
                drawCircle(Color.White.copy(alpha = .4f), radius, style = Stroke(1f))
                // Wheel chroma orientation derived from current channels, so slider and wheel stay in sync.
                val hsv = FloatArray(3)
                android.graphics.Color.RGBToHSV(((values[0] + .5f) * 255).toInt(), ((values[1] + .5f) * 255).toInt(), ((values[2] + .5f) * 255).toInt(), hsv)
                val angle = hsv[0] / 180f * PI.toFloat()
                val amount = ((values.maxOrNull()!! - values.minOrNull()!!) / .5f).coerceAtMost(1f)
                drawCircle(Color.White, 5f, center + Offset(cos(angle), sin(angle)) * radius * amount, style = Stroke(2f))
            }
            Column(Modifier.weight(1f)) {
                listOf(R.string.photo_grading_red, R.string.photo_grading_green, R.string.photo_grading_blue).forEachIndexed { index, label ->
                    Text(stringResource(label) + "  " + String.format(Locale.US, "%+.3f", values[index]), color = Color.White, fontSize = 10.sp)
                    Slider(values[index], { value -> update(values.mapIndexed { i, old -> if (index == i) value else old }) },
                        valueRange = -.5f.. .5f, modifier = Modifier.height(28.dp))
                }
            }
        }
        Text(stringResource(R.string.photo_grading_master), color = Color.White, fontSize = 10.sp)
        Slider(values.average().toFloat(), { v -> val delta = v - values.average().toFloat(); update(values.map { it + delta }) }, valueRange = -.5f.. .5f)
        Text(stringResource(R.string.photo_grading_wheel_help), color = Color.White.copy(alpha = .45f), fontSize = 10.sp)
    }
}
