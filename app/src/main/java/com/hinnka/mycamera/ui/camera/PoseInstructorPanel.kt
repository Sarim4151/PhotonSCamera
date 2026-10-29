package com.hinnka.mycamera.ui.camera

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.hinnka.mycamera.R
import com.hinnka.mycamera.pose.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.Executors

/** The readback already includes the renderer's orientation, crop and front-camera mirror.
 * Normalized landmarks map directly to this exact preview box: never mirror/rotate them again. */
@Composable
internal fun BoxScope.PoseInstructorPanel(active: Boolean, cameraKey: String?, magnification: Int,
    topPadding: androidx.compose.ui.unit.Dp, surface: () -> CameraGLSurfaceView?) {
    var enabled by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf(false) }
    var guide by remember { mutableStateOf(PoseGuide.PORTRAIT) }
    var reading by remember { mutableStateOf(PoseReading()) }
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentSurface by rememberUpdatedState(surface)
    val eligible = active && magnification == 1
    LaunchedEffect(enabled, eligible, cameraKey, guide, lifecycle) {
        reading = PoseReading()
        if (!enabled || !eligible) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // Each camera/lifecycle session owns its request flag; callbacks from a retired
            // surface cannot block or reset a new session's request.
            val pending = AtomicBoolean(false)
            val worker = Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "PoseInstructor").apply { priority = Thread.MIN_PRIORITY }
            }.asCoroutineDispatcher()
            var instructor: PoseInstructor? = null
            try {
                withContext(worker) { instructor = PoseInstructor(context) }
                while (isActive) {
                    val started = SystemClock.elapsedRealtime()
                    val frame = Channel<Bitmap?>(1, onUndeliveredElement = { it?.recycle() })
                    try {
                        val view = currentSurface()
                        if (view != null && pending.compareAndSet(false, true)) {
                            view.captureOriginalPreviewFrame(640) { bitmap ->
                                pending.set(false)
                                if (!frame.trySend(bitmap).isSuccess) bitmap?.recycle()
                            }
                            val bitmap = withTimeoutOrNull(1500) { frame.receive() }
                            if (bitmap != null) {
                                try { reading = withContext(worker) { instructor!!.inspect(bitmap, guide) } }
                                finally { bitmap.recycle() }
                            } else reading = PoseReading(hint = PoseHint.UNAVAILABLE)
                        }
                    } finally { frame.cancel() }
                    delay((400 - (SystemClock.elapsedRealtime() - started)).coerceAtLeast(1))
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { reading = PoseReading(hint = PoseHint.UNAVAILABLE) }
            finally {
                withContext(NonCancellable + worker) { instructor?.close() }
                worker.close()
            }
        }
    }
    if (enabled && eligible) {
        Canvas(Modifier.fillMaxSize()) {
            val points = reading.points
            val edges = listOf(11 to 12, 11 to 13, 13 to 15, 12 to 14, 14 to 16,
                11 to 23, 12 to 24, 23 to 24, 23 to 25, 25 to 27, 24 to 26, 26 to 28)
            fun visible(i: Int) = points.getOrNull(i)?.let { it.confidence >= .6f && it.x in 0f..1f && it.y in 0f..1f } == true
            for ((a, b) in edges) if (visible(a) && visible(b)) {
                drawLine(Color(0xFF8DE1CF), Offset(points[a].x * size.width, points[a].y * size.height),
                    Offset(points[b].x * size.width, points[b].y * size.height), 2.dp.toPx())
            }
            points.forEach { if (it.confidence >= .6f && it.x in 0f..1f && it.y in 0f..1f)
                drawCircle(Color.White, 3.dp.toPx(), Offset(it.x * size.width, it.y * size.height)) }
        }
        Text(stringResource(poseHintLabel(reading.hint)), color = Color.White,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = topPadding + 40.dp)
                .background(Color.Black.copy(alpha = .65f), RoundedCornerShape(8.dp)).padding(8.dp))
    }
    if (active) TextButton(onClick = { dialog = true },
        modifier = Modifier.align(Alignment.TopEnd).padding(top = topPadding + 8.dp, end = 6.dp)
            .background(Color.Black.copy(alpha = .65f), RoundedCornerShape(20.dp))) {
        Text(stringResource(if (enabled) R.string.pose_tool_on else R.string.pose_tool), color = Color.White)
    }
    if (dialog) AlertDialog(onDismissRequest = { dialog = false },
        title = { Text(stringResource(R.string.pose_tool)) },
        text = { Column {
            Text(stringResource(R.string.pose_description))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.pose_enable), Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
            PoseGuide.entries.forEach { option ->
                TextButton(onClick = { guide = option }) {
                    RadioButton(selected = guide == option, onClick = { guide = option })
                    Text(stringResource(if (option == PoseGuide.PORTRAIT) R.string.pose_portrait else R.string.pose_full_body))
                }
            }
            if (magnification != 1) Text(stringResource(R.string.pose_magnification))
        } }, confirmButton = { TextButton(onClick = { dialog = false }) { Text(stringResource(android.R.string.ok)) } })
}

private fun poseHintLabel(hint: PoseHint) = when (hint) {
    PoseHint.SEARCHING -> R.string.pose_searching
    PoseHint.CENTER -> R.string.pose_center
    PoseHint.HEADROOM -> R.string.pose_headroom
    PoseHint.SHOULDERS -> R.string.pose_shoulders
    PoseHint.HEAD -> R.string.pose_head
    PoseHint.ARMS -> R.string.pose_arms
    PoseHint.FEET -> R.string.pose_feet
    PoseHint.READY -> R.string.pose_ready
    PoseHint.UNAVAILABLE -> R.string.pose_unavailable
}
