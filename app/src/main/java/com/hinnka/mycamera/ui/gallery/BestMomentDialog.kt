package com.hinnka.mycamera.ui.gallery

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hinnka.mycamera.R
import com.hinnka.mycamera.livephoto.BestMomentExtractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun BestMomentDialog(file: File, sourceId: String, onDismiss: () -> Unit, onSaved: suspend () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var duration by remember(file) { mutableLongStateOf(0) }
    var position by remember(file) { mutableFloatStateOf(0f) }
    var frame by remember(file) { mutableStateOf<Bitmap?>(null) }
    var loading by remember(file) { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var suggestions by remember(file) { mutableStateOf<List<BestMomentExtractor.SuggestedMoment>>(emptyList()) }
    var analyzing by remember(file) { mutableStateOf(false) }
    var analysisFailed by remember(file) { mutableStateOf(false) }
    var manual by remember(file) { mutableStateOf(false) }
    LaunchedEffect(file, duration) {
        if (duration <= 0) return@LaunchedEffect
        analyzing = true
        try {
            suggestions = BestMomentExtractor.suggestions(file, duration)
            if (!manual && !saving) suggestions.firstOrNull()?.let { position = it.timeMs.toFloat() }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { analysisFailed = true }
        finally { analyzing = false }
    }
    LaunchedEffect(file) {
        try { duration = BestMomentExtractor.duration(file) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true; loading = false }
    }
    LaunchedEffect(file, position, duration) {
        if (duration <= 0) return@LaunchedEffect
        loading = true
        try {
            val next = BestMomentExtractor.frame(file, position.toLong(), true)
            frame = next
            failed = false
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
        finally { loading = false }
    }
    // Dispose after Compose has stopped displaying each old frame.
    DisposableEffect(frame) { val owned = frame; onDispose { owned?.recycle() } }
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.best_moment_title)) },
        text = { Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.best_moment_description))
            if (analyzing) Text(stringResource(R.string.best_moment_analyzing))
            if (analysisFailed) Text(stringResource(R.string.best_moment_analysis_failed))
            if (suggestions.isNotEmpty()) {
                Text(stringResource(R.string.best_moment_suggestions))
                suggestions.forEach { suggestion ->
                    FilterChip(selected = position.toLong() == suggestion.timeMs, enabled = !saving,
                        onClick = { manual = true; position = suggestion.timeMs.toFloat() },
                        label = { Text(stringResource(R.string.best_moment_suggestion, suggestion.timeMs / 1000f, suggestion.score)) })
                }
            }
            frame?.let { Image(it.asImageBitmap(), stringResource(R.string.best_moment_preview),
                Modifier.fillMaxWidth().height(240.dp), contentScale = ContentScale.Fit) }
            if (loading || saving) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(stringResource(R.string.best_moment_time, position / 1000f, duration / 1000f))
            Slider(value = position, onValueChange = { manual = true; position = it },
                valueRange = 0f..(duration - 1).coerceAtLeast(1).toFloat(), enabled = duration > 0 && !saving)
            if (failed) Text(stringResource(R.string.best_moment_failed), color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { TextButton(enabled = !loading && !saving && !failed && frame != null,
            onClick = {
                saving = true
                scope.launch {
                    try {
                        BestMomentExtractor.save(context, file, sourceId, position.toLong())
                        onSaved()
                        onDismiss()
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { failed = true }
                    finally { saving = false }
                }
            }) { Text(stringResource(R.string.best_moment_save)) } },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
