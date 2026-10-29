package com.hinnka.mycamera.ui.gallery

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hinnka.mycamera.R
import com.hinnka.mycamera.gallery.MediaData
import com.hinnka.mycamera.gallery.PhotoDiagnostics
import kotlinx.coroutines.CancellationException

@Composable
fun PhotoDiagnosticsDialog(photo: MediaData, onDismiss: () -> Unit) {
    val context=LocalContext.current.applicationContext
    var report by remember(photo.id) { mutableStateOf<PhotoDiagnostics.Report?>(null) }
    var failed by remember(photo.id) { mutableStateOf(false) }
    LaunchedEffect(photo.id) {
        try { report=PhotoDiagnostics.analyze(context,photo) }
        catch(cancelled: CancellationException) { throw cancelled }
        catch(_: Exception) { failed=true }
    }
    val unknown=stringResource(R.string.photo_diagnostics_unknown)
    AlertDialog(onDismissRequest=onDismiss,title={Text(stringResource(R.string.photo_diagnostics_title))},
        text={Column(Modifier.heightIn(max=520.dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.photo_diagnostics_description))
            if(report==null && !failed) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(failed) Text(stringResource(R.string.photo_diagnostics_failed),color=MaterialTheme.colorScheme.error)
            report?.let { result ->
                val metadata=result.metadata
                Text(stringResource(R.string.photo_diagnostics_dimensions,
                    metadata?.width?.takeIf { it>0 } ?: photo.width,metadata?.height?.takeIf { it>0 } ?: photo.height))
                Text(stringResource(R.string.photo_diagnostics_sample,result.width,result.height))
                Text(stringResource(R.string.photo_diagnostics_exposure,metadata?.iso?.toString() ?: unknown,
                    metadata?.shutterSpeed ?: unknown,metadata?.aperture ?: unknown))
                Text(stringResource(R.string.photo_diagnostics_edge,result.edge))
                Text(stringResource(R.string.photo_diagnostics_clipping,result.highlights,result.shadows))
                if(result.highlights>2 || result.shadows>2) Text(stringResource(R.string.photo_diagnostics_clipping_tip))
                if(result.residual!=null) Text(stringResource(R.string.photo_diagnostics_noise,result.residual,result.flatArea))
                else Text(stringResource(R.string.photo_diagnostics_noise_unavailable))
                Text(stringResource(R.string.photo_diagnostics_interpretation))
                val properties=metadata?.customProperties.orEmpty()
                val reconstruction=properties["sequenceReconstructionType"]?.takeIf { it.isNotBlank() }
                val rejected=properties["sequenceRejectedFrames"] ?: properties["rejectedFrameIndices"]
                Text(stringResource(R.string.photo_diagnostics_reconstruction,reconstruction ?: unknown))
                Text(stringResource(R.string.photo_diagnostics_rejected,
                    rejected?.split(',')?.count { it.trim().toIntOrNull()!=null }?.toString() ?: unknown))
                properties["sequenceFallbackReason"]?.takeIf { it.isNotBlank() }?.let {
                    Text(stringResource(R.string.photo_diagnostics_fallback,it))
                }
            }
        }},confirmButton={TextButton(onClick=onDismiss) {Text(stringResource(R.string.cancel))}})
}
