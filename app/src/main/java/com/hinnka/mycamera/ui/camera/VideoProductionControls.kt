package com.hinnka.mycamera.ui.camera

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hinnka.mycamera.R
import com.hinnka.mycamera.video.VideoProductionManager

@Composable
fun VideoProductionControls(recording: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var slate by remember { mutableStateOf(VideoProductionManager.read(context)) }
    val status by VideoProductionManager.status.collectAsState()
    fun update(value: VideoProductionManager.Slate) {
        slate = value
        VideoProductionManager.save(context, value)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Text(stringResource(R.string.production_slate), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(slate.scene, { update(slate.copy(scene = it.take(80))) },
                Modifier.weight(1f), enabled = !recording, singleLine = true,
                label = { Text(stringResource(R.string.production_scene)) })
            OutlinedTextField(slate.take, { update(slate.copy(take = it.take(32))) },
                Modifier.weight(1f), enabled = !recording, singleLine = true,
                label = { Text(stringResource(R.string.production_take)) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(slate.reel, { update(slate.copy(reel = it.take(80))) },
                Modifier.weight(1f), enabled = !recording, singleLine = true,
                label = { Text(stringResource(R.string.production_reel)) })
            OutlinedTextField(slate.camera, { update(slate.copy(camera = it.take(80))) },
                Modifier.weight(1f), enabled = !recording, singleLine = true,
                label = { Text(stringResource(R.string.production_camera)) })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.production_good_take), Modifier.weight(1f))
            Switch(slate.goodTake, { update(slate.copy(goodTake = it)) }, enabled = !recording)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.production_time_of_day), Modifier.weight(1f))
            Switch(slate.timeOfDay, { update(slate.copy(timeOfDay = it)) }, enabled = !recording)
        }
        Text(stringResource(R.string.production_timecode_hint), style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.production_proxy), Modifier.weight(1f))
            Switch(slate.proxyEnabled, { update(slate.copy(proxyEnabled = it)) }, enabled = !recording && Build.VERSION.SDK_INT >= 31)
        }
        Text(stringResource(if (Build.VERSION.SDK_INT >= 31) R.string.production_proxy_hint else R.string.production_proxy_unavailable), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.production_sidecar_hint), style = MaterialTheme.typography.bodySmall)
        if (status != VideoProductionManager.Status.IDLE) {
            Text(stringResource(when (status) {
                VideoProductionManager.Status.WORKING -> R.string.production_working
                VideoProductionManager.Status.SAVED -> R.string.production_saved
                else -> R.string.production_failed
            }), style = MaterialTheme.typography.bodySmall)
        }
    }
}
