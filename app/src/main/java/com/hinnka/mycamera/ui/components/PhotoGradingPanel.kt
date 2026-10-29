package com.hinnka.mycamera.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hinnka.mycamera.R
import com.hinnka.mycamera.model.ColorRecipeParams
import com.hinnka.mycamera.model.RecipeParam
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.ln

private enum class PhotoGradingTool { LGGO, WHEELS, CURVES, COLOR }

@Composable
internal fun PhotoGradingPanel(
    currentParams: ColorRecipeParams,
    onParamsChange: (ColorRecipeParams) -> Unit,
    onParamChange: (RecipeParam, Float) -> Unit,
    onCurveChange: (CurveChannel, FloatArray?) -> Unit,
    previewBitmap: Bitmap?,
    imageHistogram: ImageHistogram?,
) {
    var tool by remember { mutableStateOf(PhotoGradingTool.LGGO) }
    var colorIndex by remember { mutableIntStateOf(0) }
    var scopeFailed by remember { mutableStateOf(false) }
    val scopes by produceState<PhotoScopeData?>(null, previewBitmap) {
        scopeFailed = false
        if (previewBitmap == null || previewBitmap.isRecycled) {
            value = null
            return@produceState
        }
        delay(140) // Cancel stale preview analyses while a control is being dragged.
        value = try { withContext(Dispatchers.Default) {
            ensureActive()
            val scale = minOf(1f, 192f / maxOf(previewBitmap.width, previewBitmap.height))
            val sample = Bitmap.createScaledBitmap(previewBitmap,
                (previewBitmap.width * scale).toInt().coerceAtLeast(1),
                (previewBitmap.height * scale).toInt().coerceAtLeast(1), true)
            try {
                val pixels = IntArray(sample.width * sample.height)
                sample.getPixels(pixels, 0, sample.width, 0, 0, sample.width, sample.height)
                ensureActive()
                calculatePhotoScopes(pixels, sample.width, sample.height)
            } finally {
                if (sample !== previewBitmap) sample.recycle()
            }
        } } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: OutOfMemoryError) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            scopeFailed = true
            null
        } catch (error: Exception) {
            // The editor can retire a cached preview while its replacement is being rendered.
            // A failed optional scope must not crash or prevent grading/export.
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            scopeFailed = true
            null
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.photo_grading_pipeline), color = Color.White.copy(alpha = .55f), fontSize = 10.sp)
        if (scopes != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScopeView(checkNotNull(scopes), false, Modifier.weight(1f))
                ScopeView(checkNotNull(scopes), true, Modifier.weight(1f))
            }
        } else {
            Text(stringResource(if (scopeFailed) R.string.photo_grading_scope_unavailable else R.string.photo_grading_scope_pending), color = Color.White.copy(alpha = .4f), fontSize = 10.sp)
        }
        RecipeSectionTabs(listOf(PhotoGradingTool.LGGO to R.string.photo_grading_lggo, PhotoGradingTool.WHEELS to R.string.photo_grading_wheels,
            PhotoGradingTool.CURVES to R.string.recipe_tab_curve,
            PhotoGradingTool.COLOR to R.string.recipe_tab_lch), tool, { tool = it })
        when (tool) {
            PhotoGradingTool.LGGO -> LggoPanel(currentParams, onParamsChange)
            PhotoGradingTool.WHEELS -> ColorGradingPanel(currentParams, onParamsChange)
            PhotoGradingTool.CURVES -> CurveEditorPanel(currentParams, onCurveChange, imageHistogram)
            PhotoGradingTool.COLOR -> {
                val groups = listOf(
                    R.string.recipe_lch_skin to listOf(RecipeParam.SKIN_HUE, RecipeParam.SKIN_CHROMA, RecipeParam.SKIN_LIGHTNESS),
                    R.string.recipe_lch_red to listOf(RecipeParam.RED_HUE, RecipeParam.RED_CHROMA, RecipeParam.RED_LIGHTNESS),
                    R.string.recipe_lch_orange to listOf(RecipeParam.ORANGE_HUE, RecipeParam.ORANGE_CHROMA, RecipeParam.ORANGE_LIGHTNESS),
                    R.string.recipe_lch_yellow to listOf(RecipeParam.YELLOW_HUE, RecipeParam.YELLOW_CHROMA, RecipeParam.YELLOW_LIGHTNESS),
                    R.string.recipe_lch_green to listOf(RecipeParam.GREEN_HUE, RecipeParam.GREEN_CHROMA, RecipeParam.GREEN_LIGHTNESS),
                    R.string.recipe_lch_cyan to listOf(RecipeParam.CYAN_HUE, RecipeParam.CYAN_CHROMA, RecipeParam.CYAN_LIGHTNESS),
                    R.string.recipe_lch_blue to listOf(RecipeParam.BLUE_HUE, RecipeParam.BLUE_CHROMA, RecipeParam.BLUE_LIGHTNESS),
                    R.string.recipe_lch_purple to listOf(RecipeParam.PURPLE_HUE, RecipeParam.PURPLE_CHROMA, RecipeParam.PURPLE_LIGHTNESS),
                    R.string.recipe_lch_magenta to listOf(RecipeParam.MAGENTA_HUE, RecipeParam.MAGENTA_CHROMA, RecipeParam.MAGENTA_LIGHTNESS))
                // Three compact rows keep every range reachable on small screens.
                groups.chunked(3).forEachIndexed { row, items ->
                    RecipeSectionTabs(items.mapIndexed { i, group -> (row * 3 + i) to group.first }, colorIndex, { colorIndex = it })
                }
                groups[colorIndex].second.forEach { param ->
                    ColorRecipeSlider(param, param.getValue(currentParams), { onParamChange(param, it) })
                }
            }
        }
    }
}

@Composable
private fun ScopeView(data: PhotoScopeData, vector: Boolean, modifier: Modifier) {
    Column(modifier) {
        Text(stringResource(if (vector) R.string.photo_grading_vectorscope else R.string.photo_grading_waveform),
            color = Color.White.copy(alpha = .7f), fontSize = 10.sp)
        Canvas(Modifier.fillMaxWidth().height(86.dp)) {
            drawRect(Color(0xFF090C10))
            if (vector) {
                drawCircle(Color.White.copy(alpha = .14f), radius = minOf(size.width, size.height) * .45f, style = Stroke(1f))
                drawLine(Color.White.copy(alpha = .12f), Offset(size.width / 2, 0f), Offset(size.width / 2, size.height))
            } else {
                for (i in 0..4) drawLine(Color.White.copy(alpha = .12f), Offset(0f, size.height * i / 4), Offset(size.width, size.height * i / 4))
            }
            val counts = if (vector) data.vectors else data.waveform
            val max = counts.maxOrNull()?.coerceAtLeast(1) ?: 1
            val w = size.width / data.bins
            val h = size.height / data.bins
            counts.forEachIndexed { i, count -> if (count > 0) {
                val density = (ln(1f + count) / ln(1f + max)).coerceIn(.15f, 1f)
                drawRect(Color(0xFF8DDFC8).copy(alpha = density), Offset((i % data.bins) * w, (i / data.bins) * h), Size(w.coerceAtLeast(1f), h.coerceAtLeast(1f)))
            } }
        }
    }
}
