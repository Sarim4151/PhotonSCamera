package com.hinnka.mycamera.ui.camera

import com.hinnka.mycamera.ml.DepthModelManager
import com.hinnka.mycamera.ml.DepthModelDownloadState
import com.hinnka.mycamera.model.PreviewAssistSettings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.os.Environment
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import coil.compose.AsyncImage
import com.hinnka.mycamera.ui.theme.AccentColor
import com.hinnka.mycamera.ui.theme.accentContentColor
import com.hinnka.mycamera.MyCameraApplication
import com.hinnka.mycamera.R
import com.hinnka.mycamera.camera.AspectRatio
import com.hinnka.mycamera.camera.CameraState
import com.hinnka.mycamera.camera.FocusPointSource
import com.hinnka.mycamera.camera.PhotoCaptureProgress
import com.hinnka.mycamera.camera.PhotoQualitySignal
import com.hinnka.mycamera.camera.PhotoQualitySignals
import com.hinnka.mycamera.data.CaptureButtonStyle
import com.hinnka.mycamera.data.DevelopAnimationStyle
import com.hinnka.mycamera.model.CameraPreset
import com.hinnka.mycamera.model.ColorRecipeParams
import com.hinnka.mycamera.model.LutSelectorMode
import com.hinnka.mycamera.raw.SpectralFilmSelection
import com.hinnka.mycamera.ui.components.*
import com.hinnka.mycamera.utils.OrientationObserver
import com.hinnka.mycamera.viewmodel.CameraViewModel
import com.hinnka.mycamera.viewmodel.GalleryViewModel
import com.hinnka.mycamera.video.CaptureMode
import com.hinnka.mycamera.video.VideoAspectRatio
import com.hinnka.mycamera.video.VideoFpsPreset
import com.hinnka.mycamera.video.VideoLogProfile
import com.hinnka.mycamera.video.VideoResolutionPreset
import com.hinnka.mycamera.video.VideoStabilizationMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.*
import com.hinnka.mycamera.ui.icons.AppIcons

/**
 * 主相机界面
 */
enum class ActivePanel {
    NONE,
    SETTINGS,
    FILTERS,
    EDIT,
    PRESETS
}

private enum class CameraShootingMode {
    PROFESSIONAL,
    PHOTO,
    VIDEO
}

private const val InitialPreviewTransitionDelayMillis = 150L
private const val PreviewTransitionRevealDurationMillis = 800
private const val ZoomStopAnimationCompletionThreshold = 0.001f
private const val RawCaptureTapDebounceMillis = 1000L
private const val DefaultShutterSpeedNs = 1_000_000_000f / 60f
private const val DefaultIso = 100f
private const val DefaultAwbTemperature = 5000f
private const val DefaultFocusDistance = 0f
private val CameraTopBarBaseHeight = 56.dp
private val VideoTopBarBaseHeight = 80.dp
private val VideoTopBarLoweredOffset = 36.dp
private val XpanViewfinderInnerBorder = 4.dp
private val XpanSideControlReservedWidth = 56.dp

private fun formatVirtualApertureFStop(aperture: Float): String {
    val rounded = aperture.roundToInt()
    return if (aperture == rounded.toFloat()) rounded.toString() else aperture.toString()
}

@Composable
private fun cameraTopSafePadding(): Dp {
    val density = LocalDensity.current
    val topInset = with(density) {
        maxOf(
            WindowInsets.statusBars.getTop(this).toDp(),
            WindowInsets.displayCutout.getTop(this).toDp()
        )
    }
    // Toolbar content no longer includes the former 32dp status-bar spacer.
    return topInset
}

private fun Bitmap.copyForCaptureAnimation(): Bitmap? {
    if (isRecycled) return null
    val copyConfig = if (config == Bitmap.Config.HARDWARE) {
        Bitmap.Config.ARGB_8888
    } else {
        config ?: Bitmap.Config.ARGB_8888
    }
    return copy(copyConfig, false) ?: copy(Bitmap.Config.ARGB_8888, false)
}

private fun Bitmap.recycleIfAlive() {
    if (!isRecycled) {
        recycle()
    }
}

@Composable
private fun PanelDismissPreviewOverlay(
    bounds: Rect?,
    parentBounds: Rect?,
    dimBackground: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bounds = bounds ?: return
    if (bounds.width <= 0f || bounds.height <= 0f) return

    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val density = LocalDensity.current
    val parentLeft = parentBounds?.left ?: 0f
    val parentTop = parentBounds?.top ?: 0f
    val width = with(density) { bounds.width.toDp() }
    val height = with(density) { bounds.height.toDp() }

    Box(
        modifier = modifier
            .offset {
                IntOffset(
                    x = (bounds.left - parentLeft).roundToInt(),
                    y = (bounds.top - parentTop).roundToInt()
                )
            }
            .size(width = width, height = height)
            .then(
                if (dimBackground) {
                    Modifier.background(Color.Black.copy(alpha = 0.4f))
                } else {
                    Modifier
                }
            )
            .pointerInput(Unit) {
                detectTapGestures {
                    currentOnDismiss()
                }
            }
    )
}

private fun CameraParameter.defaultResetValue(): Float {
    return when (this) {
        CameraParameter.EXPOSURE_COMPENSATION -> 0f
        CameraParameter.SHUTTER_SPEED -> DefaultShutterSpeedNs
        CameraParameter.ISO -> DefaultIso
        CameraParameter.FOCUS -> DefaultFocusDistance
        CameraParameter.WHITE_BALANCE -> DefaultAwbTemperature
    }
}

