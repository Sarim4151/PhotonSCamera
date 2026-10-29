package com.hinnka.mycamera.ui.gallery

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hinnka.mycamera.R
import com.hinnka.mycamera.processor.CompositeRegion
import com.hinnka.mycamera.processor.RegionCompositeTools
import com.hinnka.mycamera.processor.FacePhotoTools
import com.hinnka.mycamera.processor.FaceFrameAnalysis
import com.hinnka.mycamera.viewmodel.GalleryViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Composable
fun CompositeEditorDialog(viewModel: GalleryViewModel, onDismiss: () -> Unit) {
    var inputs by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var candidates by remember { mutableStateOf<List<FaceFrameAnalysis>>(emptyList()) }
    var reviewed by remember { mutableStateOf(false) }
    var renderedRegion by remember { mutableStateOf<CompositeRegion?>(null) }
    var failure by remember { mutableStateOf(false) }
    val renderMutex = remember { Mutex() }
    val history = remember { mutableStateListOf<CompositeRegion>() }
    val region = viewModel.compositeRegion
    fun update(value: CompositeRegion) { history.add(viewModel.compositeRegion); viewModel.compositeRegion = value }
    LaunchedEffect(Unit) {
        var owned = emptyList<Bitmap>()
        try {
            owned = viewModel.compositeInputs()
            renderMutex.withLock { candidates = FacePhotoTools.analyze(owned) }
            inputs = owned
            awaitCancellation()
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { failure = true }
        finally { withContext(NonCancellable) { renderMutex.withLock { owned.forEach { it.recycle() } } } }
    }
    LaunchedEffect(inputs, region) {
        reviewed = false
        if (inputs.size == 2) {
            try {
                delay(180)
                renderMutex.withLock {
                    val result = RegionCompositeTools.render(inputs[0], inputs[1], region)
                    // Published snapshots may still be held by Compose render nodes; GC owns their release.
                    preview = result
                    renderedRegion = region
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { failure = true }
        }
    }
    DisposableEffect(Unit) { onDispose { preview = null } }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.composite_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.composite_guidance))
                if (failure) Text(stringResource(R.string.frame_tools_decode_failed))
                preview?.let { bitmap ->
                    Box {
                    Image(bitmap.asImageBitmap(), contentDescription = stringResource(R.string.composite_preview),
                        modifier = Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat()/bitmap.height).pointerInput(Unit) {
                            detectDragGestures(onDragStart = { history.add(viewModel.compositeRegion) }) { change, drag ->
                                change.consume()
                                val current = viewModel.compositeRegion
                                viewModel.compositeRegion = current.copy(x=(current.x+drag.x/size.width).coerceIn(0f,1f), y=(current.y+drag.y/size.height).coerceIn(0f,1f))
                            }
                        })
                        Canvas(Modifier.matchParentSize()) {
                            val top = Offset((region.x-region.width/2)*size.width, (region.y-region.height/2)*size.height)
                            val extent = Size(region.width*size.width, region.height*size.height)
                            if (region.ellipse) drawOval(Color.Yellow, top, extent, style=Stroke(2.dp.toPx()))
                            else drawRect(Color.Yellow, top, extent, style=Stroke(2.dp.toPx()))
                        }
                    }

                }
                Text(stringResource(R.string.composite_face_candidates))
                candidates.getOrNull(if(region.reversed) 0 else 1)?.faces?.forEachIndexed { index, face ->
                    TextButton(onClick = {
                        val rect=face.normalizedRect
                        update(region.copy(x=(rect.centerX()+region.dx).coerceIn(0f,1f), y=(rect.centerY()+region.dy).coerceIn(0f,1f), width=(rect.width()*1.25f).coerceIn(.05f,1f), height=(rect.height()*1.25f).coerceIn(.05f,1f), ellipse=true))
                    }) { Text(stringResource(R.string.composite_face_candidate,index+1)) }
                }
                Row { Checkbox(reviewed,{ reviewed=it },enabled=preview!=null && renderedRegion==region); Text(stringResource(R.string.group_review_confirm)) }
                TextButton(onClick = { update(region.copy(reversed = !region.reversed)) }) { Text(stringResource(R.string.composite_swap)) }
                Row { Checkbox(region.ellipse, { update(region.copy(ellipse=it)) }); Text(stringResource(R.string.composite_ellipse)) }
                RegionSlider(R.string.composite_x, region.x, 0f..1f) { update(region.copy(x=it)) }
                RegionSlider(R.string.composite_y, region.y, 0f..1f) { update(region.copy(y=it)) }
                RegionSlider(R.string.composite_width, region.width, .05f..1f) { update(region.copy(width=it)) }
                RegionSlider(R.string.composite_height, region.height, .05f..1f) { update(region.copy(height=it)) }
                RegionSlider(R.string.composite_align_x, region.dx, -.5f.. .5f) { update(region.copy(dx=it)) }
                RegionSlider(R.string.composite_align_y, region.dy, -.5f.. .5f) { update(region.copy(dy=it)) }
                RegionSlider(R.string.composite_feather, region.feather, 0f.. .2f) { update(region.copy(feather=it)) }
                TextButton(onClick = { if(history.isNotEmpty()) viewModel.compositeRegion=history.removeAt(history.lastIndex) }, enabled = history.isNotEmpty()) { Text(stringResource(R.string.composite_undo)) }
            }
        },
        confirmButton = { TextButton(onClick = { viewModel.saveComposite(); onDismiss() }, enabled = inputs.size == 2 && preview != null && reviewed && renderedRegion==region && !failure) { Text(stringResource(R.string.composite_save)) } },
        dismissButton = { TextButton(onClick=onDismiss) { Text(stringResource(android.R.string.cancel)) } })
}

@Composable
private fun RegionSlider(label: Int, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Text(stringResource(label)); Slider(value, onChange, valueRange=range)
}
