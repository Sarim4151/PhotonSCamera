package com.hinnka.mycamera.ui.settings

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.DisposableEffect
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hinnka.mycamera.ui.theme.AccentColor
import com.hinnka.mycamera.R
import com.hinnka.mycamera.model.CameraPreset
import com.hinnka.mycamera.raw.RawRenderingEngine
import com.hinnka.mycamera.utils.PLog
import com.hinnka.mycamera.viewmodel.CameraViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import com.hinnka.mycamera.ui.icons.AppIcons

private fun presetPackageFileName(displayName: String, presetId: String): String {
    val sanitized = displayName
        .trim()
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .trim('.', ' ')
        .take(80)
        .ifBlank { presetId }
    val baseName = if (sanitized.endsWith(".pp", ignoreCase = true)) {
        sanitized.dropLast(3)
    } else {
        sanitized
    }
    return "$baseName.pp"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetManagementScreen(
    viewModel: CameraViewModel,
    onBack: () -> Unit,
    onPresetEditClick: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val allPresets by viewModel.allPresets.collectAsState()
    val activePresetId by viewModel.activePresetId.collectAsState()
    val activePresetModified by viewModel.isActivePresetModified.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var localPresets by remember { mutableStateOf(allPresets) }
    var deletingPreset by remember { mutableStateOf<CameraPreset?>(null) }
    var renamingPreset by remember { mutableStateOf<CameraPreset?>(null) }
    var renameText by remember { mutableStateOf("") }
    var searchText by remember { mutableStateOf("") }
    var favoritesOnly by remember { mutableStateOf(false) }
    var showRestoreDefaultsDialog by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var exportingPresetId by remember { mutableStateOf<String?>(null) }
    var pendingExportBytes by remember { mutableStateOf(ByteArray(0)) }
    val lazyListState = rememberLazyListState()
    var referenceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showBefore by remember { mutableStateOf(false) }
    val referencePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                    android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                        decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                        val scale = minOf(1.0, 512.0 / maxOf(info.size.width, info.size.height))
                        decoder.setTargetSize(maxOf(1, (info.size.width * scale).toInt()), maxOf(1, (info.size.height * scale).toInt()))
                    }
                }.getOrNull()
            }
            referenceBitmap = bitmap
        }
    }


    val presetFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty() && !isImporting) {
            isImporting = true
            scope.launch {
                var successCount = 0
                uris.forEach { uri ->
                    if (viewModel.importPresetPackage(uri)) successCount++
                }
                val failedCount = uris.size - successCount
                isImporting = false
                val message = when {
                    failedCount == 0 -> context.getString(
                        R.string.preset_import_success,
                        successCount,
                    )
                    successCount == 0 -> context.getString(
                        R.string.preset_import_failed,
                        failedCount,
                    )
                    else -> context.getString(
                        R.string.preset_import_partial,
                        successCount,
                        failedCount,
                    )
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val success = withContext(Dispatchers.IO) {
                    try {
                        val output = context.contentResolver.openOutputStream(uri, "wt")
                            ?: error("Cannot open preset export destination")
                        output.use { it.write(pendingExportBytes) }
                        true
                    } catch (e: Exception) {
                        PLog.e("PresetManagementScreen", "Failed to write preset package", e)
                        false
                    }
                }
                Toast.makeText(
                    context,
                    context.getString(
                        if (success) R.string.preset_export_success else R.string.preset_export_failed
                    ),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    LaunchedEffect(allPresets) {
        val localIds = localPresets.map { it.id }
        val incomingIds = allPresets.map { it.id }
        if (localIds.toSet() != incomingIds.toSet()) {
            val byId = allPresets.associateBy { it.id }
            localPresets = localIds.mapNotNull { byId[it] } + allPresets.filter { it.id !in localIds }
        } else {
            val byId = allPresets.associateBy { it.id }
            localPresets = localPresets.mapNotNull { byId[it.id] }
        }
    }

    fun saveOrderAndBack() {
        viewModel.savePresetOrder(localPresets)
        onBack()
    }

    BackHandler {
        saveOrderAndBack()
    }

    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromId = from.key as? String ?: return@rememberReorderableLazyListState
        val toId = to.key as? String ?: return@rememberReorderableLazyListState
        val fromIndex = localPresets.indexOfFirst { it.id == fromId }
        val toIndex = localPresets.indexOfFirst { it.id == toId }
        if (fromIndex != -1 && toIndex != -1) {
            localPresets = localPresets.toMutableList().apply {
                add(toIndex, removeAt(fromIndex))
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF151515))
            .navigationBarsPadding()
    ) {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.settings_preset_management),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium
                )
            },
            navigationIcon = {
                IconButton(onClick = { saveOrderAndBack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = Color.White
                    )
                }
            },
            actions = {
                val defaultPresetName = stringResource(R.string.preset_new_preset_default)
                IconButton(
                    onClick = {
                        viewModel.prepareCurrentSettingsPresetDraft(defaultPresetName)
                        onPresetEditClick(null)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.preset_new),
                        tint = Color.White
                    )
                }
                IconButton(
                    onClick = { presetFilePicker.launch(arrayOf("*/*")) },
                    enabled = !isImporting,
                ) {
                    if (isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = AppIcons.Download,
                            contentDescription = stringResource(R.string.preset_import),
                            tint = Color.White,
                        )
                    }
                }
                IconButton(onClick = { showRestoreDefaultsDialog = true }) {
                    Icon(
                        imageVector = AppIcons.RestartAlt,
                        contentDescription = stringResource(R.string.preset_restore_defaults),
                        tint = Color.White.copy(alpha = 0.8f)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF151515))
        )

        Row {
            TextButton(onClick = { referencePicker.launch(arrayOf("image/*")) }) { Text(stringResource(R.string.recipe_reference)) }
            if (referenceBitmap != null) TextButton(onClick = { showBefore = !showBefore }) {
                Text(stringResource(if (showBefore) R.string.recipe_after else R.string.recipe_before))
            }
        }
        if (referenceBitmap != null) Text(stringResource(R.string.recipe_reference_scope), modifier = Modifier.padding(horizontal = 16.dp))
        OutlinedTextField(value = searchText, onValueChange = { searchText = it },
            label = { Text(stringResource(R.string.recipe_search)) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        TextButton(onClick = { favoritesOnly = !favoritesOnly }) {
            Text(stringResource(if (favoritesOnly) R.string.recipe_show_all else R.string.recipe_favorites))
        }
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(localPresets.filter { (!favoritesOnly || it.isFavorite) && (searchText.isBlank() || it.name.contains(searchText, true)) }, key = { _, preset -> preset.id }) { _, preset ->
                val displayName = presetDisplayName(preset)
                ReorderableItem(reorderableLazyListState, key = preset.id) { isDragging ->
                    PresetManagementItem(
                        preset = preset,
                        referenceBitmap = referenceBitmap,
                        showBefore = showBefore,
                        renderReference = { source -> viewModel.renderPresetReference(preset, source) },
                        displayName = displayName,
                        isActive = activePresetId == preset.id,
                        isModified = activePresetModified && activePresetId == preset.id,
                        isDragging = isDragging,
                        isExporting = exportingPresetId == preset.id,
                        dragModifier = Modifier.draggableHandle(),
                        onSelect = { viewModel.applyPreset(preset) },
                        onExport = {
                            if (exportingPresetId == null) {
                                exportingPresetId = preset.id
                                scope.launch {
                                    val bytes = viewModel.exportPresetPackage(preset, displayName)
                                    exportingPresetId = null
                                    if (bytes != null) {
                                        pendingExportBytes = bytes
                                        exportLauncher.launch(
                                            presetPackageFileName(displayName, preset.id)
                                        )
                                    } else {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.preset_export_failed),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                }
                            }
                        },
                        onRename = { renamingPreset = preset; renameText = displayName },
                        onDuplicate = { viewModel.updatePresetIdentity(preset.id, name = context.getString(R.string.recipe_copy_name, displayName), duplicate = true) },
                        onFavorite = { viewModel.updatePresetIdentity(preset.id, favorite = !preset.isFavorite) },
                        onEdit = { onPresetEditClick(preset.id) },
                        onDelete = { deletingPreset = preset }
                    )
                }
            }
        }
    }

    renamingPreset?.let { preset ->
        AlertDialog(onDismissRequest = { renamingPreset = null },
            title = { Text(stringResource(R.string.recipe_rename)) },
            text = { OutlinedTextField(value = renameText, onValueChange = { renameText = it }) },
            confirmButton = { TextButton(enabled = renameText.isNotBlank(), onClick = {
                viewModel.updatePresetIdentity(preset.id, name = renameText)
                renamingPreset = null
            }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { renamingPreset = null }) { Text(stringResource(R.string.cancel)) } })
    }

    deletingPreset?.let { preset ->
        AlertDialog(
            onDismissRequest = { deletingPreset = null },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.preset_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        localPresets = localPresets.filterNot { it.id == preset.id }
                        viewModel.deletePreset(preset.id)
                        deletingPreset = null
                    }
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingPreset = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showRestoreDefaultsDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDefaultsDialog = false },
            title = { Text(stringResource(R.string.preset_restore_defaults)) },
            text = { Text(stringResource(R.string.preset_restore_defaults_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetToDefaultPresets()
                        showRestoreDefaultsDialog = false
                    }
                ) {
                    Text(stringResource(R.string.preset_restore_defaults), color = AccentColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDefaultsDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun PresetManagementItem(
    preset: CameraPreset,
    referenceBitmap: Bitmap?,
    showBefore: Boolean,
    renderReference: suspend (Bitmap) -> Bitmap?,
    displayName: String,
    isActive: Boolean,
    isModified: Boolean,
    isDragging: Boolean,
    isExporting: Boolean,
    dragModifier: Modifier,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onFavorite: () -> Unit,
    onSelect: () -> Unit,
    onExport: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var renderedReference by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(preset, referenceBitmap) {
        renderedReference = referenceBitmap?.let { renderReference(it) }
    }
    DisposableEffect(renderedReference) {
        val owned = renderedReference
        onDispose { owned?.let { if (!it.isRecycled) it.recycle() } }
    }
    val cardColor = when {
        isDragging -> Color.White.copy(alpha = 0.14f)
        isActive -> AccentColor.copy(alpha = 0.10f)
        else -> Color.White.copy(alpha = 0.055f)
    }
    val borderColor = if (isActive) AccentColor.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.10f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp)),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = AppIcons.DragHandle,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.45f),
                modifier = dragModifier.size(28.dp)
            )

            val thumbnail = if (showBefore) referenceBitmap else renderedReference
            if (thumbnail != null && !thumbnail.isRecycled) Image(thumbnail.asImageBitmap(),
                contentDescription = stringResource(R.string.recipe_reference), modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isModified) "$displayName*" else displayName,
                        color = if (isActive) AccentColor else Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    PresetBadge(
                        text = stringResource(if (preset.isBuiltIn) R.string.preset_builtin_badge else R.string.preset_custom_badge),
                        highlighted = preset.isBuiltIn
                    )
                }

                FlowRow {
                    TextButton(onClick = onRename) { Text(stringResource(R.string.recipe_rename)) }
                    TextButton(onClick = onDuplicate) { Text(stringResource(R.string.recipe_duplicate)) }
                    TextButton(onClick = onFavorite) { Text(stringResource(if (preset.isFavorite) R.string.recipe_unfavorite else R.string.recipe_favorite)) }
                }
                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    val rawRenderingEngine = RawRenderingEngine.fromPersistedName(preset.rawRenderingEngine)
                    PresetFeatureText(preset.aspectRatio.removePrefix("RATIO_").replace("_", ":"))
                    if (rawRenderingEngine == RawRenderingEngine.Spektrafilm ||
                        rawRenderingEngine == RawRenderingEngine.Fotufilm
                    ) {
                        PresetFeatureText("FILM")
                    }
                    if (preset.lutId != null) PresetFeatureText("LUT")
                    if (rawRenderingEngine == RawRenderingEngine.AdobeCurve && preset.hasRawDcpSelection()) {
                        PresetFeatureText("DCP")
                    }
                }
            }

            IconButton(
                onClick = onExport,
                enabled = !isExporting,
                modifier = Modifier.size(38.dp),
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White.copy(alpha = 0.75f),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = AppIcons.Download,
                        contentDescription = stringResource(R.string.preset_export),
                        tint = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(38.dp)) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(R.string.edit),
                    tint = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(38.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun presetDisplayName(preset: CameraPreset): String {
    val original = CameraPreset.BUILT_IN_PRESETS.firstOrNull { it.id == preset.id }
    if (original == null || original.name != preset.name) return preset.name
    return when (preset.id) {
        "builtin_default" -> stringResource(R.string.default_text)
        "builtin_hasselblad_natural" -> stringResource(R.string.preset_builtin_hasselblad_natural)
        "builtin_portrait" -> stringResource(R.string.preset_builtin_portrait)
        "builtin_classic_film" -> stringResource(R.string.preset_builtin_classic_film)
        "builtin_monochrome" -> stringResource(R.string.preset_builtin_monochrome)
        "builtin_cinematic" -> stringResource(R.string.preset_builtin_cinematic)
        "builtin_leica_m9_moment" -> stringResource(R.string.preset_builtin_leica_m9_moment)
        else -> com.hinnka.mycamera.model.CameraInspiredRecipes
            .displayNameResource(preset.id)?.let { stringResource(it) } ?: preset.name
    }
}

@Composable
private fun PresetBadge(text: String, highlighted: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (highlighted) AccentColor.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.10f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = if (highlighted) AccentColor else Color.White.copy(alpha = 0.65f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun PresetFeatureText(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