@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    galleryViewModel: GalleryViewModel,
    onGalleryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onFilterManagementClick: (String?) -> Unit,
    onFrameManagementClick: () -> Unit,
    onToolboxClick: () -> Unit,
    onPresetEditClick: (String?) -> Unit,
    onPresetManagementClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsState()
    val intervalCaptureState by viewModel.intervalCaptureState.collectAsState()
    var showIntervalDialog by remember { mutableStateOf(false) }
    var showFocusBracketDialog by remember { mutableStateOf(false) }
    var showAdvancedCaptureDialog by remember { mutableStateOf(false) }
    var showGroupCaptureDialog by remember { mutableStateOf(false) }
    var showFullSourcePreCapture by remember { mutableStateOf(false) }
    var showPhotoSphere by remember { mutableStateOf(false) }
    var showSphereRequirements by remember { mutableStateOf(false) }
    var sphereSessionIdentity by remember { mutableStateOf<Triple<String, android.util.Size, Int>?>(null) }
    val privateCaptureActive = showGroupCaptureDialog || showFullSourcePreCapture || showPhotoSphere
    val displayRotation = androidx.compose.ui.platform.LocalView.current.display?.rotation ?: android.view.Surface.ROTATION_0
    val sphereInfo = state.getCurrentCameraInfo()
    val sphereSupported = sphereInfo?.lensFacing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK &&
        sphereInfo.lensType == com.hinnka.mycamera.camera.LensType.BACK_MAIN && !sphereInfo.isVirtualIszLens &&
        sphereInfo.focalLength35mmEquivalent.isFinite() && sphereInfo.focalLength35mmEquivalent > 0f &&
        kotlin.math.abs(state.zoomRatio-1f)<.01f && state.aspectRatio == com.hinnka.mycamera.camera.AspectRatio.RATIO_4_3

    val sphereSessionValid = sphereSupported && sphereSessionIdentity ==
        Triple(state.currentCameraId, state.currentCaptureSize, displayRotation)
    LaunchedEffect(showPhotoSphere, sphereSessionValid) {
        if (showPhotoSphere && !sphereSessionValid) {
            showPhotoSphere = false
            showSphereRequirements = true
        }
    }

    var groupCaptureAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showStyleDialog by remember { mutableStateOf(false) }
    var showQuickControlsDialog by remember { mutableStateOf(false) }
    val quickPreferences = remember(context) { ProControlsPreferences(context) }
    var quickSlots by remember { mutableStateOf(quickPreferences.loadSlots()) }
    var showViewfinderQuickControls by remember { mutableStateOf(quickPreferences.showInViewfinder()) }
    var viewfinderQuickControlsExpanded by remember { mutableStateOf(false) }
    var quickPresets by remember { mutableStateOf(quickPreferences.presets()) }
    fun saveQuickSlots(value: List<ProQuickControl>) {
        quickSlots = value.distinct().take(ProQuickControl.MAX_SLOTS)
        quickPreferences.saveSlots(quickSlots)
    }
    if (showQuickControlsDialog) QuickControlsDialog(
        slots = quickSlots, onSlotsChange = ::saveQuickSlots, presets = quickPresets,
        showInViewfinder = showViewfinderQuickControls,
        onShowInViewfinderChange = { enabled ->
            showViewfinderQuickControls = enabled
            quickPreferences.setShowInViewfinder(enabled)
            if (!enabled) viewfinderQuickControlsExpanded = false
        },
        onSavePreset = { quickPreferences.savePreset(it, quickSlots); quickPresets = quickPreferences.presets() },
        onLoadPreset = { quickPresets[it]?.let(::saveQuickSlots) },
        onDeletePreset = { quickPreferences.deletePreset(it); quickPresets = quickPreferences.presets() },
        onDismiss = { showQuickControlsDialog = false })
    val advancedCameraState by viewModel.advancedCameraState.collectAsState()
    val selectedAdvancedCapture by viewModel.selectedAdvancedCapture.collectAsState()
    if (showAdvancedCaptureDialog) {
        AdvancedCaptureDialog(state = advancedCameraState,
            selection = selectedAdvancedCapture ?: viewModel.advancedCaptureConfiguration(),
            canStart = !intervalCaptureState.isRunning && !viewModel.focusBracketState.value.isRunning &&
                state.captureMode == CaptureMode.PHOTO && state.isPreviewActive && !state.isCapturing &&
                state.countdownValue == 0 && !viewModel.multipleExposureState.enabled,
            onSelect = viewModel::selectAdvancedCapture, onRecover = viewModel::recoverAdvancedCapture,
            onStop = viewModel::stopAdvancedCapture, onDismiss = { showAdvancedCaptureDialog = false },
            onFullSourcePreCapture = {
                viewModel.clearAdvancedCaptureSelection();viewModel.setMotionPriorityEnabled(false)
                showAdvancedCaptureDialog=false;showGroupCaptureDialog=false;showPhotoSphere=false;showFullSourcePreCapture=true
            }, onPhotoSphere = {
                showAdvancedCaptureDialog=false
                if(sphereSupported) {
                    viewModel.clearAdvancedCaptureSelection();viewModel.setMotionPriorityEnabled(false)
                    sphereSessionIdentity = Triple(state.currentCameraId, state.currentCaptureSize, displayRotation)
                    showGroupCaptureDialog=false;showFullSourcePreCapture=false;showPhotoSphere=true
                } else showSphereRequirements=true
            })
    }
    if(showSphereRequirements) AlertDialog(onDismissRequest={showSphereRequirements=false},
        text={Text(stringResource(R.string.sphere_requirements))},
        confirmButton={TextButton(onClick={showSphereRequirements=false}){Text(stringResource(android.R.string.ok))}})
    val focusBracketState by viewModel.focusBracketState.collectAsState()
    if (showFocusBracketDialog) {
        FocusBracketDialog(
            state = focusBracketState, minimumFocusDistance = state.minimumFocusDistance,
            canStart = !advancedCameraState.running && !advancedCameraState.saving && !intervalCaptureState.isRunning && state.isPreviewActive &&
                state.captureMode == CaptureMode.PHOTO && !state.isCapturing && !state.isFocusLocked &&
                state.minimumFocusDistance.isFinite() && state.minimumFocusDistance > 0f &&
                !viewModel.multipleExposureState.enabled,
            onStart = viewModel::startFocusBracket, onStop = viewModel::stopFocusBracket,
            onDismiss = { showFocusBracketDialog = false },
        )
    }
    if (showIntervalDialog) {
        IntervalCaptureDialog(
            state = intervalCaptureState,
            canStart = !advancedCameraState.running && !advancedCameraState.saving && !focusBracketState.isRunning && state.captureMode == CaptureMode.PHOTO && state.isPreviewActive &&
                !state.isCapturing && state.countdownValue == 0 &&
                !state.videoRecordingState.isProcessing && !viewModel.multipleExposureState.enabled,
            onStart = viewModel::startIntervalCapture,
            onStop = viewModel::stopIntervalCapture,
            onDismiss = { showIntervalDialog = false },
            onFocusBracket = { showIntervalDialog = false; showFocusBracketDialog = true },
            onAdvancedCapture = { showIntervalDialog = false; showAdvancedCaptureDialog = true },
            onGroupCapture = { viewModel.clearAdvancedCaptureSelection(); showIntervalDialog = false; showGroupCaptureDialog = true },
            onPhotographicStyle = { showIntervalDialog = false; showStyleDialog = true },
        )
    }
    val isCameraInitialized by viewModel.isInitialized.collectAsState()
    val latestPhoto by galleryViewModel.latestPhoto.collectAsState()
    val showLevelIndicator by viewModel.showLevelIndicator.collectAsState(initial = false)
    val previewAssistSettings by viewModel.previewAssistSettings.collectAsState(initial = PreviewAssistSettings())
    val focusPeakingEnabled by viewModel.focusPeakingEnabled.collectAsState(initial = false)
    val eyeFocusEnabled by viewModel.eyeFocusEnabled.collectAsState()
    val subjectFocusEnabled by viewModel.subjectFocusEnabled.collectAsState()
    val subjectFocusLabel by viewModel.subjectFocusLabel.collectAsState()
    val motionPriorityEnabled by viewModel.motionPriorityEnabled.collectAsState()
    val keepScreenOn by viewModel.keepScreenOn.collectAsState(initial = false)
    val currentLutId by viewModel.currentLutId.collectAsState()
    val lutSwipeState = rememberLutSwipeState()
    val currentRecipeParams by viewModel.currentRecipeParams.collectAsState()
    val whiteBalanceIntent by viewModel.whiteBalanceIntent.collectAsState()
    if (showStyleDialog) com.hinnka.mycamera.ui.components.PhotographicStyleDialog(
        value = com.hinnka.mycamera.model.PhotographicStyle.fromRecipe(currentRecipeParams),
        onChange = viewModel::setPhotographicStyle,
        whiteBalanceIntent = whiteBalanceIntent,
        onWhiteBalanceIntentChange = viewModel::setWhiteBalanceIntent,
        onDismiss = { showStyleDialog = false })

    val lutSelectorMode by viewModel.lutSelectorMode.collectAsState()
    val activePresetId by viewModel.activePresetId.collectAsState()
    val activePresetModified by viewModel.isActivePresetModified.collectAsState()
    val customPresets by viewModel.customPresets.collectAsState()
    val currentBaselineRecipeParams by viewModel.currentBaselineRecipeParams.collectAsState()
    val categoryOrder by viewModel.categoryOrder.collectAsState(emptyList())
    val useRaw by viewModel.useRaw.collectAsState()
    val useJpgMax by viewModel.useJpgMax.collectAsState()
    val useMultipleExposure by viewModel.useMultipleExposure.collectAsState()
    val useRawMax by viewModel.useRawMax.collectAsState()
    val ultraHdrEnabled by viewModel.ultraHdrGainMapEnabled.collectAsState()
    val useLivePhoto by viewModel.useLivePhoto.collectAsState()
    val enableDevelopAnimation by viewModel.enableDevelopAnimation.collectAsState()
    val developAnimationStyle by viewModel.developAnimationStyle.collectAsState()
    val phantomMode by viewModel.phantomMode.collectAsState()
    val topSheetAspectRatios by viewModel.topSheetAspectRatios.collectAsState()
    val videoCodec by viewModel.videoCodec.collectAsState()
    val videoAudioInputOptions by viewModel.videoAudioInputOptions.collectAsState()
    val videoNoiseReductionStatus by viewModel.videoNoiseReductionStatus.collectAsState()
    val videoMonitoringStats by viewModel.videoMonitoringStats.collectAsState()
    val videoNoiseSuppressionAvailable = remember { com.hinnka.mycamera.video.DeviceVideoNoiseReduction.isAvailable() }
    val phantomPipPreview by viewModel.phantomPipPreview.collectAsState()
    val rawDcpId by viewModel.rawDcpId.collectAsState()
    val rawDcpIdsByLens by viewModel.rawDcpIdsByLens.collectAsState()
    val rawHncsFilmCurveMode by viewModel.rawHncsFilmCurveMode.collectAsState()
    val rawColorEngine by viewModel.rawRenderingEngine.collectAsState()
    val rawToneMappingParameters by viewModel.rawToneMappingParameters.collectAsState()
    val rawSpectralFilmStock by viewModel.rawSpectralFilmStock.collectAsState()
    val rawSpectralFilmSelection by viewModel.rawSpectralFilmSelection.collectAsState()
    val rawSpectralFilmPrint by viewModel.rawSpectralFilmPrint.collectAsState()
    val rawFotufilmDocument by viewModel.rawFotufilmDocument.collectAsState()
    val multipleExposureState = viewModel.multipleExposureState
    val canStartShutterAnimation by viewModel.canStartShutterAnimation.collectAsState()
    val currentCaptureModeForEffects by rememberUpdatedState(state.captureMode)
    var previewRecipeParamsOverride by remember(currentLutId, activePresetId) {
        mutableStateOf<ColorRecipeParams?>(null)
    }
    var pendingCaptureAnimationBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var captureAnimationJob by remember { mutableStateOf<Job?>(null) }
    var cameraScreenBounds by remember { mutableStateOf<Rect?>(null) }
    var previewBounds by remember { mutableStateOf<Rect?>(null) }
    var viewfinderAreaBounds by remember { mutableStateOf<Rect?>(null) }
    var zoomBarBounds by remember { mutableStateOf<Rect?>(null) }
    var filterButtonBounds by remember { mutableStateOf<Rect?>(null) }
    var captureButtonBounds by remember { mutableStateOf<Rect?>(null) }
    var parameterRulerBounds by remember { mutableStateOf<Rect?>(null) }
    var galleryThumbnailBounds by remember { mutableStateOf<Rect?>(null) }
    var captureAnimationSnapshot by remember { mutableStateOf<CaptureAnimationSnapshot?>(null) }
    var previewTransitionActive by remember { mutableStateOf(true) }
    var previewTransitionRevealing by remember { mutableStateOf(false) }
    var previewTransitionToken by remember { mutableIntStateOf(0) }
    var previewTransitionAwaitingResume by remember { mutableStateOf(false) }
    var previewTransitionSawPause by remember { mutableStateOf(false) }
    var hasPlayedInitialPreviewTransition by remember { mutableStateOf(false) }
    var rawCaptureTapLocked by remember { mutableStateOf(false) }
    var zoomStopAnimationJob by remember { mutableStateOf<Job?>(null) }
    val lensSwitchTransition = remember(viewModel, scope) { LensSwitchTransitionState(scope, viewModel) }

    DisposableEffect(lensSwitchTransition) {
        onDispose { lensSwitchTransition.cancel() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        lensSwitchTransition.cancel()
        viewModel.setLocationTrackingForeground(false)
    }

    fun discardTransientLookEdits() {
        previewRecipeParamsOverride = null
    }

    LaunchedEffect(currentRecipeParams) {
        if (previewRecipeParamsOverride?.isSameAs(currentRecipeParams) == true) {
            previewRecipeParamsOverride = null
        }
    }

    // Surface may become ready while persisted camera settings are still being restored.
    // Retain the exact instance and open only after initialization has completed.
    var previewSurfaceTexture by remember { mutableStateOf<SurfaceTexture?>(null) }
    var isCameraPrepared by remember { mutableStateOf(false) }

    LaunchedEffect(isCameraInitialized) {
        isCameraPrepared = isCameraInitialized && viewModel.prepareCamera()
    }

    LaunchedEffect(
        isCameraInitialized,
        isCameraPrepared,
        previewSurfaceTexture,
    ) {
        val surfaceTexture = previewSurfaceTexture ?: return@LaunchedEffect
        if (!isCameraInitialized || !isCameraPrepared) return@LaunchedEffect
        viewModel.openCamera(surfaceTexture)
    }

    // UI State
    var activePanel by remember { mutableStateOf(ActivePanel.NONE) }
    var selectedParameter by remember { mutableStateOf(CameraParameter.EXPOSURE_COMPENSATION) }
    var showParameterRuler by remember(state.captureMode, state.useRaw) { mutableStateOf(false) }
    var previewMagnification by remember(state.currentCameraId, state.captureMode) { mutableIntStateOf(1) }
    LaunchedEffect(selectedParameter, state.isAutoFocus, showParameterRuler) {
        if (selectedParameter != CameraParameter.FOCUS || state.isAutoFocus || !showParameterRuler) {
            previewMagnification = 1
        }
    }
    val isXpan = state.aspectRatio == AspectRatio.XPAN
    val burstCapturingCount = viewModel.burstImageCount

    var isGhostPermissionFlowActive by remember { mutableStateOf(false) }
    val activity = remember(context) { context.findActivity() }

    val ghostLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { _ ->
            // Results are handled via the ON_RESUME lifecycle effect to avoid self-reference issues
        }
    )

    val dcpImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                viewModel.importRawDcps(uris) { _, _ -> }
            }
        }
    )

    // 当打开滤镜面板时，生成预览图
    LaunchedEffect(activePanel) {
        if (activePanel == ActivePanel.FILTERS) {
            viewModel.generateThumbnail()
        }
    }

    LaunchedEffect(state.useRaw, state.captureMode) {
        if (!state.useRaw || state.captureMode != CaptureMode.PHOTO) {
            rawCaptureTapLocked = false
        }
    }

    // 从后台返回时检查并恢复相机，刷新最新照片
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.checkAndRecoverCamera()
        viewModel.setLocationTrackingForeground(true)
        galleryViewModel.refreshLatestPhoto()

        // Handle automated ghost mode permission sequence
        if (isGhostPermissionFlowActive) {
            val hasOverlay = Settings.canDrawOverlays(context)
            val hasFiles = Environment.isExternalStorageManager()

            if (hasOverlay && !hasFiles) {
                // Overlay granted, now request files
                ghostLauncher.launch(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        ("package:${context.packageName}").toUri()
                    )
                )
            } else if (hasOverlay) {
                // All permissions granted
                isGhostPermissionFlowActive = false
                if (!phantomMode) {
                    viewModel.togglePhantomMode()
                }
            } else {
                // If overlay is still missing after returning, user might have cancelled
                // We stop the automatic flow to avoid getting stuck
                isGhostPermissionFlowActive = false
            }
        }

        viewModel.updateLut()
    }

    DisposableEffect(Unit) {
        onDispose {
            pendingCaptureAnimationBitmap?.recycleIfAlive()
            pendingCaptureAnimationBitmap = null
            captureAnimationJob?.cancel()
        }
    }

    LaunchedEffect(enableDevelopAnimation, state.captureMode) {
        if (!enableDevelopAnimation || state.captureMode != CaptureMode.PHOTO) {
            pendingCaptureAnimationBitmap?.recycleIfAlive()
            pendingCaptureAnimationBitmap = null
            captureAnimationJob?.cancel()
            captureAnimationSnapshot = null
        }
    }

    // 监听照片保存完成事件，立即刷新缩略图
    LaunchedEffect(Unit) {
        viewModel.imageSavedEvent.collect {
            galleryViewModel.refreshLatestPhoto()
            MyCameraApplication.updateWidgets(context)
        }
    }

    // 显影在拍摄请求完成时开始，不等待图像处理或保存。
    LaunchedEffect(Unit) {
        viewModel.captureCompletedEvent.collect { metadata ->
            if (!enableDevelopAnimation || currentCaptureModeForEffects != CaptureMode.PHOTO) {
                pendingCaptureAnimationBitmap?.recycleIfAlive()
                pendingCaptureAnimationBitmap = null
                captureAnimationSnapshot = null
                return@collect
            }
            val rootOffset = cameraScreenBounds?.topLeft ?: androidx.compose.ui.geometry.Offset.Zero
            val sourceBounds = previewBounds?.translate(-rootOffset)
            val targetBounds = galleryThumbnailBounds?.translate(-rootOffset)
            val animationStyle = developAnimationStyle

            fun startCaptureAnimation(bitmap: Bitmap) {
                if (sourceBounds == null || targetBounds == null || !scope.isActive ||
                    !enableDevelopAnimation || currentCaptureModeForEffects != CaptureMode.PHOTO
                ) {
                    bitmap.recycleIfAlive()
                    return
                }
                captureAnimationJob?.cancel()
                captureAnimationJob = scope.launch {
                    var processedBitmap: Bitmap? = null
                    try {
                        processedBitmap = if (animationStyle == DevelopAnimationStyle.INSTANT_PRINT) {
                            viewModel.renderCaptureAnimationFrame(bitmap, metadata)
                        } else {
                            viewModel.applyLut(bitmap)
                        }
                        processedBitmap.copyForCaptureAnimation()?.let {
                            captureAnimationSnapshot = CaptureAnimationSnapshot(
                                bitmap = it.asImageBitmap(),
                                sourceBounds = sourceBounds,
                                targetBounds = targetBounds,
                                style = animationStyle
                            )
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        com.hinnka.mycamera.utils.PLog.e("CaptureAnimation", "Failed to prepare capture animation", e)
                    } finally {
                        processedBitmap?.recycleIfAlive()
                        if (processedBitmap !== bitmap) bitmap.recycleIfAlive()
                    }
                }
            }

            pendingCaptureAnimationBitmap?.let(::startCaptureAnimation)
                ?: viewModel.glSurfaceView?.capturePreviewFrame(::startCaptureAnimation)
            pendingCaptureAnimationBitmap = null
        }
    }

    val previewSize = state.currentPreviewSize
    val previewAspectRatio = state.getPreviewAspectRatio()
    val previewTransitionCoverFractionState = animateFloatAsState(
        targetValue = when {
            previewTransitionActive && !previewTransitionRevealing -> 1f
            previewTransitionActive -> 0f
            else -> 0f
        },
        animationSpec = if (previewTransitionActive && !previewTransitionRevealing) {
            snap()
        } else {
            tween(
                durationMillis = PreviewTransitionRevealDurationMillis,
                easing = FastOutSlowInEasing
            )
        },
        label = "previewTransitionCoverFraction"
    )
    val previewTransitionCoverFraction = previewTransitionCoverFractionState.value

    fun cancelZoomStopAnimation() {
        zoomStopAnimationJob?.cancel()
        zoomStopAnimationJob = null
    }

    fun animateCurrentLensToZoomStop(targetZoom: Float) {
        cancelZoomStopAnimation()
        val startZoom = viewModel.zoomRatioByMain
        if (abs(startZoom - targetZoom) <= ZoomStopAnimationCompletionThreshold) {
            viewModel.setZoomRatio(targetZoom)
            return
        }
        zoomStopAnimationJob = scope.launch {
            val progress = Animatable(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = resolveZoomStopAnimationDurationMillis(startZoom, targetZoom),
                    easing = FastOutSlowInEasing
                )
            ) {
                viewModel.setZoomRatio(
                    interpolateZoomRatio(
                        startZoom = startZoom,
                        targetZoom = targetZoom,
                        progress = value
                    )
                )
            }
            viewModel.setZoomRatio(targetZoom)
        }
    }

    fun runPreviewTransition(onSwitch: () -> Unit) {
        cancelZoomStopAnimation()
        lensSwitchTransition.cancel()
        previewTransitionActive = true
        previewTransitionRevealing = false
        previewTransitionToken += 1
        previewTransitionAwaitingResume = true
        previewTransitionSawPause = false
        scope.launch {
            withFrameNanos { }
            onSwitch()
        }
    }

    fun switchToLensWithPreviewTransition(cameraId: String) {
        lensSwitchTransition.cancelPendingSwitch()
        if (cameraId == state.getCurrentCameraInfo()?.cameraId) return
        val targetCamera = state.availableCameras.firstOrNull { it.cameraId == cameraId } ?: return
        val targetZoom = targetCamera.displayIntrinsicZoomRatio.takeIf { it > 0f }
            ?: targetCamera.intrinsicZoomRatio.takeIf { it > 0f }
            ?: 1f
        if (viewModel.isVideoLensLocked()) {
            animateCurrentLensToZoomStop(targetZoom)
            return
        }
        cancelZoomStopAnimation()
        lensSwitchTransition.switchLens(cameraId, targetZoom) { viewModel.switchToLens(cameraId) }
    }

    fun setZoomWithPreviewTransition(targetZoom: Float) {
        lensSwitchTransition.cancelPendingSwitch()
        cancelZoomStopAnimation()
        if (viewModel.isVideoLensLocked()) {
            viewModel.setZoomRatio(targetZoom)
            return
        }
        if (viewModel.isCurrentLensCustomZoomRatioStop(targetZoom)) {
            viewModel.setZoomRatio(targetZoom)
            return
        }
        val currentCamera = state.getCurrentCameraInfo()
        val currentCameraId = currentCamera?.cameraId ?: "0"
        val camera = viewModel.findOptimalLens(
            targetZoom,
            state.availableCameras,
            currentCameraId
        )
        if (camera != null && camera.cameraId != currentCamera?.cameraId) {
            lensSwitchTransition.switchLens(camera.cameraId, targetZoom) {
                viewModel.switchToLensAndSetZoomRatio(camera.cameraId, targetZoom)
            }
        } else {
            viewModel.setZoomRatio(targetZoom)
        }
    }

    fun animateZoomStopWithPreviewTransition(targetZoom: Float) {
        lensSwitchTransition.cancelPendingSwitch()
        cancelZoomStopAnimation()
        if (viewModel.isVideoLensLocked() ||
            viewModel.isCurrentLensCustomZoomRatioStop(targetZoom)
        ) {
            animateCurrentLensToZoomStop(targetZoom)
            return
        }
        val currentCamera = state.getCurrentCameraInfo()
        val currentCameraId = currentCamera?.cameraId ?: "0"
        val camera = viewModel.findOptimalLens(
            targetZoom,
            state.availableCameras,
            currentCameraId
        )
        if (camera != null && camera.cameraId != currentCamera?.cameraId) {
            lensSwitchTransition.switchLens(camera.cameraId, targetZoom) {
                viewModel.switchToLensAndSetZoomRatio(camera.cameraId, targetZoom)
            }
        } else {
            animateCurrentLensToZoomStop(targetZoom)
        }
    }

    fun switchCameraWithPreviewTransition() {
        runPreviewTransition { viewModel.switchCamera() }
    }

    fun setShootingModeWithPreviewTransition(mode: CameraShootingMode) {
        if (mode == CameraShootingMode.PROFESSIONAL && !state.isRawSupported) return

        val targetCaptureMode = when (mode) {
            CameraShootingMode.PROFESSIONAL,
            CameraShootingMode.PHOTO -> CaptureMode.PHOTO
            CameraShootingMode.VIDEO -> CaptureMode.VIDEO
        }
        val targetUseRaw = when (mode) {
            CameraShootingMode.PROFESSIONAL -> true
            CameraShootingMode.PHOTO -> false
            CameraShootingMode.VIDEO -> null
        }
        if (
            targetCaptureMode == state.captureMode &&
            (targetUseRaw == null || targetUseRaw == state.useRaw)
        ) {
            return
        }

        runPreviewTransition {
            if (targetCaptureMode == CaptureMode.VIDEO && state.aspectRatio == AspectRatio.XPAN) {
                viewModel.setAspectRatio(AspectRatio.RATIO_4_3)
            }
            viewModel.setShootingMode(targetCaptureMode, targetUseRaw)
        }
    }

    fun presetTargetAspectRatio(preset: CameraPreset?): AspectRatio {
        return try {
            AspectRatio.valueOf(preset?.aspectRatio ?: AspectRatio.RATIO_4_3.name)
        } catch (_: Exception) {
            AspectRatio.RATIO_4_3
        }
    }

    fun presetRequiresPreviewTransition(preset: CameraPreset?): Boolean {
        if (state.captureMode == CaptureMode.VIDEO) return false
        val targetAspectRatio = presetTargetAspectRatio(preset)
        return targetAspectRatio != state.aspectRatio
    }

    LaunchedEffect(previewTransitionToken, state.isPreviewActive, previewTransitionAwaitingResume) {
        if (!previewTransitionAwaitingResume) return@LaunchedEffect

        if (!state.isPreviewActive) {
            previewTransitionSawPause = true
            return@LaunchedEffect
        }

        if (previewTransitionSawPause) {
            delay(80)
            previewTransitionRevealing = true
            delay(220)
            previewTransitionActive = false
            previewTransitionAwaitingResume = false
            previewTransitionSawPause = false
        }
    }

    val shouldKeepScreenOn = keepScreenOn || state.videoRecordingState.isRecording || advancedCameraState.running || advancedCameraState.saving || focusBracketState.isRunning || intervalCaptureState.isRunning

    DisposableEffect(activity, shouldKeepScreenOn) {
        if (shouldKeepScreenOn) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(previewTransitionToken) {
        if (previewTransitionToken == 0) return@LaunchedEffect
        delay(700)
        if (previewTransitionAwaitingResume) {
            previewTransitionRevealing = true
            delay(220)
            previewTransitionActive = false
            previewTransitionAwaitingResume = false
            previewTransitionSawPause = false
        }
    }

    LaunchedEffect(
        state.isPreviewActive,
        state.captureMode,
        hasPlayedInitialPreviewTransition,
        canStartShutterAnimation
    ) {
        val canRevealInitialPreview =
            canStartShutterAnimation ||
                state.captureMode == CaptureMode.VIDEO
        if (hasPlayedInitialPreviewTransition || !state.isPreviewActive || !canRevealInitialPreview) return@LaunchedEffect
        previewTransitionActive = true
        delay(InitialPreviewTransitionDelayMillis)
        previewTransitionRevealing = true
        delay(PreviewTransitionRevealDurationMillis.toLong())
        previewTransitionActive = false
        hasPlayedInitialPreviewTransition = true
    }

    var showGhostPermissionDialog by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.showGhostPermissions) {
        if (viewModel.showGhostPermissions) {
            showGhostPermissionDialog = true
            viewModel.showGhostPermissions = false
        }
    }

    if (viewModel.showPaymentDialog) {
        PaymentDialog(
            onDismiss = { viewModel.showPaymentDialog = false },
            onPurchase = {
                if (activity != null) {
                    viewModel.purchase(activity)
                }
                viewModel.showPaymentDialog = false
            }
        )
    }

    if (viewModel.showEnhancedStabilizationUnavailableDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissEnhancedStabilizationUnavailableDialog,
            title = {
                Text(
                    text = stringResource(R.string.eis_plus_unavailable_title),
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.eis_plus_unavailable_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissEnhancedStabilizationUnavailableDialog) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }

    if (showGhostPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showGhostPermissionDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.ghost_mode_dialog_title),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.ghost_mode_dialog_description),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.ghost_mode_permissions_required),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.ghost_mode_overlay_permission),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.ghost_mode_file_permission),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showGhostPermissionDialog = false
                        isGhostPermissionFlowActive = true
                        if (!Settings.canDrawOverlays(context)) {
                            ghostLauncher.launch(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    ("package:${context.packageName}").toUri()
                                )
                            )
                        } else if (!Environment.isExternalStorageManager()) {
                            ghostLauncher.launch(
                                Intent(
                                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    ("package:${context.packageName}").toUri()
                                )
                            )
                        } else {
                            isGhostPermissionFlowActive = false
                            viewModel.togglePhantomMode()
                        }
                    }
                ) {
                    Text(stringResource(R.string.ghost_mode_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showGhostPermissionDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                cameraScreenBounds = coordinates.boundsInRoot()
            }
    ) {

        val backgroundPainter = rememberBackgroundPainter(viewModel)

        val isVideoMode = state.captureMode == CaptureMode.VIDEO
        val isPhotoStyleMode = state.captureMode != CaptureMode.VIDEO
        val onParameterClick: (CameraParameter) -> Unit = { parameter ->
            val wasSelected = selectedParameter == parameter
            selectedParameter = parameter
            showParameterRuler = !wasSelected || !showParameterRuler
        }
        LaunchedEffect(showParameterRuler) {
            if (!showParameterRuler) {
                parameterRulerBounds = null
            }
        }

        val density = LocalDensity.current
        val width = with(density) { constraints.maxWidth.toDp() }
        val height = with(density) { constraints.maxHeight.toDp() }
        val topSafePadding = cameraTopSafePadding()
        val topBarOffset = 0.dp
        val topBarBaseHeight = if (isVideoMode) {
            VideoTopBarBaseHeight
        } else {
            CameraTopBarBaseHeight
        }
        val topBarHeight = topBarBaseHeight + topSafePadding
        val topBarBottom = topBarHeight + topBarOffset - 4.dp
        val navigationBarHeight = with(density) {
            WindowInsets.navigationBars.getBottom(this).toDp()
        }
        val contentHeight = (height - navigationBarHeight).coerceAtLeast(0.dp)
        val viewfinderFramePadding = if (isXpan) XpanViewfinderInnerBorder * 2 else 0.dp
        val sideControlSpace = if (isXpan) XpanSideControlReservedWidth else 0.dp
        val maxCardWidth = (width - sideControlSpace * 2)
            .coerceAtLeast(viewfinderFramePadding)
        val maxCardHeight = if (isXpan) {
            (contentHeight - topBarBottom - CameraControlsLayoutDefaults.XpanViewfinderClearance)
                .coerceAtLeast(viewfinderFramePadding)
        } else {
            contentHeight
        }
        val maxViewfinderContentWidth = (maxCardWidth - viewfinderFramePadding)
            .coerceAtLeast(0.dp)
        val maxViewfinderContentHeight = (maxCardHeight - viewfinderFramePadding)
            .coerceAtLeast(0.dp)
        val viewfinderContentWidth = minOf(
            maxViewfinderContentWidth,
            maxViewfinderContentHeight * previewAspectRatio
        )
        val cardWidth = viewfinderContentWidth + viewfinderFramePadding
        val cardHeight = viewfinderContentWidth / previewAspectRatio + viewfinderFramePadding
        val xpanSideWidth = ((width - cardWidth) / 2).coerceAtLeast(0.dp)
        val placeViewfinderBelowTopBar = contentHeight - cardHeight >= topBarBottom
        // Balance small previews between the toolbar and collapsed controls. Reserving both
        // bars first prevents the downward shift from pushing parameters back into the image.
        val extraViewfinderTopSpacing = if (isXpan) {
            0.dp
        } else {
            ((contentHeight - topBarBottom - cardHeight -
                CameraControlsLayoutDefaults.CollapsedControlsHeight) / 2).coerceAtLeast(0.dp)
        }
        val viewfinderTop = if (placeViewfinderBelowTopBar) {
            topBarBottom + extraViewfinderTopSpacing
        } else {
            ((contentHeight - cardHeight) / 2).coerceAtLeast(0.dp)
        }
        val viewfinderTopOverlayPadding = (topBarBottom - viewfinderTop).coerceAtLeast(0.dp)

        val activateQuickControl: (ProQuickControl) -> Unit = { control -> when (control) {
            ProQuickControl.FLASH -> viewModel.toggleFlash()
            ProQuickControl.TIMER -> viewModel.toggleTimer()
            ProQuickControl.LIVE_PHOTO -> viewModel.setUseLivePhoto(!state.useLivePhoto)
            ProQuickControl.HISTOGRAM -> viewModel.saveShowHistogram(!viewModel.showHistogram)
            ProQuickControl.GRID -> viewModel.setShowGrid(!state.showGrid)
            ProQuickControl.LEVEL -> viewModel.setShowLevelIndicator(!showLevelIndicator)
            ProQuickControl.ZEBRA -> viewModel.setPreviewAssistSettings(previewAssistSettings.copy(zebraEnabled = !previewAssistSettings.zebraEnabled))
            ProQuickControl.PEAKING -> viewModel.setFocusPeakingEnabled(!focusPeakingEnabled)
            ProQuickControl.AF_LOCK -> viewModel.setFocusLocked(!state.isFocusLocked)
            ProQuickControl.INTERVAL -> if (!advancedCameraState.running && !advancedCameraState.saving) showIntervalDialog = true
            ProQuickControl.AE_LOCK -> viewModel.setExposureLocked(!state.isExposureLocked)
        } }
        val topBar = @Composable {
            CameraTopBar(
                quickSlots = quickSlots,
                quickActive = buildSet {
                    if (state.showGrid) add(ProQuickControl.GRID)
                    if (showLevelIndicator) add(ProQuickControl.LEVEL)
                    if (viewModel.showHistogram) add(ProQuickControl.HISTOGRAM)
                    if (focusPeakingEnabled) add(ProQuickControl.PEAKING)
                    if (previewAssistSettings.zebraEnabled) add(ProQuickControl.ZEBRA)
                    if (state.isFocusLocked) add(ProQuickControl.AF_LOCK)
                    if (state.isExposureLocked) add(ProQuickControl.AE_LOCK)
                },
                onConfigureQuickControls = { showQuickControlsDialog = true },
                onQuickControl = activateQuickControl,
                captureMode = state.captureMode,
                isRecording = state.videoRecordingState.isRecording,
                recordingElapsedMs = state.videoRecordingState.elapsedMs,
                flashMode = state.flashMode,
                onFlashToggle = {
                    viewModel.toggleFlash()
                },
                timerSeconds = state.timerSeconds,
                onTimerToggle = { viewModel.toggleTimer() },
                onIntervalClick = {
                    if (advancedCameraState.running || advancedCameraState.saving) showAdvancedCaptureDialog = true else if (focusBracketState.isRunning) showFocusBracketDialog = true else showIntervalDialog = true
                },
                intervalRunning = intervalCaptureState.isRunning || focusBracketState.isRunning || advancedCameraState.running || advancedCameraState.saving,
                showHistogram = viewModel.showHistogram,
                onHistogramToggle = {
                    viewModel.saveShowHistogram(!viewModel.showHistogram)
                },
                useLivePhoto = useLivePhoto,
                onLivePhotoToggle = { viewModel.setUseLivePhoto(!state.useLivePhoto) },
                videoConfig = state.videoConfig,
                videoCapabilities = state.videoCapabilities,
                onVideoTorchToggle = { viewModel.setVideoTorchEnabled(!state.videoConfig.torchEnabled) },
                onVideoStabilizationModeChange = viewModel::setVideoStabilizationMode,
                onVideoResolutionClick = {
                    cycleVideoResolution(state)?.let(viewModel::setVideoResolution)
                },
                onVideoFpsClick = {
                    cycleVideoFps(state)?.let(viewModel::setVideoFps)
                },
                onSettingsClick = {
                    activePanel = if (activePanel == ActivePanel.SETTINGS) ActivePanel.NONE else ActivePanel.SETTINGS
                },
                modifier = Modifier
                    .padding(top = topSafePadding)
                    .offset(y = topBarOffset)
            )
        }

        val zoomBar = @Composable {
            if (activePanel == ActivePanel.NONE && !isXpan) {
                ZoomControlBar(
                    viewModel = viewModel,
                    zoomRatio = viewModel.zoomRatioByMain,
                    availableCameras = state.availableCameras,
                    currentCameraId = state.getCurrentCameraInfo()?.cameraId ?: "0",
                    onZoomChange = { setZoomWithPreviewTransition(it) },
                    onZoomStopClick = { animateZoomStopWithPreviewTransition(it) },
                    onLensSwitch = { lensId -> switchToLensWithPreviewTransition(lensId) },
                    onFilterClick = {
                        activePanel = if (activePanel == ActivePanel.FILTERS) ActivePanel.NONE else ActivePanel.FILTERS
                    },
                    onFilterButtonBoundsChanged = { bounds ->
                        filterButtonBounds = bounds
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            zoomBarBounds = coordinates.boundsInRoot()
                        }
                )
            } else {
                SideEffect {
                    zoomBarBounds = null
                }
            }
        }

        val parameterRuler = @Composable { rulerModifier: Modifier ->
            val whiteBalanceCurrentValue = if (
                state.awbMode == android.hardware.camera2.CameraMetadata.CONTROL_AWB_MODE_AUTO
            ) {
                state.actualAwbTemperature?.toFloat() ?: state.awbTemperature.toFloat()
            } else {
                state.awbTemperature.toFloat()
            }
            ParameterRuler(
                parameter = selectedParameter,
                currentValue = when (selectedParameter) {
                    CameraParameter.EXPOSURE_COMPENSATION -> state.exposureCompensation * state.getExposureCompensationStep()
                    CameraParameter.SHUTTER_SPEED -> state.shutterSpeed.toFloat()
                    CameraParameter.ISO -> state.iso.toFloat()
                    CameraParameter.FOCUS -> state.focusDistance
                    CameraParameter.WHITE_BALANCE -> whiteBalanceCurrentValue
                },
                minValue = when (selectedParameter) {
                    CameraParameter.EXPOSURE_COMPENSATION -> state.getExposureCompensationRange().lower * state.getExposureCompensationStep()
                    CameraParameter.SHUTTER_SPEED -> state.getManualShutterSpeedRange().lower.toFloat()
                    CameraParameter.ISO -> state.getIsoRange().lower.toFloat()
                    CameraParameter.FOCUS -> 0f
                    CameraParameter.WHITE_BALANCE -> state.awbTemperatureMin.toFloat()
                },
                maxValue = when (selectedParameter) {
                    CameraParameter.EXPOSURE_COMPENSATION -> state.getExposureCompensationRange().upper * state.getExposureCompensationStep()
                    CameraParameter.SHUTTER_SPEED -> state.getManualShutterSpeedRange().upper.toFloat()
                    CameraParameter.ISO -> state.getIsoRange().upper.toFloat()
                    CameraParameter.FOCUS -> state.minimumFocusDistance
                    CameraParameter.WHITE_BALANCE -> state.awbTemperatureMax.toFloat()
                },
                isAdjustable = when (selectedParameter) {
                    CameraParameter.EXPOSURE_COMPENSATION -> state.isAutoExposure
                    CameraParameter.SHUTTER_SPEED, CameraParameter.ISO -> true
                    CameraParameter.FOCUS -> state.minimumFocusDistance > 0f
                    CameraParameter.WHITE_BALANCE -> state.canAdjustWhiteBalance
                },
                isAutoMode = when (selectedParameter) {
                    CameraParameter.SHUTTER_SPEED -> state.isShutterSpeedAuto
                    CameraParameter.ISO -> state.isIsoAuto
                    CameraParameter.FOCUS -> state.isAutoFocus
                    CameraParameter.WHITE_BALANCE ->
                        state.awbMode == android.hardware.camera2.CameraMetadata.CONTROL_AWB_MODE_AUTO
                    CameraParameter.EXPOSURE_COMPENSATION -> false
                },
                showAutoButton = when (selectedParameter) {
                    CameraParameter.SHUTTER_SPEED, CameraParameter.ISO, CameraParameter.WHITE_BALANCE, CameraParameter.FOCUS -> true
                    else -> false
                },
                isAutoModeToggleEnabled = when (selectedParameter) {
                    CameraParameter.WHITE_BALANCE -> state.canAdjustWhiteBalance
                    CameraParameter.FOCUS -> state.minimumFocusDistance > 0f
                    else -> true
                },
                valueStep = state.getExposureCompensationStep(),
                resetValue = selectedParameter.defaultResetValue(),
                showHyperfocalButton = selectedParameter == CameraParameter.FOCUS && state.minimumFocusDistance > 0f,
                hyperfocalEnabled = state.isHyperfocalFocusEnabled,
                hyperfocalDistanceMeters = state.hyperfocalDistanceMeters,
                onHyperfocalToggle = { enabled ->
                    viewModel.setHyperfocalFocusEnabled(enabled)
                },
                onSnapFocusSelected = { preset -> viewModel.setSnapFocusDistance(preset) },
                snapFocusEnabled = !state.isCapturing && !state.videoRecordingState.isProcessing,
                previewMagnification = previewMagnification,
                onMagnificationToggle = {
                    previewMagnification = when (previewMagnification) { 1 -> 2; 2 -> 4; else -> 1 }
                    if (state.isAutoFocus) viewModel.setAutoFocus(false)
                },
                onValueChange = { value ->
                    when (selectedParameter) {
                        CameraParameter.EXPOSURE_COMPENSATION -> viewModel.setExposureCompensation((value / state.getExposureCompensationStep()).roundToInt())
                        CameraParameter.SHUTTER_SPEED -> viewModel.setShutterSpeed(value.toLong())
                        CameraParameter.ISO -> viewModel.setIso(value.toInt())
                        CameraParameter.FOCUS -> viewModel.setFocusDistance(value)
                        CameraParameter.WHITE_BALANCE -> viewModel.setAwbTemperature(value.toInt())
                    }
                },
                onAutoModeToggle = {
                    when (selectedParameter) {
                        CameraParameter.SHUTTER_SPEED -> viewModel.setShutterSpeedAuto(!state.isShutterSpeedAuto)
                        CameraParameter.ISO -> viewModel.setIsoAuto(!state.isIsoAuto)
                        CameraParameter.WHITE_BALANCE -> {
                            if (state.canAdjustWhiteBalance) {
                                if (state.awbMode == android.hardware.camera2.CameraMetadata.CONTROL_AWB_MODE_AUTO) {
                                    viewModel.setAwbMode(android.hardware.camera2.CameraMetadata.CONTROL_AWB_MODE_OFF)
                                } else {
                                    viewModel.setAwbMode(android.hardware.camera2.CameraMetadata.CONTROL_AWB_MODE_AUTO)
                                }
                            }
                        }

                        CameraParameter.FOCUS -> viewModel.setAutoFocus(!state.isAutoFocus)
                        else -> {}
                    }
                },
                modifier = rulerModifier,
            )
        }

        val parameterBar = @Composable {
                activeParameter: CameraParameter?,
                onParameterClick: (CameraParameter) -> Unit ->
            CameraParameterBar(
                state = state,
                selectedParameter = activeParameter,
                onParameterClick = onParameterClick
            )
        }

        val viewfinder = @Composable {
            Card(
                modifier = Modifier

                    .width(cardWidth)
                    .height(cardHeight),
                shape = RoundedCornerShape(if (isXpan) XpanViewfinderInnerBorder else 0.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black)
            ) {
                Box(
                    modifier = Modifier
                        .padding(if (isXpan) XpanViewfinderInnerBorder else 0.dp)
                        .fillMaxSize()
                        .background(Color.Black)
                        .onGloballyPositioned { coordinates ->
                            previewBounds = coordinates.boundsInRoot()
                        }
                        .pointerInput(state.availableCameras, privateCaptureActive) {
                            if (privateCaptureActive) return@pointerInput
                            val switchThreshold = max(size.width * 0.25f, 96.dp.toPx())
                            val flingVelocity = 900.dp.toPx()
                            val minFlingDistance = 48.dp.toPx()
                            val touchSlop = viewConfiguration.touchSlop
                            val longPressTimeout = viewConfiguration.longPressTimeoutMillis
                            awaitEachGesture {
                                var gestureStartedInPreviewControl: Boolean? = null
                                var childConsumedDrag = false
                                // 横向滑动切换 LUT：先越过 touch slop 并确认以横向为主才进入拖动，
                                // 竖向/斜向手势与点按不会触发。
                                var swipeStart: Offset? = null
                                var swipeDownTime = 0L
                                var swipeDecided = false
                                var swipeTracking = false
                                val velocityTracker = VelocityTracker()
                                fun cancelSwipe() {
                                    if (swipeTracking) lutSwipeState.cancel()
                                    swipeTracking = false
                                    swipeDecided = true
                                }
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (gestureStartedInPreviewControl == null) {
                                        val firstPressedChange =
                                            event.changes.firstOrNull { it.pressed }
                                        val currentPreviewBounds = previewBounds
                                        gestureStartedInPreviewControl =
                                            if (firstPressedChange != null && currentPreviewBounds != null) {
                                                val positionInRoot =
                                                    currentPreviewBounds.topLeft + firstPressedChange.position
                                                zoomBarBounds?.contains(positionInRoot) == true ||
                                                    parameterRulerBounds?.contains(positionInRoot) == true
                                            } else {
                                                false
                                            }
                                    }
                                    if (gestureStartedInPreviewControl == true) {
                                        if (event.changes.all { !it.pressed }) {
                                            viewModel.isZooming = false
                                            break
                                        }
                                        continue
                                    }
                                    if (event.changes.size >= 2) {
                                        // Pinch to zoom
                                        childConsumedDrag = true
                                        cancelSwipe()
                                        viewModel.isZooming = true
                                        val zoom = event.calculateZoom()
                                        if (zoom != 1f) {
                                            val nextZoom =
                                                (viewModel.zoomRatioByMain * zoom).coerceIn(
                                                    viewModel.globalMinZoom,
                                                    viewModel.globalMaxZoom
                                                )
                                            setZoomWithPreviewTransition(nextZoom)
                                        }
                                        event.changes.forEach { it.consume() }
                                    } else if (event.changes.size == 1) {
                                        // Single finger -> horizontal drag for LUT switch
                                        val change = event.changes[0]
                                        if (change.isConsumed && change.position != change.previousPosition) {
                                            childConsumedDrag = true
                                            cancelSwipe()
                                        }
                                        if (change.pressed && !childConsumedDrag) {
                                            val start = swipeStart ?: change.position.also {
                                                swipeStart = it
                                                swipeDownTime = change.uptimeMillis
                                                velocityTracker.resetTracking()
                                            }
                                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                                            val dx = change.position.x - start.x
                                            val dy = change.position.y - start.y
                                            if (!swipeDecided && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                                                swipeDecided = true
                                                val previousLut = viewModel.peekPreviousLut()
                                                val nextLut = viewModel.peekNextLut()
                                                val horizontal = abs(dx) > abs(dy) * 1.5f
                                                // 按住超过长按时长后才移动属于长按锁焦，不再作为滑动切换
                                                val afterLongPress =
                                                    change.uptimeMillis - swipeDownTime >= longPressTimeout
                                                if (
                                                    horizontal &&
                                                    !afterLongPress &&
                                                    nextLut != null &&
                                                    nextLut.id != currentLutId
                                                ) {
                                                    swipeTracking = true
                                                    lutSwipeState.begin(
                                                        width = size.width,
                                                        threshold = switchThreshold,
                                                        previousName = previousLut?.getName(),
                                                        nextName = nextLut.getName(),
                                                    )
                                                }
                                            }
                                            if (swipeTracking) {
                                                // 消费事件，使预览上的点按对焦 / 长按锁焦随之取消
                                                change.consume()
                                                lutSwipeState.drag(sign(dx) * max(abs(dx) - touchSlop, 0f))
                                            }
                                        } else if (!change.pressed && swipeTracking) {
                                            change.consume()
                                            val direction = lutSwipeState.release(
                                                velocityX = velocityTracker.calculateVelocity().x,
                                                flingVelocity = flingVelocity,
                                                minFlingDistance = minFlingDistance,
                                            )
                                            swipeTracking = false
                                            val selectedLut = when {
                                                direction > 0 -> viewModel.switchToPreviousLut()
                                                direction < 0 -> viewModel.switchToNextLut()
                                                else -> null
                                            }
                                            if (selectedLut != null) {
                                                lutSwipeState.commit(direction, selectedLut.getName())
                                            } else {
                                                lutSwipeState.cancel()
                                            }
                                        }
                                    }

                                    if (event.changes.all { !it.pressed }) {
                                        cancelSwipe()
                                        viewModel.isZooming = false
                                        break
                                    }
                                }
                            }
                        },
                ) {
                    val currentCameraId = state.currentCameraId
                    val calibrationOffset by viewModel.getCameraOrientationOffset(currentCameraId)
                        .collectAsState(initial = 0)
                    val eyeFocusActive = (eyeFocusEnabled || subjectFocusEnabled) &&
                        (viewModel.isEyeFocusRuntimeAvailable || subjectFocusEnabled) &&
                        state.isAutoFocus &&
                        !state.isHyperfocalFocusEnabled &&
                        (state.focusPoint == null || state.focusPointSource == FocusPointSource.EYE)
                    val portraitMaskActive = state.captureMode == CaptureMode.PHOTO &&
                        state.useRaw &&
                        state.isRawSupported

                    // 相机准备完成后再创建 Surface，首次只打开最终选中的镜头。
                    if (isCameraPrepared) {
                        CameraPreviewGL(
                            aspectRatio = previewAspectRatio,
                            previewSize = previewSize,
                            captureSize = state.currentCaptureSize,
                            captureMode = state.captureMode,
                            sensorOrientation = state.getCurrentCameraInfo()?.sensorOrientation
                                ?: 0,
                            lensFacing = if (state.getCurrentCameraInfo()?.lensFacing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT) 0 else 1,
                            calibrationOffset = calibrationOffset,
                            baselineLut = viewModel.currentBaselineLutConfig,
                            currentLut = viewModel.currentLutConfig,
                            enginePreviewPlan = viewModel.currentEnginePreviewPlan,
                            baselineColorRecipeParams = currentBaselineRecipeParams,
                            colorRecipeParams = previewRecipeParamsOverride ?: currentRecipeParams,
                            focusPoint = state.focusPoint.takeIf { previewMagnification == 1 },
                            meteringPoint = state.meteringPoint.takeIf { previewMagnification == 1 },
                            canMoveMeteringPoint = previewMagnification == 1 && state.supportsPointMetering &&
                                !state.isCapturing && activePanel == ActivePanel.NONE,
                            onMeteringPointMove = viewModel::moveMeteringPoint,
                            onFocusPointMove = viewModel::moveFocusPoint,
                            onMeteringPointMerge = viewModel::mergeMeteringPoint,
                            focusPointSource = state.focusPointSource,
                            isFocusLocked = state.isFocusLocked,
                            isFocusing = state.isFocusing,
                            focusSuccess = state.focusSuccess,
                            meteringMode = state.effectiveMeteringMode,
                            onSurfaceTextureReady = { surfaceTexture ->
                                previewSurfaceTexture = surfaceTexture
                            },
                            onSurfaceDestroyed = { surfaceTexture ->
                                if (previewSurfaceTexture === surfaceTexture) {
                                    previewSurfaceTexture = null
                                }
                                viewModel.closeCamera(surfaceTexture)
                            },
                            onTap = { x, y, w, h ->
                                if (previewMagnification > 1) {
                                    previewMagnification = 1
                                } else if (state.isFocusLocked) {
                                    viewModel.unlockFocus()
                                } else if (activePanel != ActivePanel.NONE) {
                                    activePanel = ActivePanel.NONE
                                } else {
                                    viewModel.focusOnPoint(x, y, w, h)
                                }
                            },
                            onLongPress = { x, y, w, h ->
                                if (previewMagnification > 1) {
                                    previewMagnification = 1
                                } else if (activePanel != ActivePanel.NONE) {
                                    activePanel = ActivePanel.NONE
                                } else {
                                    viewModel.lockFocusOnPoint(x, y, w, h)
                                }
                            },
                            onHistogramUpdated = { viewModel.handleHistogramUpdate(it) },
                            onMeteringUpdated = { w, l -> viewModel.handleMeteringUpdate(w, l) },
                            onHighlightPointUpdated = { hx, hy ->
                                viewModel.handleHighlightPointUpdate(
                                    hx,
                                    hy
                                )
                            },
                            onEyeFocusInputAvailable = if (eyeFocusActive || portraitMaskActive || motionPriorityEnabled ||
                                selectedAdvancedCapture?.mode == com.hinnka.mycamera.camera.AdvancedCameraMode.SMART) {
                                viewModel::handleEyeFocusInputUpdate
                            } else {
                                null
                            },
                            onFirstPreviewFrame = viewModel::onFirstPreviewFrame,
                            onGLSurfaceViewReady = {
                                viewModel.glSurfaceView = it
                            },
                            livePhotoRecorder = viewModel.livePhotoRecorder,
                            videoLogProfile = state.videoConfig.logProfile,
                            isEyeFocusBusy = viewModel.isEyeFocusBusy,
                            isAutoFocus = state.isAutoFocus,
                            focusPeakingEnabled = focusPeakingEnabled,
                            previewAssistSettings = previewAssistSettings.copy(falseColorEnabled = state.captureMode == CaptureMode.VIDEO && previewAssistSettings.falseColorEnabled),
                            previewMagnification = previewMagnification,
                            stabilizationCoordinator = viewModel.realtimeStabilizationCoordinator,
                            stabilizationInputSize = if (
                                state.captureMode == CaptureMode.VIDEO &&
                                state.videoConfig.stabilizationMode ==
                                    VideoStabilizationMode.ENHANCED
                            ) {
                                state.videoCapabilities
                                    .enhancedStabilizationInputSizesByResolution[
                                        state.videoConfig.resolution
                                    ] ?: previewSize
                            } else {
                                previewSize
                            },
                            photoPreviewStabilizationEnabled =
                                state.photoPreviewStabilizationEnabled,
                            videoPreviewStabilizationEnabled =
                                state.videoConfig.stabilizationMode ==
                                    VideoStabilizationMode.ENHANCED,
                            videoPreviewStabilizationStrength =
                                state.videoConfig.enhancedStabilizationStrength,
                            videoPreviewStabilizationLookahead =
                                state.videoConfig.enhancedStabilizationLookahead,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = if (previewTransitionActive || previewTransitionCoverFractionState.value > 0.001f) 1f else 0f
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.5f)
                                .align(Alignment.TopCenter)
                                .graphicsLayer {
                                    translationY = -size.height * (1f - previewTransitionCoverFractionState.value)
                                }
                                .background(Color.Black)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.5f)
                                .align(Alignment.BottomCenter)
                                .graphicsLayer {
                                    translationY = size.height * (1f - previewTransitionCoverFractionState.value)
                                }
                                .background(Color.Black)
                        )
                    }

                    LensSwitchTransitionOverlay(lensSwitchTransition, Modifier.fillMaxSize())

                    if (showViewfinderQuickControls && !privateCaptureActive &&
                        !state.videoRecordingState.isRecording && selectedAdvancedCapture == null) {
                        Column(
                            Modifier.align(Alignment.TopStart)
                                .padding(top = viewfinderTopOverlayPadding + 8.dp, start = 8.dp)
                                .background(Color.Black.copy(alpha = .72f), RoundedCornerShape(14.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            TextButton(onClick = { viewfinderQuickControlsExpanded = !viewfinderQuickControlsExpanded }) {
                                Text(stringResource(R.string.quick_viewfinder_toggle), color = Color.White, fontSize = 12.sp)
                            }
                            if (viewfinderQuickControlsExpanded) {
                                quickSlots.forEach { control ->
                                    TextButton(onClick = {
                                        activateQuickControl(control)
                                        viewfinderQuickControlsExpanded = false
                                    }) {
                                        Text(stringResource(control.label), color = Color.White, fontSize = 12.sp)
                                    }
                                }
                                TextButton(onClick = {
                                    viewfinderQuickControlsExpanded = false
                                    showQuickControlsDialog = true
                                }) {
                                    Text(stringResource(R.string.quick_controls_title), color = AccentColor, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    val showLivePhotoIndicator =
                        state.captureMode == CaptureMode.PHOTO && useLivePhoto
                    val showVirtualApertureIndicator =
                        state.captureMode != CaptureMode.VIDEO &&
                            state.isVirtualApertureEnabled
                    if (showLivePhotoIndicator || showVirtualApertureIndicator) {
                        Column(
                            modifier = Modifier
                                .padding(
                                    top = viewfinderTopOverlayPadding + 8.dp,
                                    end = 12.dp
                                )
                                .align(Alignment.TopEnd),
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (showLivePhotoIndicator) {
                                Surface(
                                    color = Color.Black.copy(alpha = 0.8f),
                                    shape = CircleShape
                                ) {
                                    Icon(
                                        painterResource(R.drawable.ic_live_photo),
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier
                                            .padding(4.dp)
                                            .size(18.dp)
                                    )
                                }
                            }
                            if (showVirtualApertureIndicator) {
                                Surface(
                                    color = Color.Black.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = stringResource(
                                            R.string.virtual_aperture_viewfinder_hint,
                                            formatVirtualApertureFStop(state.virtualAperture)
                                        ),
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Medium,
                                        style = TextStyle(shadow = ViewfinderTextShadow),
                                        modifier = Modifier.padding(
                                            horizontal = 6.dp,
                                            vertical = 3.dp
                                        )
                                    )
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        multipleExposureState.previewBitmap?.let { previewBitmap ->
                            Image(
                                bitmap = previewBitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .autoRotate(matchParentSize = true),
                                alpha = 0.45f,
                                contentScale = ContentScale.Crop
                            )
                        }

                        val advancedDisplayMode = if (advancedCameraState.running || advancedCameraState.saving)
                            advancedCameraState.mode else selectedAdvancedCapture?.mode
                        if (state.captureMode == CaptureMode.PHOTO && advancedDisplayMode != null && !privateCaptureActive) {
                            Column(Modifier.align(Alignment.TopEnd)
                                .padding(top = viewfinderTopOverlayPadding + 8.dp, end = 8.dp)
                                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                                .padding(8.dp), horizontalAlignment = Alignment.End) {
                                Text(stringResource(advancedModeLabel(advancedDisplayMode)), color = Color.White)
                                if (advancedDisplayMode !in listOf(com.hinnka.mycamera.camera.AdvancedCameraMode.PRE_CAPTURE,
                                    com.hinnka.mycamera.camera.AdvancedCameraMode.PREVIEW_COMPOSITE,
                                    com.hinnka.mycamera.camera.AdvancedCameraMode.PREVIEW_ND)) {
                                    Text(stringResource(R.string.advanced_source_dimensions, state.currentCaptureSize.width,
                                        state.currentCaptureSize.height, state.currentCaptureSize.width.toDouble() * state.currentCaptureSize.height / 1_000_000.0), color = Color.White)
                                }
                                if (advancedDisplayMode == com.hinnka.mycamera.camera.AdvancedCameraMode.HIGH_DETAIL) {
                                    selectedAdvancedCapture?.options?.detailInputMaxEdge?.let { edge ->
                                        Text(if (edge == 0) stringResource(R.string.advanced_detail_full) else
                                            stringResource(R.string.advanced_detail_working_edge, edge, edge * 2), color = Color.White)
                                    }
                                }
                                Text(stringResource(if (advancedCameraState.saving) R.string.advanced_viewfinder_saving
                                    else if (advancedCameraState.running) R.string.advanced_viewfinder_stop
                                    else R.string.advanced_viewfinder_start), color = Color.White)
                                if (advancedCameraState.running || advancedCameraState.saving) {
                                    Text(stringResource(R.string.advanced_progress, advancedCameraState.frames,
                                        advancedCameraState.elapsedSeconds), color = Color.White)
                                    advancedCameraState.preview?.let { preview ->
                                        androidx.compose.foundation.Image(preview.asImageBitmap(),
                                            contentDescription = stringResource(R.string.advanced_preview),
                                            modifier = Modifier.size(120.dp, 90.dp))
                                    }
                                    if (advancedCameraState.status.isNotBlank()) Text(advancedCameraState.status, color = Color.White)
                                } else {
                                    if (advancedCameraState.mode == advancedDisplayMode && advancedCameraState.status.isNotBlank()) {
                                        Text(advancedCameraState.status, color = Color.White)
                                    }
                                    TextButton(onClick = viewModel::clearAdvancedCaptureSelection) {
                                        Text(stringResource(R.string.advanced_normal_photo))
                                    }
                                }
                            }
                        }

                        if (subjectFocusEnabled && !subjectFocusLabel.isNullOrBlank()) {
                            Text(subjectFocusLabel.orEmpty(), modifier = Modifier.align(Alignment.TopStart)
                                .padding(12.dp).background(Color.Black.copy(alpha = .55f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp), color = CameraControlGold, fontSize = 12.sp)
                        }
                        if (selectedAdvancedCapture?.mode == com.hinnka.mycamera.camera.AdvancedCameraMode.ASTRO) {
                            selectedAdvancedCapture?.options?.astroHorizon?.let { horizon ->
                                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                                    drawLine(Color.Yellow.copy(alpha = .8f),
                                        androidx.compose.ui.geometry.Offset(0f, size.height * horizon),
                                        androidx.compose.ui.geometry.Offset(size.width, size.height * horizon), strokeWidth = 2.dp.toPx())
                                }
                            }
                        }
                        PoseInstructorPanel(
                            active = state.captureMode == CaptureMode.PHOTO && state.isPreviewActive && !privateCaptureActive &&
                                selectedAdvancedCapture == null &&
                                !state.isCapturing && !advancedCameraState.running && !advancedCameraState.saving &&
                                !previewTransitionActive && !lensSwitchTransition.isActive,
                            cameraKey = state.currentCameraId,
                            magnification = previewMagnification,
                            topPadding = viewfinderTopOverlayPadding,
                            surface = { viewModel.glSurfaceView },
                        )

                        // 实时直方图 (Overlaid on preview if enabled)
                        if ((isPhotoStyleMode || state.captureMode == CaptureMode.VIDEO) && state.histogram != null && viewModel.showHistogram) {
                            HistogramView(
                                histogram = state.histogram,
                                modifier = Modifier
                                    .padding(
                                        start = 8.dp,
                                        top = viewfinderTopOverlayPadding + 8.dp
                                    )
                                    .size(80.dp, 40.dp)
                                    .align(Alignment.TopStart)
                                    .autoRotate(dx = -20.dp, dy = 20.dp)
                            )
                        }

                        // A compact preview-referred warning. RAW may retain highlight detail
                        // beyond the display preview, so the wording says "preview" explicitly.
                        if (state.captureMode == CaptureMode.PHOTO && state.isPreviewActive &&
                            !state.isCapturing && selectedAdvancedCapture == null && !privateCaptureActive
                        ) {
                            val qualitySignal = PhotoQualitySignals.evaluate(state)
                            if (qualitySignal != null) {
                                Text(
                                    text = stringResource(when (qualitySignal) {
                                        PhotoQualitySignal.SOURCE_RESOLUTION_LIMITED -> R.string.photo_quality_source_limited
                                        PhotoQualitySignal.PREVIEW_HIGHLIGHTS_CLIPPING -> R.string.photo_quality_preview_clipping
                                    }),
                                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
                                        .background(Color.Black.copy(alpha = .68f), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    color = Color.White, fontSize = 11.sp
                                )
                            }
                        }

                        if (state.captureMode == CaptureMode.VIDEO && state.videoRecordingState.isRecording && !state.videoRecordingState.isPaused) {
                            Text(stringResource(R.string.video_monitor_stats,videoMonitoringStats.inputFps,videoMonitoringStats.submittedFps,
                                videoMonitoringStats.totalQueueReplacements,videoMonitoringStats.renderMs),
                                modifier=Modifier.align(Alignment.TopEnd).padding(top=viewfinderTopOverlayPadding+8.dp,end=8.dp)
                                    .background(Color.Black.copy(alpha=.6f),RoundedCornerShape(8.dp)).padding(8.dp),
                                color=Color.White,fontSize=10.sp)
                        }
                        if (state.captureMode == CaptureMode.VIDEO && previewAssistSettings.falseColorEnabled) {
                            Text(stringResource(R.string.video_false_colour_legend),
                                modifier=Modifier.align(Alignment.BottomCenter).padding(bottom=12.dp)
                                    .background(Color.Black.copy(alpha=.65f),RoundedCornerShape(8.dp)).padding(8.dp),
                                color=Color.White,fontSize=10.sp)
                        }

                        // 网格线覆盖
                        if (state.showGrid) {
                            GridOverlay(
                                aspectRatio = previewAspectRatio,
                                style = state.gridStyle,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // 水平仪覆盖
                        if (showLevelIndicator) {
                            LevelIndicatorOverlay(
                                aspectRatio = previewAspectRatio,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // 倒计时覆盖
                        if (state.countdownValue > 0) {
                            CountdownOverlay(
                                countdownValue = state.countdownValue,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        if (state.burstCapturing && burstCapturingCount > 0) {
                            BurstCaptureOverlay(
                                count = burstCapturingCount,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = viewfinderTopOverlayPadding)
                            )
                        }

                        if (useMultipleExposure && state.captureMode == CaptureMode.PHOTO) {
                            MultipleExposureOverlay(
                                state = multipleExposureState,
                                onFinish = { viewModel.finishMultipleExposureSession() },
                                onUndo = { viewModel.undoLastMultipleExposureFrame() },
                                onCancel = { viewModel.cancelMultipleExposureSession() },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = viewfinderTopOverlayPadding)
                            )
                        }

                        if(showFullSourcePreCapture) FullSourcePreCaptureOverlay(
                            captureFrame=viewModel::captureGuidedGroupFrame,photoProcessor=viewModel.photoProcessor,
                            topControlsInset=viewfinderTopOverlayPadding,
                            bottomControlsInset=(viewfinderTop+cardHeight-(contentHeight-CameraControlsLayoutDefaults.CaptureAreaHeight-CameraControlsLayoutDefaults.CaptureClearance)).coerceAtLeast(0.dp),
                            onCaptureActionChanged={groupCaptureAction=it},onSessionClosed=viewModel::discardGuidedGroupSession,
                            onSaved={showFullSourcePreCapture=false;viewModel.capturedThumbnail.value?.let{viewModel.acknowledgeCapturedThumbnail(it.captureId)};galleryViewModel.refreshLatestPhoto();MyCameraApplication.updateWidgets(context)},
                            onDismiss={showFullSourcePreCapture=false})
                        if(showPhotoSphere && sphereSessionValid && sphereInfo != null) PhotoSphereOverlay(
                            captureFrame=viewModel::captureGuidedGroupFrame,photoProcessor=viewModel.photoProcessor,
                            captureSize=state.currentCaptureSize,focalLength35mmEquivalent=sphereInfo.focalLength35mmEquivalent,displayRotation=displayRotation,
                            topControlsInset=viewfinderTopOverlayPadding,
                            bottomControlsInset=(viewfinderTop+cardHeight-(contentHeight-CameraControlsLayoutDefaults.CaptureAreaHeight-CameraControlsLayoutDefaults.CaptureClearance)).coerceAtLeast(0.dp),
                            onCaptureActionChanged={groupCaptureAction=it},onSessionClosed=viewModel::discardGuidedGroupSession,
                            onSaved={showPhotoSphere=false;viewModel.capturedThumbnail.value?.let{viewModel.acknowledgeCapturedThumbnail(it.captureId)};galleryViewModel.refreshLatestPhoto();MyCameraApplication.updateWidgets(context)},
                            onDismiss={showPhotoSphere=false})
                        if (showGroupCaptureDialog) GroupCaptureOverlay(
                            captureFrame = viewModel::captureGuidedGroupFrame,
                            onSessionClosed = viewModel::discardGuidedGroupSession,
                            photoProcessor = viewModel.photoProcessor,
                            topControlsInset = viewfinderTopOverlayPadding,
                            bottomControlsInset = (viewfinderTop + cardHeight -
                                (contentHeight - CameraControlsLayoutDefaults.CaptureAreaHeight -
                                    CameraControlsLayoutDefaults.CaptureClearance)).coerceAtLeast(0.dp),
                            onCaptureActionChanged = { groupCaptureAction = it },
                            onSaved = {
                                showGroupCaptureDialog = false
                                // The last source shot must not cover the new composite thumbnail.
                                viewModel.capturedThumbnail.value?.let { viewModel.acknowledgeCapturedThumbnail(it.captureId) }
                                galleryViewModel.refreshLatestPhoto()
                                MyCameraApplication.updateWidgets(context)
                            },
                            onDismiss = { showGroupCaptureDialog = false },
                        )
                    }

                    if (isXpan) {
                        CornerOverlay(
                            modifier = Modifier.fillMaxSize(),
                            radius = XpanViewfinderInnerBorder,
                            color = Color.Black
                        )
                    }

                    LutSwipeIndicator(state = lutSwipeState)
                }
            }

            if (isXpan) {
                // Center both side bars in the space above the capture controls, which
                // overlap the lower part of the XPAN viewfinder.
                val sideControlsBottom = minOf(
                    viewfinderTop + cardHeight,
                    contentHeight - CameraControlsLayoutDefaults.CaptureAreaHeight -
                        CameraControlsLayoutDefaults.CaptureClearance
                )
                val sideControlsHeight = (sideControlsBottom - viewfinderTop)
                    .coerceIn(0.dp, cardHeight)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(cardHeight)
                        .padding(bottom = cardHeight - sideControlsHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .width(xpanSideWidth),
                        contentAlignment = Alignment.Center
                    ) {
                        ZoomControlBarVerticel(
                            viewModel = viewModel,
                            zoomRatio = viewModel.zoomRatioByMain,
                            availableCameras = state.availableCameras,
                            currentCameraId = state.getCurrentCameraInfo()?.cameraId ?: "0",
                            onZoomChange = { setZoomWithPreviewTransition(it) },
                            onZoomStopClick = { animateZoomStopWithPreviewTransition(it) },
                            onLensSwitch = { lensId -> switchToLensWithPreviewTransition(lensId) },
                            onFilterClick = {
                                activePanel =
                                    if (activePanel == ActivePanel.FILTERS) ActivePanel.NONE else ActivePanel.FILTERS
                            },
                            onFilterButtonBoundsChanged = { bounds ->
                                filterButtonBounds = bounds
                            },
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(xpanSideWidth),
                        contentAlignment = Alignment.Center
                    ) {
                        CameraParameterBarVerticel(
                            state = state,
                            selectedParameter = selectedParameter.takeIf {
                                showParameterRuler
                            },
                            onParameterClick = onParameterClick,
                        )
                    }
                }
            }
        }


        val controls = @Composable { modifier: Modifier ->
            Controls(
                state = state,
                viewModel = viewModel,
                galleryViewModel = galleryViewModel,
                latestPhoto = latestPhoto,
                useMultipleExposure = useMultipleExposure,
                multipleExposureState = multipleExposureState,
                advancedCaptureActive = advancedCameraState.running || advancedCameraState.saving,
                advancedCaptureSelected = selectedAdvancedCapture != null || privateCaptureActive,
                onGalleryThumbnailBoundsChanged = { bounds ->
                    galleryThumbnailBounds = bounds
                },
                onCaptureButtonBoundsChanged = { bounds ->
                    captureButtonBounds = bounds
                },
                onSwitchCameraClick = { if (!privateCaptureActive) switchCameraWithPreviewTransition() },
                onCaptureModeSelected = ::setShootingModeWithPreviewTransition,
                modeSwitchEnabled = !privateCaptureActive && !previewTransitionActive && !lensSwitchTransition.isActive,
                onCaptureTap = {
                    if (privateCaptureActive) { groupCaptureAction?.invoke(); return@Controls }
                    if (state.captureMode == CaptureMode.PHOTO && viewModel.toggleAdvancedCaptureFromShutter()) return@Controls
                    val shouldDebounceRawCapture =
                        state.useRaw && state.captureMode == CaptureMode.PHOTO
                    if (shouldDebounceRawCapture) {
                        if (rawCaptureTapLocked) {
                            return@Controls
                        }
                        rawCaptureTapLocked = true
                        scope.launch {
                            delay(RawCaptureTapDebounceMillis)
                            rawCaptureTapLocked = false
                        }
                    }

                    if (enableDevelopAnimation && state.captureMode == CaptureMode.PHOTO) {
                        viewModel.glSurfaceView?.capturePreviewFrame(maxLongEdge = 1440) { bitmap ->
                            if (scope.isActive && enableDevelopAnimation &&
                                currentCaptureModeForEffects == CaptureMode.PHOTO
                            ) {
                                pendingCaptureAnimationBitmap?.recycleIfAlive()
                                pendingCaptureAnimationBitmap = bitmap
                            } else {
                                bitmap.recycleIfAlive()
                            }
                        }
                    }
                    viewModel.capture()
                },
                onGalleryClick = {
                    onGalleryClick()
                },
                sideControlInset = if (isXpan) {
                    (xpanSideWidth - CameraControlsLayoutDefaults.SideButtonSize) / 2
                } else {
                    null
                },
                modifier = modifier
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .paint(backgroundPainter, contentScale = ContentScale.Crop)
                .navigationBarsPadding(),
        ) {
            // The viewfinder is always the bottom layer. Its selected aspect ratio is fitted
            // to the full available area before camera controls are overlaid.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cardHeight)
                    .align(Alignment.TopCenter)
                    .offset(y = viewfinderTop)
                    .onGloballyPositioned { coordinates ->
                        viewfinderAreaBounds = coordinates.boundsInRoot()
                    },
                contentAlignment = Alignment.Center
            ) {
                viewfinder()
            }

            topBar()

            CameraBottomControlsLayout(
                viewfinderBottom = viewfinderTop + cardHeight,
                modifier = Modifier.fillMaxSize(),
                zoomBar = {
                    // Panel visibility changes the zoom content, not the space used to
                    // position the parameter row beneath it. XPAN has no horizontal bar.
                    Box(
                        modifier = if (isXpan) Modifier else Modifier
                            .fillMaxWidth()
                            .height(CameraControlsLayoutDefaults.ZoomBarHeight)
                    ) {
                        zoomBar()
                    }
                },
                parameterRuler = {
                    AnimatedVisibility(
                        visible = showParameterRuler,
                        enter = expandVertically(
                            animationSpec = tween(220, easing = FastOutSlowInEasing),
                            expandFrom = Alignment.Bottom
                        ) + fadeIn(tween(160)),
                        exit = shrinkVertically(
                            animationSpec = tween(180, easing = FastOutSlowInEasing),
                            shrinkTowards = Alignment.Bottom
                        ) + fadeOut(tween(120))
                    ) {
                        parameterRuler(
                            Modifier
                                .then(if (isXpan) Modifier.width(viewfinderContentWidth) else Modifier)
                                .onGloballyPositioned { coordinates ->
                                    parameterRulerBounds = coordinates.boundsInRoot()
                                }
                        )
                    }
                },
                parameterBar = {
                    if (!isXpan) {
                        parameterBar(selectedParameter.takeIf { showParameterRuler }, onParameterClick)
                    }
                },
                captureControls = {
                    controls(Modifier.fillMaxWidth())
                }
            )
        }
        val panelDismissBounds = if (isXpan) {
            viewfinderAreaBounds ?: previewBounds
        } else {
            previewBounds
        }

        if (activePanel != ActivePanel.NONE) {
            PanelDismissPreviewOverlay(
                bounds = panelDismissBounds,
                parentBounds = cameraScreenBounds,
                dimBackground = activePanel == ActivePanel.SETTINGS,
                onDismiss = { activePanel = ActivePanel.NONE }
            )
        }

        // TopSheet for settings
        val videoEngineEnabled by viewModel.videoEngineEnabled.collectAsState()
        CameraTopSheet(
            visible = activePanel == ActivePanel.SETTINGS,
            captureMode = state.captureMode,
            aspectRatio = state.aspectRatio,
            topSheetAspectRatios = topSheetAspectRatios,
            onAspectRatioChange = { runPreviewTransition { viewModel.setAspectRatio(it) } },
            videoAspectRatio = state.videoConfig.aspectRatio,
            onVideoAspectRatioChange = { runPreviewTransition { viewModel.setVideoAspectRatio(it) } },
            videoLogProfile = state.videoConfig.logProfile,
            availableVideoLogProfiles = state.videoCapabilities.availableLogProfiles,
            onVideoLogProfileChange = { viewModel.setVideoLogProfile(it) },
            videoBitrate = state.videoConfig.bitrate,
            onVideoBitrateChange = { viewModel.setVideoBitrate(it) },
            videoCodec = videoCodec,
            onVideoCodecChange = { viewModel.setVideoCodec(it) },
            videoAudioInputId = state.videoConfig.audioInputId,
            videoAudioInputOptions = videoAudioInputOptions,
            onVideoAudioInputChange = { viewModel.setVideoAudioInputId(it) },
            videoNoiseSuppressionEnabled = state.videoConfig.noiseSuppressionEnabled,
            videoNoiseSuppressionAvailable = videoNoiseSuppressionAvailable,
            videoWindReduction = state.videoConfig.windReduction,
            videoNoiseReductionStatus = videoNoiseReductionStatus,
            videoAudioSettingsLocked = state.videoRecordingState.isRecording || state.videoRecordingState.isProcessing,
            onVideoNoiseSuppressionChange = viewModel::setVideoNoiseSuppression,
            onVideoWindReductionChange = viewModel::setVideoWindReduction,
            videoFalseColorEnabled=previewAssistSettings.falseColorEnabled,
            onVideoFalseColorChange={viewModel.setPreviewAssistSettings(previewAssistSettings.copy(falseColorEnabled=it))},
            videoZebraEnabled=previewAssistSettings.zebraEnabled,
            onVideoZebraChange={viewModel.setPreviewAssistSettings(previewAssistSettings.copy(zebraEnabled=it))},
            videoPeakingEnabled=focusPeakingEnabled,
            onVideoPeakingChange=viewModel::setFocusPeakingEnabled,
            videoHistogramEnabled=viewModel.showHistogram,
            onVideoHistogramChange=viewModel::saveShowHistogram,
            videoEngineEnabled = videoEngineEnabled,
            onVideoEngineChange = viewModel::setVideoEngineEnabled,
            videoExposureLocked = state.isExposureLocked,
            onVideoExposureLockChange = viewModel::setExposureLocked,
            videoWhiteBalanceLocked = state.videoConfig.whiteBalanceLockEnabled,
            onVideoWhiteBalanceLockChange = viewModel::setVideoWhiteBalanceLockEnabled,
            videoFps=state.videoConfig.fps.fps,
            videoShutterNs=state.shutterSpeed,
            videoShutterAuto=state.isShutterSpeedAuto,
            videoManualExposureSupported=viewModel.motionPrioritySupported,
            onVideoShutterAngleChange=viewModel::setVideoShutterAngle,
            useRaw = useRaw && state.isRawSupported,
            isRawSupported = state.isRawSupported,
            hdrPlusEnabled = useRawMax,
            onHdrPlusEnabledChange = viewModel::setHdrPlusEnabled,
            rawDcpId = rawDcpId,
            rawDcpIdsByLens = rawDcpIdsByLens,
            rawDcpLensOptions = rawDcpLensOptions(state.availableCameras),
            availableDcps = viewModel.availableDcps,
            rawHncsFilmCurveMode = rawHncsFilmCurveMode,
            rawRenderingEngine = rawColorEngine,
            rawToneMappingParameters = rawToneMappingParameters,
            rawSpectralFilmSelection = rawSpectralFilmSelection ?: SpectralFilmSelection(rawSpectralFilmStock ?: "kodak_portra_400"),
            rawSpectralFilmPrint = rawSpectralFilmPrint ?: "kodak_portra_endura",
            ultraHdrEnabled = ultraHdrEnabled,
            onUltraHdrToggle = viewModel::setUltraHdrGainMapEnabled,
            photoPreviewStabilizationEnabled =
                state.photoPreviewStabilizationEnabled,
            photoPreviewStabilizationAvailable =
                viewModel.realtimeStabilizationCoordinator.isCurrentCameraSupported,
            onPhotoPreviewStabilizationChange =
                viewModel::setPhotoPreviewStabilizationEnabled,
            onRawDcpChange = { viewModel.setRawDcpId(it) },
            onRawDcpIdsByLensChange = { viewModel.setRawDcpIdsByLens(it) },
            onRawHncsFilmCurveModeChange = { viewModel.setRawHncsFilmCurveMode(it) },
            onImportRawDcp = { dcpImportLauncher.launch(arrayOf("*/*")) },
            onDeleteRawDcp = { dcp ->
                viewModel.deleteRawDcp(dcp.id) { success ->
                    android.widget.Toast.makeText(
                        context,
                        if (success) R.string.raw_dcp_delete_success else R.string.raw_dcp_delete_failed,
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onRawColorEngineChange = { viewModel.setRawColorEngine(it) },
            onRawToneMappingParametersChange = { viewModel.setRawToneMappingParameters(it) },
            onRawSpectralFilmSelectionChange = { viewModel.setRawSpectralFilmSelection(it) },
            onRawSpectralFilmPrintChange = { viewModel.setRawSpectralFilmPrint(it) },
            rawFotufilmDocument = rawFotufilmDocument,
            onRawFotufilmDocumentChange = { viewModel.setRawFotufilmDocument(it) },
            meteringMode = state.meteringMode,
            onMeteringModeChange = { viewModel.setMeteringMode(it) },
            onFilterManageClick = {
                activePanel = ActivePanel.NONE
                onFilterManagementClick(null)
            },
            onFrameManageClick = {
                activePanel = ActivePanel.NONE
                onFrameManagementClick()
            },
            onPresetManageClick = {
                activePanel = ActivePanel.NONE
                onPresetManagementClick()
            },
            onToolboxClick = {
                activePanel = ActivePanel.NONE
                onToolboxClick()
            },
            onMoreSettingsClick = {
                activePanel = ActivePanel.NONE
                onSettingsClick()
            },
            useJpgMax = useJpgMax,
            onJpgMaxToggle = {
                viewModel.setUseJpgMax(it)
            },
            useMultipleExposure = useMultipleExposure,
            onMultipleExposureToggle = { viewModel.setUseMultipleExposure(it) },
            contentTopPadding = topSafePadding
        )

        // During navigation measurement or window resizing, the viewport can be
        // shorter than the toolbar. The panel then has no available vertical space.
        val filterPanelTop = minOf(
            if (!isXpan && !isVideoMode) topBarHeight else 0.dp,
            maxHeight
        )
        val fallbackFilterPanelBottom = when {
            isXpan -> maxHeight - 48.dp
            isVideoMode -> maxHeight - 170.dp
            else -> topBarHeight + cardHeight - 48.dp
        }
        val filterPanelAnchorY = if (isXpan) {
            captureButtonBounds?.top
        } else {
            filterButtonBounds?.bottom
        }
        val requestedFilterPanelBottom = filterPanelAnchorY?.let { anchorY ->
            val parentTop = cameraScreenBounds?.top ?: 0f
            with(density) { (anchorY - parentTop).toDp() - 8.dp }
        } ?: fallbackFilterPanelBottom
        val filterPanelBottom = requestedFilterPanelBottom.coerceIn(filterPanelTop, maxHeight)

        AnimatedVisibility(
            activePanel == ActivePanel.FILTERS,
            enter = if (isXpan) {slideInVertically(initialOffsetY = { it })} else fadeIn(),
            exit = if (isXpan) {slideOutVertically(targetOffsetY = { it })} else fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(filterPanelBottom)
                    .padding(top = filterPanelTop),
                contentAlignment = Alignment.BottomCenter
            ) {
                val allPresets by viewModel.allPresets.collectAsState()
                val defaultPresetName = stringResource(R.string.preset_new_preset_default)
                val isVideoLog = isVideoMode && state.videoConfig.logProfile.isEnabled

                // LUT 选择器 (内嵌 Presets 列表与统一控制，无多余背景遮挡)
                LutSelector(
                    availableLuts = viewModel.selectableLutList,
                    currentLutId = currentLutId,
                    thumbnail = viewModel.previewThumbnail,
                    onLutSelected = { viewModel.setLut(it) },
                    allPresets = allPresets,
                    presetModeEnabled = !isVideoLog,
                    activePresetId = activePresetId,
                    activePresetModified = activePresetModified,
                    selectedMode = if (isVideoLog) LutSelectorMode.Style else lutSelectorMode,
                    onModeSelected = { viewModel.setLutSelectorMode(it) },
                    availableFrames = viewModel.availableFrameList,
                    currentFrameId = viewModel.currentFrameId,
                    onFrameSelected = if (isVideoLog) null else viewModel::setFrame,
                    onFrameManagementClick = {
                        activePanel = ActivePanel.NONE
                        onFrameManagementClick()
                    },
                    onPresetSelected = { preset ->
                        discardTransientLookEdits()
                        if (presetRequiresPreviewTransition(preset)) {
                            runPreviewTransition { viewModel.applyPreset(preset) }
                        } else {
                            viewModel.applyPreset(preset)
                        }
                    },
                    onCreatePresetClick = {
                        viewModel.prepareCurrentSettingsPresetDraft(defaultPresetName)
                        onPresetEditClick(null)
                    },
                    onResetPresetClick = {
                        discardTransientLookEdits()
                        val preset = allPresets.firstOrNull { it.id == activePresetId }
                        if (presetRequiresPreviewTransition(preset)) {
                            runPreviewTransition { viewModel.resetActivePreset() }
                        } else {
                            viewModel.resetActivePreset()
                        }
                    },
                    onSavePresetClick = viewModel::saveCurrentSettingsToActivePreset,
                    onPresetManagementClick = onPresetManagementClick,
                    onEditClick = {
                        activePanel = ActivePanel.EDIT
                    },
                    onManageClick = { lutId ->
                        activePanel = ActivePanel.NONE
                        onFilterManagementClick(lutId)
                    },
                    categoryOrder = categoryOrder,
                    headerContent = if (isVideoLog) {
                        {
                            VideoLogLutModeSelector(
                                mode = state.videoConfig.logLutMode,
                                enabled = !state.videoRecordingState.isRecording &&
                                    !state.videoRecordingState.isProcessing,
                                onModeSelected = viewModel::setVideoLogLutMode
                            )
                        }
                    } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 6.dp, top = 10.dp, end = 6.dp)
                )
            }
        }

        if (activePanel == ActivePanel.EDIT) {
            LutEditBottomSheet(
                lutId = currentLutId,
                initialParams = previewRecipeParamsOverride ?: currentRecipeParams,
                onParamsPreviewChange = { previewRecipeParamsOverride = it },
                showEffects = true,
                onDismiss = {
                    previewRecipeParamsOverride = null
                    activePanel = ActivePanel.FILTERS
                }
            )
        }

        captureAnimationSnapshot?.let { snapshot ->
            CaptureAnimationOverlay(
                snapshot = snapshot,
                modifier = Modifier.fillMaxSize(),
                onFinished = {
                    if (captureAnimationSnapshot?.id == snapshot.id) {
                        captureAnimationSnapshot = null
                    }
                }
            )
        }

    }
}

@Composable
fun MultipleExposureOverlay(
    state: com.hinnka.mycamera.viewmodel.MultipleExposureSessionState,
    onFinish: () -> Unit,
    onUndo: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.padding(8.dp)) {
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .wrapContentWidth(),
            color = Color.Transparent,
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier
                    .background(
                        brush = Brush.horizontalGradient(
                            listOf(
                                Color(0xD90D1117),
                                Color(0xB8141B22)
                            )
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = AppIcons.Layers,
                    contentDescription = null,
                    tint = AccentColor,
                    modifier = Modifier.size(16.dp)
                )

                Text(
                    text = "${state.capturedCount}/${state.targetCount}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge.copy(
                        shadow = ViewfinderTextShadow
                    )
                )

                if (state.isSessionActive) {
                    IconButton(
                        onClick = onUndo,
                        enabled = state.capturedCount > 0 && !state.isProcessing,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            AppIcons.AutoMirroredOutlinedUndo,
                            contentDescription = stringResource(R.string.multiple_exposure_undo),
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                    }

                    IconButton(
                        onClick = onFinish,
                        enabled = state.canFinish,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = stringResource(R.string.multiple_exposure_finish),
                            modifier = Modifier.size(16.dp),
                            tint = AccentColor
                        )
                    }

                    IconButton(
                        onClick = onCancel,
                        enabled = !state.isProcessing,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.multiple_exposure_cancel),
                            modifier = Modifier.size(16.dp),
                            tint = Color.Red
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Controls(
    state: CameraState,
    viewModel: CameraViewModel,
    galleryViewModel: GalleryViewModel,
    latestPhoto: com.hinnka.mycamera.gallery.MediaData?,
    useMultipleExposure: Boolean,
    multipleExposureState: com.hinnka.mycamera.viewmodel.MultipleExposureSessionState,
    advancedCaptureActive: Boolean,
    advancedCaptureSelected: Boolean,
    onGalleryThumbnailBoundsChanged: (Rect) -> Unit,
    onCaptureButtonBoundsChanged: (Rect) -> Unit,
    onSwitchCameraClick: () -> Unit,
    onCaptureModeSelected: (CameraShootingMode) -> Unit,
    modeSwitchEnabled: Boolean,
    onCaptureTap: () -> Unit,
    onGalleryClick: () -> Unit,
    sideControlInset: Dp?,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    val captureButtonStyle by viewModel.captureButtonStyle.collectAsState()
    val captureButtonColor by viewModel.captureButtonColor.collectAsState()
    val captureButtonImagePath by viewModel.captureButtonImagePath.collectAsState()
    val captureProcessingState by viewModel.captureProcessingState.collectAsState()
    val capturedThumbnail by viewModel.capturedThumbnail.collectAsState()

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = CameraControlsLayoutDefaults.BottomPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (state.captureMode == CaptureMode.PHOTO) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = state.isVirtualApertureEnabled,
                        enabled = modeSwitchEnabled && !state.isCapturing,
                        onClick = { viewModel.setPortraitEnabled(!state.isVirtualApertureEnabled) },
                        label = { Text(stringResource(R.string.portrait_mode_label)) })
                    if (state.isVirtualApertureEnabled) {
                        FilterChip(selected = state.virtualAperture >= 2.8f,
                            enabled = !state.isCapturing,
                            onClick = { viewModel.setAperture(4f) },
                            label = { Text(stringResource(R.string.portrait_blur_soft)) })
                        FilterChip(selected = state.virtualAperture < 2.8f,
                            enabled = !state.isCapturing,
                            onClick = { viewModel.setAperture(2f) },
                            label = { Text(stringResource(R.string.portrait_blur_strong)) })
                    }
                }
                if (state.isVirtualApertureEnabled) {
                    Text(stringResource(R.string.portrait_mode_saved_photo_hint),
                        color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp)
                    PortraitDepthControl(enabled = modeSwitchEnabled && !state.isCapturing)
                }
            }
            CaptureModeSwitcher(
                shootingMode = when {
                    state.captureMode == CaptureMode.VIDEO -> CameraShootingMode.VIDEO
                    state.useRaw && state.isRawSupported -> CameraShootingMode.PROFESSIONAL
                    else -> CameraShootingMode.PHOTO
                },
                professionalModeEnabled = state.isRawSupported,
                enabled = modeSwitchEnabled &&
                    !state.videoRecordingState.isRecording &&
                    !state.videoRecordingState.isProcessing,
                onModeSelected = onCaptureModeSelected
            )

            Spacer(modifier = Modifier.height(CameraControlsLayoutDefaults.ModeSwitcherSpacing))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = sideControlInset ?: 32.dp)
                        .onGloballyPositioned { coordinates ->
                            onGalleryThumbnailBoundsChanged(coordinates.boundsInRoot())
                        }
                        .autoRotate()
                ) {
                    GalleryThumbnail(
                        latestPhoto = latestPhoto,
                        viewModel = galleryViewModel,
                        capturedThumbnail = capturedThumbnail,
                        onCapturedThumbnailLoaded = viewModel::acknowledgeCapturedThumbnail,
                        onClick = onGalleryClick
                    )
                    key(capturedThumbnail?.captureId) {
                        GalleryProcessingOverlay(
                            pendingCount = if (capturedThumbnail == null || capturedThumbnail?.bitmap != null ||
                                capturedThumbnail?.finalThumbnailLoaded == true
                            ) {
                                captureProcessingState.pendingCount
                            } else {
                                0
                            },
                            completedCount = captureProcessingState.completedCount,
                            modifier = Modifier.matchParentSize()
                        )
                    }
                }

                CaptureButton(
                    captureMode = state.captureMode,
                    isProfessionalMode = state.useRaw && state.isRawSupported,
                    isCapturing = state.isCapturing,
                    advancedCaptureActive = advancedCaptureActive,
                    photoCaptureProgress = state.photoCaptureProgress,
                    isBurstCapturing = state.burstCapturing,
                    isVideoRecording = state.videoRecordingState.isRecording,
                    isVideoProcessing = state.videoRecordingState.isProcessing,
                    isPaused = state.videoRecordingState.isPaused,
                    allowLongPress = state.captureMode == CaptureMode.PHOTO &&
                        !useMultipleExposure && !advancedCaptureSelected,
                    multipleExposureEnabled = useMultipleExposure && state.captureMode == CaptureMode.PHOTO,
                    multipleExposureProgress = multipleExposureState.capturedCount.toFloat() /
                            multipleExposureState.targetCount.coerceAtLeast(1).toFloat(),
                    customStyle = captureButtonStyle,
                    customColor = captureButtonColor,
                    customImagePath = captureButtonImagePath,
                    onTap = onCaptureTap,
                    onLongPressStart = { viewModel.startContinuousCapture() },
                    onLongPressEnd = { viewModel.stopContinuousCapture() },
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        onCaptureButtonBoundsChanged(coordinates.boundsInRoot())
                    },
                )

                if (state.videoRecordingState.isRecording) {
                    IconButton(
                        onClick = { viewModel.captureVideoFrame() },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = sideControlInset ?: 40.dp)
                            .size(CameraControlsLayoutDefaults.SideButtonSize)
                            .autoRotate()
                    ) {
                        Icon(
                            imageVector = AppIcons.CameraAlt,
                            contentDescription = stringResource(R.string.take_photo),
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            if (state.videoRecordingState.isPaused) {
                                viewModel.resumeVideoRecording()
                            } else {
                                viewModel.pauseVideoRecording()
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 100.dp)
                            .size(48.dp)
                            .autoRotate()
                    ) {
                        Icon(
                            imageVector = if (state.videoRecordingState.isPaused) Icons.Default.PlayArrow else AppIcons.Pause,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                } else {
                    PhysicalButton(
                        onClick = {
                            if (!state.videoRecordingState.isProcessing) {
                                onSwitchCameraClick()
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = sideControlInset ?: 40.dp)
                            .size(CameraControlsLayoutDefaults.SideButtonSize)
                            .autoRotate(),
                        shape = CircleShape
                    ) {
                        Icon(
                            imageVector = AppIcons.Cameraswitch,
                            contentDescription = stringResource(R.string.switch_camera),
                            tint = Color.White.copy(
                                alpha = if (state.videoRecordingState.isProcessing) 0.35f else 1f
                            ),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }


        }
    }
}

/** Keeps render progress independent of the shutter and the optional photo fly-in animation. */
@Composable
private fun GalleryProcessingOverlay(
    pendingCount: Int,
    completedCount: Long,
    modifier: Modifier = Modifier
) {
    if (pendingCount > 0) {
        Box(modifier = modifier
            .border(2.dp, AccentColor, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.BottomCenter) {
            Text(pendingCount.toString(), color = Color.White,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.65f)).padding(2.dp))
        }
    }
}


/**
 * 拍照按钮
 */
@Composable
fun CaptureButton(
    captureMode: CaptureMode,
    isProfessionalMode: Boolean,
    isCapturing: Boolean,
    advancedCaptureActive: Boolean = false,
    photoCaptureProgress: PhotoCaptureProgress?,
    isBurstCapturing: Boolean,
    isVideoRecording: Boolean,
    isVideoProcessing: Boolean,
    isPaused: Boolean,
    allowLongPress: Boolean,
    multipleExposureEnabled: Boolean,
    multipleExposureProgress: Float,
    customStyle: CaptureButtonStyle,
    customColor: Int,
    customImagePath: String?,
    onTap: () -> Unit,
    onLongPressStart: () -> Unit,
    onLongPressEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progressColor = AccentColor
    val showMultipleExposureProgress = captureMode == CaptureMode.PHOTO && multipleExposureEnabled
    val showCaptureProgress = captureMode == CaptureMode.PHOTO && isCapturing
    val showVideoProcessingProgress = captureMode == CaptureMode.VIDEO && isVideoProcessing
    val showActivityProgress = showCaptureProgress || showVideoProcessingProgress
    val progressRingCount = (if (showMultipleExposureProgress) 1 else 0) +
        (if (showActivityProgress) 1 else 0)
    var isPressed by remember { mutableStateOf(false) }
    val customImage = remember(customImagePath) {
        customImagePath
            ?.let(::File)
            ?.takeIf(File::isFile)
    }

    val scale = if (isPressed) 0.97f else 1f
    val pulseAlpha = 1f
    // Progress remains legible without a continuous decorative redraw loop.
    val rotation = 0f

    val captureProgress = rememberPhotoCaptureProgress(
        photoCaptureProgress.takeIf { isCapturing && captureMode == CaptureMode.PHOTO }
    )

    val currentCanTap by rememberUpdatedState(advancedCaptureActive || (!isCapturing && !isVideoProcessing))
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnLongStart by rememberUpdatedState(onLongPressStart)
    val currentOnLongEnd by rememberUpdatedState(onLongPressEnd)
    val currentDisabled by rememberUpdatedState(
        isCapturing ||
            isVideoProcessing ||
            (captureMode == CaptureMode.VIDEO && isVideoRecording)
    )

    PhysicalButton(
        modifier = modifier
            .size(CameraControlsLayoutDefaults.CaptureButtonSize)
            .scale(scale)
            .pointerInput(allowLongPress) {
                var isLongPressStarted = false
                detectTapGestures(
                    onTap = {
                        if (currentCanTap) {
                            currentOnTap()
                        }
                    },
                    onLongPress = if (allowLongPress) {
                        {
                            if (!currentDisabled) {
                                isLongPressStarted = true
                                currentOnLongStart()
                            }
                        }
                    } else null,
                    onPress = {
                        isPressed = true
                        isLongPressStarted = false
                        try {
                            tryAwaitRelease()
                        } finally {
                            isPressed = false
                            if (isLongPressStarted) {
                                currentOnLongEnd()
                            }
                        }
                    }
                )
            },
        shape = CircleShape,
        contentAlignment = Alignment.Center
    ) {
        val ringColor = when {
            captureMode == CaptureMode.VIDEO && isVideoProcessing ->
                Color.White.copy(alpha = 0.5f)
            captureMode == CaptureMode.VIDEO && isVideoRecording -> {
                if (isPaused) Color.White.copy(alpha = 0.8f)
                else Color.Red.copy(alpha = pulseAlpha)
            }
            else -> CameraControlGold
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = 2.dp,
                    color = ringColor,
                    shape = CircleShape
                )
        )

        // Center shutter surface
        val centerPadding = when {
                captureMode == CaptureMode.VIDEO && isVideoRecording -> 19.dp
                progressRingCount > 0 -> CaptureProgressRingDefaults.contentPadding(progressRingCount)
                else -> 6.dp
            }
        val centerCorner = if (advancedCaptureActive || (captureMode == CaptureMode.VIDEO && isVideoRecording)) 8.dp else 36.dp
        val defaultCenterBackground = when {
            advancedCaptureActive -> Modifier.background(Color.Red)
            captureMode == CaptureMode.VIDEO -> {
                Modifier.background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFF6668),
                            Color(0xFFE4383C)
                        )
                    )
                )
            }
            else -> {
                Modifier.background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White,
                            Color(0xFFD7D7D7)
                        )
                    )
                )
            }
        }
        val useCustomAppearance = captureMode != CaptureMode.VIDEO
        val centerBackgroundModifier = when {
            useCustomAppearance && customStyle == CaptureButtonStyle.COLOR ->
                Modifier.background(Color(customColor))
            else -> defaultCenterBackground
        }

        Box(
            modifier = Modifier
                .padding(centerPadding)
                .fillMaxSize()
                .clip(RoundedCornerShape(centerCorner))
                .then(centerBackgroundModifier)
        ) {
            if (
                useCustomAppearance &&
                customStyle == CaptureButtonStyle.IMAGE &&
                customImage != null
            ) {
                AsyncImage(
                    model = customImage,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .autoRotate(matchParentSize = true)
                )
            }
        }
        if (showMultipleExposureProgress) {
            CaptureProgressRing(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = 360f * multipleExposureProgress.coerceIn(0f, 1f)
            )
        }
        if (showActivityProgress) {
            val isIndeterminate = isBurstCapturing || showVideoProcessingProgress
            CaptureProgressRing(
                color = progressColor,
                startAngle = if (isIndeterminate) rotation - 90f else -90f,
                sweepAngle = if (isIndeterminate) 90f else 360f * captureProgress.value,
                inset = CaptureProgressRingDefaults.OuterInset +
                    if (showMultipleExposureProgress) CaptureProgressRingDefaults.RingSpacing else 0.dp,
                modifier = if (isIndeterminate) Modifier.progressSemantics()
                    else Modifier.progressSemantics(captureProgress.value)
            )
        }
    }
}

@Composable
private fun rememberPhotoCaptureProgress(progress: PhotoCaptureProgress?): State<Float> {
    val fraction = remember(progress) {
        mutableFloatStateOf(progress?.fractionAt(SystemClock.elapsedRealtimeNanos()) ?: 0f)
    }
    LaunchedEffect(progress) {
        if (progress == null) return@LaunchedEffect
        while (isActive && fraction.floatValue < 1f) {
            withFrameNanos {
                fraction.floatValue = progress.fractionAt(SystemClock.elapsedRealtimeNanos())
            }
        }
    }
    return fraction
}

@Composable
private fun CaptureModeSwitcher(
    shootingMode: CameraShootingMode,
    professionalModeEnabled: Boolean,
    enabled: Boolean,
    onModeSelected: (CameraShootingMode) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .width(228.dp)
            .height(CameraControlsLayoutDefaults.ModeSwitcherHeight)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.28f))
            .border(
                width = 0.5.dp,
                color = Color.White.copy(alpha = 0.18f),
                shape = RoundedCornerShape(18.dp)
            )
            .padding(3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModeSwitcherItem(
                label = stringResource(R.string.capture_mode_professional),
                selectedContentColor = CameraControlGold,
                selected = shootingMode == CameraShootingMode.PROFESSIONAL,
                enabled = enabled && professionalModeEnabled,
                onClick = { onModeSelected(CameraShootingMode.PROFESSIONAL) },
                modifier = Modifier.weight(1f)
            )
            ModeSwitcherItem(
                label = stringResource(R.string.capture_mode_photo),
                selected = shootingMode == CameraShootingMode.PHOTO,
                enabled = enabled,
                onClick = { onModeSelected(CameraShootingMode.PHOTO) },
                modifier = Modifier.weight(1f)
            )
            ModeSwitcherItem(
                label = stringResource(R.string.capture_mode_video),
                selected = shootingMode == CameraShootingMode.VIDEO,
                enabled = enabled,
                onClick = { onModeSelected(CameraShootingMode.VIDEO) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ModeSwitcherItem(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedContentColor: Color = CameraControlGold
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(20.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = when {
                selected -> selectedContentColor.copy(alpha = 0.88f)
                enabled -> Color.White.copy(alpha = 0.82f)
                else -> Color.White.copy(alpha = 0.3f)
            },
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

private fun cycleVideoResolution(state: CameraState): VideoResolutionPreset? {
    return nextOption(state.videoConfig.resolution, state.videoCapabilities.availableResolutions)
}

private fun cycleVideoFps(state: CameraState): VideoFpsPreset? {
    return nextOption(state.videoConfig.fps, state.videoCapabilities.availableFps)
}

private fun cycleVideoAspectRatio(state: CameraState): VideoAspectRatio? {
    return nextOption(state.videoConfig.aspectRatio, state.videoCapabilities.availableAspectRatios)
}

private fun cycleVideoLogProfile(state: CameraState): VideoLogProfile? {
    return nextOption(state.videoConfig.logProfile, state.videoCapabilities.availableLogProfiles)
}

private fun <T> nextOption(current: T, options: List<T>): T? {
    if (options.isEmpty()) return null
    val currentIndex = options.indexOf(current)
    return if (currentIndex == -1) {
        options.firstOrNull()
    } else {
        options[(currentIndex + 1) % options.size]
    }
}


fun Modifier.autoRotate(
    dx: Dp = 0.dp,
    dy: Dp = 0.dp,
    matchParentSize: Boolean = false
): Modifier = composed {
    val rotationDegrees = OrientationObserver.uiRotationDegrees
    val targetDegrees = if (rotationDegrees != 0f) rotationDegrees - 180 else 0f

    val animatedDegrees by animateFloatAsState(
        targetValue = targetDegrees,
        animationSpec = tween(durationMillis = 300),
        label = "rotationAnimation"
    )

    layout { measurable, constraints ->
        val rad = Math.toRadians(animatedDegrees.toDouble())
        val cos = abs(cos(rad))
        val sin = abs(sin(rad))

        // 核心优化：匹配父布局大小时，如果旋转接近 90 度，交换约束，
        // 从而让子组件（如 AsyncImage）按旋转后的方向进行测量，实现“铺满”效果。
        val modifiedConstraints = if (matchParentSize && sin > 0.5f) {
            Constraints(
                minWidth = constraints.minHeight,
                maxWidth = constraints.maxHeight,
                minHeight = constraints.minWidth,
                maxHeight = constraints.maxWidth
            )
        } else {
            constraints
        }

        val placeable = measurable.measure(modifiedConstraints)
        val width = placeable.width
        val height = placeable.height

        if (matchParentSize) {
            val visualWidth = width * cos + height * sin
            val visualHeight = width * sin + height * cos

            val containerWidth = constraints.maxWidth.toFloat()
            val containerHeight = constraints.maxHeight.toFloat()

            // 计算缩放：确保即使在动画中，内容也能刚好填满或不超出边界
            val scale = min(
                if (visualWidth > 0) containerWidth / visualWidth else 1.0,
                if (visualHeight > 0) containerHeight / visualHeight else 1.0
            ).toFloat().coerceAtMost(1.0f)

            layout(constraints.maxWidth, constraints.maxHeight) {
                placeable.placeRelativeWithLayer(
                    (constraints.maxWidth - width) / 2,
                    (constraints.maxHeight - height) / 2
                ) {
                    rotationZ = animatedDegrees
                    scaleX = scale
                    scaleY = scale
                }
            }
        } else {
            val newWidth = (placeable.width * cos + placeable.height * sin).toInt()
            val newHeight = (placeable.width * sin + placeable.height * cos).toInt()

            val nDx = dx.toPx() * sin
            val nDy = dy.toPx() * sin

            layout(newWidth, newHeight) {
                placeable.placeRelativeWithLayer(
                    x = (newWidth - placeable.width) / 2 + nDx.toInt(),
                    y = (newHeight - placeable.height) / 2 + nDy.toInt()
                ) {
                    rotationZ = animatedDegrees
                }
            }
        }
    }
}

@Composable
private fun CornerOverlay(
    modifier: Modifier = Modifier,
    radius: Dp,
    color: Color = Color.Black
) {
    Canvas(modifier = modifier) {
        val frame = Path().apply {
            addRect(Rect(0f, 0f, size.width, size.height))
        }
        val roundedContent = Path().apply {
            addRoundRect(
                RoundRect(
                    left = 0f,
                    top = 0f,
                    right = size.width,
                    bottom = size.height,
                    cornerRadius = CornerRadius(radius.toPx(), radius.toPx())
                )
            )
        }
        frame.op(frame, roundedContent, PathOperation.Difference)
        drawPath(frame, color)
    }
}


private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

private fun advancedModeLabel(mode: com.hinnka.mycamera.camera.AdvancedCameraMode): Int = when (mode) {
    com.hinnka.mycamera.camera.AdvancedCameraMode.SMART -> R.string.advanced_smart_capture
    com.hinnka.mycamera.camera.AdvancedCameraMode.PRE_CAPTURE -> R.string.advanced_pre_capture
    com.hinnka.mycamera.camera.AdvancedCameraMode.LIVE_COMPOSITE -> R.string.advanced_live_composite
    com.hinnka.mycamera.camera.AdvancedCameraMode.LIGHT_GRAFFITI -> R.string.advanced_light_graffiti
    com.hinnka.mycamera.camera.AdvancedCameraMode.LIVE_ND -> R.string.advanced_live_nd
    com.hinnka.mycamera.camera.AdvancedCameraMode.NIGHT -> R.string.advanced_night
    com.hinnka.mycamera.camera.AdvancedCameraMode.ASTRO -> R.string.advanced_astro
    com.hinnka.mycamera.camera.AdvancedCameraMode.STAR_TRAILS -> R.string.advanced_star_trails
    com.hinnka.mycamera.camera.AdvancedCameraMode.MOTION_HDR -> R.string.advanced_hdr
    com.hinnka.mycamera.camera.AdvancedCameraMode.HIGH_DETAIL -> R.string.advanced_high_detail
    com.hinnka.mycamera.camera.AdvancedCameraMode.SOURCE_BURST -> R.string.advanced_source_burst
    com.hinnka.mycamera.camera.AdvancedCameraMode.PREVIEW_COMPOSITE -> R.string.advanced_preview_composite
    com.hinnka.mycamera.camera.AdvancedCameraMode.PREVIEW_ND -> R.string.advanced_preview_nd
}

/** Optional depth augments the bundled mask; download is explicitly requested. */
@Composable
private fun PortraitDepthControl(enabled: Boolean) {
    val context = LocalContext.current
    val depthState by remember(context.applicationContext) {
        DepthModelManager.observe(context)
    }.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { DepthModelManager.importModel(context, it) }
    }
    TextButton(onClick = { showDialog = true }, enabled = enabled,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        modifier = Modifier.height(32.dp)) {
        Text(stringResource(when (depthState) {
            DepthModelDownloadState.Ready -> R.string.portrait_depth_ready
            is DepthModelDownloadState.Downloading, DepthModelDownloadState.Importing -> R.string.portrait_depth_working
            else -> R.string.portrait_depth_optional
        }), fontSize = 11.sp)
    }
    if (showDialog) {
        DepthModelDownloadDialog(state = depthState,
            onDownload = { DepthModelManager.download(context) },
            onImport = { importLauncher.launch(arrayOf("*/*")) },
            onDismiss = { showDialog = false },
            description = stringResource(R.string.portrait_depth_description))
    }
}
