package com.hinnka.mycamera.ui.gallery

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Paint
import android.provider.MediaStore
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hinnka.mycamera.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class PaintStroke(val points: List<Offset>, val color: Color, val width: Float)
private val paintColors = listOf(Color.White, Color.Black, Color(0xFFE53935), Color(0xFFFFC107), Color(0xFF43A047), Color(0xFF1E88E5))

/** A bounded photo canvas with independently editable paint layers and non-destructive export. */
@Composable
fun PhotoPaintDialog(load: suspend () -> Bitmap?, onDismiss: () -> Unit, onSaved: suspend () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var layers by remember { mutableStateOf(listOf(emptyList<PaintStroke>())) }
    var selectedLayer by remember { mutableIntStateOf(0) }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var brushColor by remember { mutableStateOf(paintColors[2]) }
    var brushSize by remember { mutableFloatStateOf(5f) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { bitmap = withContext(Dispatchers.IO) { load() }; error = bitmap == null }
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.photo_paint_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.photo_paint_hint), style = MaterialTheme.typography.bodySmall)
                val source = bitmap
                if (source == null) CircularProgressIndicator()
                else Box(Modifier.fillMaxWidth().aspectRatio(source.width.toFloat() / source.height).background(Color.DarkGray)) {
                    Image(source.asImageBitmap(), null, Modifier.matchParentSize())
                    Canvas(Modifier.matchParentSize().pointerInput(selectedLayer, brushColor, brushSize, saving) {
                        if (saving) return@pointerInput
                        detectDragGestures(onDragStart = { current = listOf(Offset((it.x / size.width).coerceIn(0f,1f), (it.y / size.height).coerceIn(0f,1f))) },
                            onDragEnd = {
                                if (current.isNotEmpty()) {
                                    val stroke = PaintStroke(current, brushColor, brushSize.dp.toPx() / size.width)
                                    layers = layers.mapIndexed { i, layer -> if (i == selectedLayer) layer + stroke else layer }
                                }
                                current = emptyList()
                            }, onDragCancel = { current = emptyList() }) { change, _ ->
                            current = current + Offset((change.position.x / size.width).coerceIn(0f,1f), (change.position.y / size.height).coerceIn(0f,1f))
                            change.consume()
                        }
                    }) {
                        (layers.flatten() + if (current.isNotEmpty()) listOf(PaintStroke(current,brushColor,brushSize.dp.toPx() / size.width)) else emptyList()).forEach { stroke ->
                            val path = Path()
                            stroke.points.forEachIndexed { i, point ->
                                val x = point.x * size.width; val y = point.y * size.height
                                if (i == 0) path.moveTo(x,y) else path.lineTo(x,y)
                            }
                            drawPath(path, stroke.color, style = Stroke(stroke.width * size.width, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    paintColors.forEach { color ->
                        Surface(onClick = { brushColor = color }, color = color, shape = CircleShape,
                            modifier = Modifier.size(if (color == brushColor) 34.dp else 28.dp)) {}
                    }
                }
                Text(stringResource(R.string.photo_paint_brush, brushSize.toInt()))
                Slider(brushSize, { brushSize = it }, valueRange = 2f..24f)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { layers = layers + listOf(emptyList()); selectedLayer = layers.lastIndex }, enabled = !saving) { Text(stringResource(R.string.photo_paint_add_layer)) }
                    TextButton(onClick = {
                        val target = layers.indexOfLast { it.isNotEmpty() }
                        if (target >= 0) layers = layers.mapIndexed { i, layer -> if (i == target) layer.dropLast(1) else layer }
                    }, enabled = !saving) { Text(stringResource(R.string.photo_paint_undo)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    layers.forEachIndexed { index, _ ->
                        FilterChip(selected = index == selectedLayer, onClick = { selectedLayer = index },
                            label = { Text("${index + 1}") })
                    }
                }
                Text(stringResource(R.string.photo_paint_layers, layers.size, selectedLayer + 1))
                if (error) Text(stringResource(R.string.photo_paint_error), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(enabled = bitmap != null && !saving && layers.any { it.isNotEmpty() }, onClick = {
                val source = bitmap ?: return@Button
                val snapshot = layers.flatten()
                saving = true; error = false
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            val output = source.copy(Bitmap.Config.ARGB_8888, true)
                            val canvas = android.graphics.Canvas(output)
                            snapshot.forEach { stroke ->
                                val brush = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                    color = android.graphics.Color.argb((stroke.color.alpha*255).toInt(), (stroke.color.red*255).toInt(), (stroke.color.green*255).toInt(), (stroke.color.blue*255).toInt())
                                    style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
                                    strokeWidth = stroke.width * source.width
                                }
                                val path = android.graphics.Path()
                                stroke.points.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x*source.width,p.y*source.height) else path.lineTo(p.x*source.width,p.y*source.height) }
                                canvas.drawPath(path, brush)
                            }
                            val values = ContentValues().apply {
                                put(MediaStore.Images.Media.DISPLAY_NAME, "Photon_Paint_${System.currentTimeMillis()}.jpg")
                                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                                put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/PhotonCamera")
                                put(MediaStore.Images.Media.IS_PENDING, 1)
                            }
                            val uri = requireNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
                            try {
                                context.contentResolver.openOutputStream(uri)?.use { check(output.compress(Bitmap.CompressFormat.JPEG, 95, it)) } ?: throw IllegalStateException("Output unavailable")
                                context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                            } catch (e: Exception) { context.contentResolver.delete(uri, null, null); throw e }
                            finally { output.recycle() }
                        }
                        onSaved(); onDismiss()
                    } catch (_: Exception) { error = true }
                    finally { saving = false }
                }
            }) { Text(stringResource(R.string.photo_paint_save)) }
        }, dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(android.R.string.cancel)) } })
}
