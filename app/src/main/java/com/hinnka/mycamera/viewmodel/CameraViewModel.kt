package com.hinnka.mycamera.viewmodel

import com.hinnka.mycamera.camera.FocusBracketOutcome

import com.hinnka.mycamera.camera.FocusBracketState

import com.hinnka.mycamera.model.MultipleExposureBlendMode

import com.hinnka.mycamera.camera.IntervalCaptureState

import com.hinnka.mycamera.camera.SnapFocusDistance

import com.hinnka.mycamera.model.PreviewAssistSettings


import com.hinnka.mycamera.camera.AdvancedCameraMode
import com.hinnka.mycamera.camera.AdvancedCaptureSelection
import com.hinnka.mycamera.camera.AdvancedCaptureOptions
import com.hinnka.mycamera.processor.SourceTemporalAccumulator
import com.hinnka.mycamera.camera.AdvancedCameraState
import com.hinnka.mycamera.processor.TemporalPreviewBuffer
import com.hinnka.mycamera.processor.PhotoSequenceOperation
import com.hinnka.mycamera.processor.CaptureSequenceMerge
import kotlin.coroutines.resume
import android.app.Application
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureResult
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hinnka.mycamera.camera.*
import com.hinnka.mycamera.data.ContentRepository
import com.hinnka.mycamera.data.CameraFeaturePreferencesUpdate
import com.hinnka.mycamera.data.CaptureButtonStyle
import com.hinnka.mycamera.data.CaptureSoundRepository
import com.hinnka.mycamera.R
import com.hinnka.mycamera.data.DevelopAnimationStyle
import com.hinnka.mycamera.frame.FrameRenderer
import com.hinnka.mycamera.data.PreferenceUpdateValue
import com.hinnka.mycamera.data.PresetPackageManager
import com.hinnka.mycamera.data.UserPreferences
import com.hinnka.mycamera.data.VolumeKeyAction
import com.hinnka.mycamera.frame.FrameEditorDraft
import com.hinnka.mycamera.frame.FrameInfo
import com.hinnka.mycamera.frame.FramePreviewFactory
import com.hinnka.mycamera.frame.FramePreviewSelection
import com.hinnka.mycamera.gallery.GalleryManager
import com.hinnka.mycamera.gallery.MediaMetadata
import com.hinnka.mycamera.gallery.PhotoSavePath
import com.hinnka.mycamera.lut.BaselineColorCorrectionTarget
import com.hinnka.mycamera.lut.BakedLutExporter
import com.hinnka.mycamera.lut.LutConfig
import com.hinnka.mycamera.lut.LutConverter
import com.hinnka.mycamera.lut.LutInfo
import com.hinnka.mycamera.lut.creator.LutGenerator
import com.hinnka.mycamera.lut.creator.OpenAIApiClient
import com.hinnka.mycamera.model.CameraPreset
import com.hinnka.mycamera.model.ColorRecipeParams
import com.hinnka.mycamera.model.LutSelectorMode
import com.hinnka.mycamera.model.RecipeParam
import com.hinnka.mycamera.model.SafeImage
import com.hinnka.mycamera.model.toEffectParams
import com.hinnka.mycamera.phantom.PhantomWidgetProvider
import com.hinnka.mycamera.preview.EyeFocusPreviewFrame
import com.hinnka.mycamera.preview.PortraitMaskSnapshot
import com.hinnka.mycamera.preview.PreviewEyeFocusProcessor
import com.hinnka.mycamera.processor.CaptureProcessingQueue
import com.hinnka.mycamera.processor.RawBurstFrameRole
import com.hinnka.mycamera.processor.MgcSpatialOutputMode
import com.hinnka.mycamera.processor.MgcMergeMethod
import com.hinnka.mycamera.processor.PhotonSensorSizeTuning
import com.hinnka.mycamera.processor.MgcRawMaxMode
import com.hinnka.mycamera.processor.RawmaxExposurePlanner
import com.hinnka.mycamera.processor.RawStackFrame
import com.hinnka.mycamera.raw.ColorSpace
import com.hinnka.mycamera.raw.DcpProfileParser
import com.hinnka.mycamera.raw.DcpInfo
import com.hinnka.mycamera.raw.RawEnginePreviewPlan
import com.hinnka.mycamera.raw.RawEnginePreviewResolver
import com.hinnka.mycamera.raw.RawEnginePreviewSettings
import com.hinnka.mycamera.raw.HncsFilmCurveMode
import com.hinnka.mycamera.raw.HncsRenderIntent
import com.hinnka.mycamera.raw.RawOutputUpscaleMode
import com.hinnka.mycamera.raw.HncsProfileManager
import com.hinnka.mycamera.color.TransferCurve
import com.hinnka.mycamera.model.EffectParams
import com.hinnka.mycamera.raw.RawProcessingPreferences
import com.hinnka.mycamera.raw.RawSceneExposureMath
import com.hinnka.mycamera.raw.RawProfile
import com.hinnka.mycamera.raw.RawCfaCorrection
import com.hinnka.mycamera.raw.RawCaptureExposureCompensationMetadata
import com.hinnka.mycamera.raw.RawDemosaicProcessor
import com.hinnka.mycamera.raw.RawDigitalZoomResampling
import com.hinnka.mycamera.raw.RawProfileToneMapMode
import com.hinnka.mycamera.raw.LumixPhotoStyle
import com.hinnka.mycamera.raw.CanonPictureStyle
import com.hinnka.mycamera.raw.RawRenderingEngine
import com.hinnka.mycamera.raw.RawDenoiseDefaults
import com.hinnka.mycamera.raw.RawSharpeningDefaults
import com.hinnka.mycamera.raw.RawToneMappingParameters
import com.hinnka.mycamera.raw.RawNoiseProfileInfo
import com.hinnka.mycamera.raw.RawNoiseProfileManager
import com.hinnka.mycamera.raw.RawWhiteLevelCorrection
import com.hinnka.mycamera.raw.SpectralFilmProfile
import com.hinnka.mycamera.raw.SpectralFilmSelection
import com.hinnka.mycamera.raw.SpectralFilmTuning
import com.hinnka.mycamera.raw.fotufilm.FotufilmDocument
import com.hinnka.mycamera.raw.fotufilm.FotufilmDocumentStorage
import com.hinnka.mycamera.screencapture.PhantomPipCrop
import com.hinnka.mycamera.stabilization.ExternalLensStabilizationConfig
import com.hinnka.mycamera.ui.camera.CameraGLSurfaceView
import com.hinnka.mycamera.ui.camera.ZoomDisplayMode
import com.hinnka.mycamera.utils.*
import com.hinnka.mycamera.video.CaptureMode
import com.hinnka.mycamera.video.VideoAudioInputManager
import com.hinnka.mycamera.video.VideoAudioInputOption
import com.hinnka.mycamera.video.VideoAspectRatio
import com.hinnka.mycamera.video.VideoBitratePreset
import com.hinnka.mycamera.video.VideoFpsPreset
import com.hinnka.mycamera.video.VideoLogProfile
import com.hinnka.mycamera.video.VideoLogLutMode
import com.hinnka.mycamera.video.VideoRecordingPath
import com.hinnka.mycamera.video.VideoResolutionPreset
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlin.math.hypot

data class MultipleExposureFrame(
    val index: Int,
    val file: File
)

private data class PendingRawStackFrame(
    val frame: RawStackFrame,
    val captureInfo: CaptureInfo,
    val captureResult: CaptureResult?,
)

private data class CaptureSettingsSnapshot(
    @Volatile var state: CameraState,
    val preferences: UserPreferences,
    val lutId: String,
    val frameId: String?,
    val recipe: ColorRecipeParams,
    val cameraId: String,
    val sensorOrientation: Int,
    val lensFacing: Int,
    val deviceRotation: Int,
    val blackBorderCrop: RawBlackBorderCrop,
    val portraitMask: PortraitMaskSnapshot?,
    val multipleExposure: Boolean,
    val sequenceProperties: Map<String, String> = emptyMap(),
    val enginePlan: RawEnginePreviewPlan? = null,
    val photoId: String = UUID.randomUUID().toString(),
    val galleryRegistration: CompletableDeferred<Unit> = CompletableDeferred(),
    @Volatile var galleryRegistrationStarted: Boolean = false,
    val sourceRawReference: OwnedCaptureReference<Bitmap>? = null,
    // Exposure matching needs the unfiltered source; UI placeholders need the displayed look.
    @Volatile var originalThumbnail: Bitmap? = null,
    @Volatile var displayThumbnail: Bitmap? = null,
    var livePhotoVideo: CompletableDeferred<Pair<File, Long>?>? = null,
    var captureId: Long = 0L,
    @Volatile var captureCompleted: Boolean = false,
    @Volatile var processingFinished: Boolean = false,
    @Volatile var savedPhotoId: String? = null,
)

/** The shutter preview remains visible until the matching final thumbnail has loaded. */
data class CapturedThumbnail(
    val captureId: Long,
    val photoId: String,
    val bitmap: Bitmap?,
    val savedPhotoId: String?,
    val processingFinished: Boolean,
    val finalThumbnailLoaded: Boolean = false,
)

data class MultipleExposureSessionState(
    val enabled: Boolean = false,
    val sessionId: String? = null,
    val targetCount: Int = 2,
    val blendMode: MultipleExposureBlendMode = MultipleExposureBlendMode.AVERAGE,
    val capturedCount: Int = 0,
    val frames: List<MultipleExposureFrame> = emptyList(),
    val isProcessing: Boolean = false,
    val previewBitmap: Bitmap? = null
) {
    val isSessionActive: Boolean
        get() = sessionId != null

    val canFinish: Boolean
        get() = capturedCount >= 2 && !isProcessing
}

private fun resolvePreviewBaselineTarget(prefs: UserPreferences): BaselineColorCorrectionTarget? {
    return BaselineColorCorrectionTarget.RAW.takeIf {
        prefs.captureMode == CaptureMode.PHOTO && prefs.useRaw && !prefs.phantomMode
    }
}

private data class RawSpectralFilmSettings(
    val stock: String?,
    val print: String?,
    val tuning: SpectralFilmTuning
)

private fun resolveEffectiveRawAutoExposure(): Boolean = false

private fun rawProcessingMetadataProperties(
    userPrefs: UserPreferences?,
    sensorPhysicalAreaMm2: Float?,
    digitalZoom: Float,
): Map<String, String> = PhotonSensorSizeTuning.captureProperties(
    enabled = userPrefs?.let { it.useRawMax && it.rawMaxQualityTuningEnabled } == true,
    sensorPhysicalAreaMm2 = sensorPhysicalAreaMm2,
) + RawDigitalZoomResampling.captureProperties(userPrefs?.rawDigitalZoomResamplingEnabled == true) +
    mapOf("photoFinishUpscale" to (userPrefs?.photoFinishUpscale == true).toString(),
        "enhancedZoom" to (userPrefs?.enhancedZoomEnabled == true && digitalZoom.isFinite() && digitalZoom > 1.05f).toString(),
        "enhancedZoomRatio" to digitalZoom.toString())

private fun resolveCaptureSharpening(
    isRawCapture: Boolean,
    userPrefs: UserPreferences?,
): Float = if (isRawCapture) {
    userPrefs?.rawMaxSharpening ?: RawSharpeningDefaults.DEFAULT_STRENGTH
} else {
    0f
}.let(RawSharpeningDefaults::normalize)

internal data class CaptureDenoiseStrengths(
    val editableLuma: Float,
    val editableChroma: Float,
    val bakedLuma: Float?,
    val bakedChroma: Float?,
)

internal fun resolveCaptureDenoiseStrengths(
    isRawCapture: Boolean,
    userPrefs: UserPreferences?,
): CaptureDenoiseStrengths = when {
    !isRawCapture -> CaptureDenoiseStrengths(0f, 0f, null, null)
    else -> CaptureDenoiseStrengths(
        editableLuma = 0f,
        editableChroma = 0f,
        bakedLuma = RawDenoiseDefaults.normalize(
            userPrefs?.rawMaxNoiseReduction ?: RawDenoiseDefaults.RAW_MAX_LUMA_STRENGTH
        ),
        bakedChroma = RawDenoiseDefaults.normalize(
            userPrefs?.rawMaxChromaNoiseReduction
                ?: RawDenoiseDefaults.RAW_MAX_CHROMA_STRENGTH
        ),
    )
}

private fun ColorRecipeParams.withoutIndependentEffects(): ColorRecipeParams {
    return EffectParams.DEFAULT.applyTo(this)
}

private data class PresetMatchSnapshot(
    val lutId: String?,
    val colorRecipe: ColorRecipeParams,
    val effects: EffectParams,
    val captureMode: CaptureMode,
    val aspectRatio: String,
    val isProfessionalMode: Boolean,
    val ultraHdrGainMapEnabled: Boolean,
    val frameId: String?,
    val rawDcpId: String?,
    val rawDcpIdsByLens: Map<String, String?>,
    val rawHncsProfileId: String?,
    val rawHncsRenderIntent: HncsRenderIntent,
    val rawHncsFilmCurveMode: HncsFilmCurveMode,
    val rawRenderingEngine: RawRenderingEngine,
    val rawMaxSharpening: Float,
    val rawMaxNoiseReduction: Float,
    val rawMaxChromaNoiseReduction: Float,
    val rawExposureCompensation: Float,
    val rawHighlightsAdjustment: Float,
    val rawShadowsAdjustment: Float,
    val rawBlackPointCorrection: Float,
    val rawWhitePointCorrection: Float,
    val rawOppoMasterToneMap: Boolean,
    val rawLumixPhotoStyle: LumixPhotoStyle,
    val rawCanonPictureStyle: CanonPictureStyle,
    val rawCanonExposureCompensationEv: Float,
    val rawLumixColorMatchingEnabled: Boolean,
    val rawHncsColorMatchingEnabled: Boolean,
    val rawCanonUseR5ColorMatrix: Boolean,
    val rawLeicaUseM9ColorMatrix: Boolean,
    /** 仅含 AgX / Filmic 曲线参数，见 [RawToneMappingParameters.engineToneCurve]。 */
    val rawEngineToneCurve: RawToneMappingParameters,
    val rawSpectralFilmStock: String?,
    val rawSpectralFilmPrint: String?,
    val rawFotufilmDocument: FotufilmDocument?,
    val rawDROMode: String,
    val rawBaselineLutId: String?
) {
    fun matches(preset: com.hinnka.mycamera.model.CameraPreset): Boolean {
        val colorRecipeMatches = colorRecipe.withoutIndependentEffects()
            .isSameAs(preset.colorRecipe.withoutIndependentEffects())
        val presetLutId = CameraPreset.normalizeLutId(preset.lutId)
//        PLog.d("PresetMatchSnapshot", "colorRecipe=$colorRecipe ${preset.colorRecipe} colorRecipe match: $colorRecipeMatches")
        val sharedSettingsMatch = lutId == presetLutId &&
            colorRecipeMatches &&
            effects == preset.effects &&
            frameId == preset.frameId
        if (!sharedSettingsMatch) return false

        if (captureMode == CaptureMode.PHOTO && aspectRatio != preset.aspectRatio) {
            return false
        }
        if (!isProfessionalMode) return true

        return ultraHdrGainMapEnabled == preset.ultraHdrGainMapEnabled &&
            rawDcpId == preset.rawDcpId &&
            rawDcpIdsByLens == preset.rawDcpIdsByLens &&
            rawHncsFilmCurveMode == HncsFilmCurveMode.fromPersistedValue(
                preset.rawHncsFilmCurveMode
            ) &&
            rawRenderingEngine == RawRenderingEngine.fromPersistedName(preset.rawRenderingEngine) &&
            rawMaxSharpening == preset.rawMaxSharpening &&
            rawMaxNoiseReduction == preset.rawMaxNoiseReduction &&
            rawMaxChromaNoiseReduction == preset.rawMaxChromaNoiseReduction &&
            rawExposureCompensation == preset.rawExposureCompensation &&
            rawHighlightsAdjustment == preset.rawHighlightsAdjustment &&
            rawShadowsAdjustment == preset.rawShadowsAdjustment &&
            rawBlackPointCorrection == preset.rawBlackPointCorrection &&
            rawWhitePointCorrection == preset.rawWhitePointCorrection &&
            rawLumixPhotoStyle == LumixPhotoStyle.fromPersistedValue(preset.rawLumixPhotoStyle) &&
            rawCanonPictureStyle == CanonPictureStyle.fromPersistedValue(preset.rawCanonPictureStyle) &&
            rawCanonExposureCompensationEv == preset.rawCanonExposureCompensationEv &&
            rawLumixColorMatchingEnabled == preset.rawLumixColorMatchingEnabled &&
            rawHncsColorMatchingEnabled == preset.rawHncsColorMatchingEnabled &&
            rawCanonUseR5ColorMatrix == preset.rawCanonUseR5ColorMatrix &&
            rawLeicaUseM9ColorMatrix == preset.rawLeicaUseM9ColorMatrix &&
            rawOppoMasterToneMap == preset.rawOppoMasterToneMap &&
            rawEngineToneCurve == preset.rawEngineToneCurve() &&
            rawSpectralFilmStock == preset.rawSpectralFilmStock &&
            rawSpectralFilmPrint == preset.rawSpectralFilmPrint &&
            rawFotufilmDocument == FotufilmDocumentStorage.decode(preset.rawFotufilmDocument, "preset ${preset.id}") &&
            rawDROMode == preset.rawDROMode &&
            rawBaselineLutId == preset.rawBaselineLutId
    }

    fun mismatchSummary(preset: com.hinnka.mycamera.model.CameraPreset): String {
        val presetLutId = CameraPreset.normalizeLutId(preset.lutId)
        val presetRawRenderingEngine = RawRenderingEngine.fromPersistedName(preset.rawRenderingEngine)
        val differences = buildList {
            if (lutId != presetLutId) add("lutId current=$lutId preset=$presetLutId")
            if (
                !colorRecipe.withoutIndependentEffects()
                    .isSameAs(preset.colorRecipe.withoutIndependentEffects())
            ) {
                add("colorRecipe differs")
            }
            if (effects != preset.effects) add("effects current=$effects preset=${preset.effects}")
            if (frameId != preset.frameId) add("frameId current=$frameId preset=${preset.frameId}")
            if (captureMode == CaptureMode.PHOTO && aspectRatio != preset.aspectRatio) {
                add("aspectRatio current=$aspectRatio preset=${preset.aspectRatio}")
            }
            if (isProfessionalMode) {
                if (ultraHdrGainMapEnabled != preset.ultraHdrGainMapEnabled) {
                    add(
                        "ultraHdrGainMapEnabled current=$ultraHdrGainMapEnabled " +
                            "preset=${preset.ultraHdrGainMapEnabled}"
                    )
                }
                if (rawDcpId != preset.rawDcpId) add("rawDcpId current=$rawDcpId preset=${preset.rawDcpId}")
                if (rawDcpIdsByLens != preset.rawDcpIdsByLens) {
                    add("rawDcpIdsByLens current=$rawDcpIdsByLens preset=${preset.rawDcpIdsByLens}")
                }
                val presetHncsFilmCurveMode = HncsFilmCurveMode.fromPersistedValue(
                    preset.rawHncsFilmCurveMode
                )
                if (rawHncsFilmCurveMode != presetHncsFilmCurveMode) {
                    add(
                        "rawHncsFilmCurveMode current=$rawHncsFilmCurveMode " +
                            "preset=$presetHncsFilmCurveMode"
                    )
                }
                if (rawRenderingEngine != presetRawRenderingEngine) {
                    add("rawRenderingEngine current=$rawRenderingEngine preset=$presetRawRenderingEngine")
                }
                if (rawMaxSharpening != preset.rawMaxSharpening) {
                    add("rawMaxSharpening current=$rawMaxSharpening preset=${preset.rawMaxSharpening}")
                }
                if (rawMaxNoiseReduction != preset.rawMaxNoiseReduction) {
                    add(
                        "rawMaxNoiseReduction current=$rawMaxNoiseReduction " +
                            "preset=${preset.rawMaxNoiseReduction}"
                    )
                }
                if (rawMaxChromaNoiseReduction != preset.rawMaxChromaNoiseReduction) {
                    add(
                        "rawMaxChromaNoiseReduction current=$rawMaxChromaNoiseReduction " +
                            "preset=${preset.rawMaxChromaNoiseReduction}"
                    )
                }
                if (rawExposureCompensation != preset.rawExposureCompensation) {
                    add(
                        "rawExposureCompensation current=$rawExposureCompensation " +
                            "preset=${preset.rawExposureCompensation}"
                    )
                }
                if (rawHighlightsAdjustment != preset.rawHighlightsAdjustment) {
                    add(
                        "rawHighlightsAdjustment current=$rawHighlightsAdjustment " +
                            "preset=${preset.rawHighlightsAdjustment}"
                    )
                }
                if (rawShadowsAdjustment != preset.rawShadowsAdjustment) {
                    add(
                        "rawShadowsAdjustment current=$rawShadowsAdjustment " +
                            "preset=${preset.rawShadowsAdjustment}"
                    )
                }
                if (rawBlackPointCorrection != preset.rawBlackPointCorrection) {
                    add(
                        "rawBlackPointCorrection current=$rawBlackPointCorrection " +
                            "preset=${preset.rawBlackPointCorrection}"
                    )
                }
                if (rawWhitePointCorrection != preset.rawWhitePointCorrection) {
                    add(
                        "rawWhitePointCorrection current=$rawWhitePointCorrection " +
                            "preset=${preset.rawWhitePointCorrection}"
                    )
                }
                if (rawLumixPhotoStyle != LumixPhotoStyle.fromPersistedValue(preset.rawLumixPhotoStyle)) {
                    add("rawLumixPhotoStyle current=$rawLumixPhotoStyle preset=${preset.rawLumixPhotoStyle}")
                }
                if (rawCanonPictureStyle != CanonPictureStyle.fromPersistedValue(preset.rawCanonPictureStyle)) {
                    add("rawCanonPictureStyle current=$rawCanonPictureStyle preset=${preset.rawCanonPictureStyle}")
                }
                if (rawCanonExposureCompensationEv != preset.rawCanonExposureCompensationEv) {
                    add("rawCanonExposureCompensationEv current=$rawCanonExposureCompensationEv preset=${preset.rawCanonExposureCompensationEv}")
                }
                if (rawLumixColorMatchingEnabled != preset.rawLumixColorMatchingEnabled ||
                    rawHncsColorMatchingEnabled != preset.rawHncsColorMatchingEnabled
                ) {
                    add(
                        "rawColorMatching current=$rawLumixColorMatchingEnabled/$rawHncsColorMatchingEnabled " +
                            "preset=${preset.rawLumixColorMatchingEnabled}/${preset.rawHncsColorMatchingEnabled}"
                    )
                }
                if (rawCanonUseR5ColorMatrix != preset.rawCanonUseR5ColorMatrix) {
                    add(
                        "rawCanonUseR5ColorMatrix current=$rawCanonUseR5ColorMatrix " +
                            "preset=${preset.rawCanonUseR5ColorMatrix}"
                    )
                }
                if (rawLeicaUseM9ColorMatrix != preset.rawLeicaUseM9ColorMatrix) {
                    add(
                        "rawLeicaUseM9ColorMatrix current=$rawLeicaUseM9ColorMatrix " +
                            "preset=${preset.rawLeicaUseM9ColorMatrix}"
                    )
                }
                if (rawOppoMasterToneMap != preset.rawOppoMasterToneMap) {
                    add(
                        "rawOppoMasterToneMap current=$rawOppoMasterToneMap " +
                            "preset=${preset.rawOppoMasterToneMap}"
                    )
                }
                if (rawEngineToneCurve != preset.rawEngineToneCurve()) {
                    add("rawEngineToneCurve current=$rawEngineToneCurve preset=${preset.rawEngineToneCurve()}")
                }
                if (rawSpectralFilmStock != preset.rawSpectralFilmStock) {
                    add("rawSpectralFilmStock current=$rawSpectralFilmStock preset=${preset.rawSpectralFilmStock}")
                }
                if (rawSpectralFilmPrint != preset.rawSpectralFilmPrint) {
                    add("rawSpectralFilmPrint current=$rawSpectralFilmPrint preset=${preset.rawSpectralFilmPrint}")
                }
                val presetFotufilmDocument =
                    FotufilmDocumentStorage.decode(preset.rawFotufilmDocument, "preset ${preset.id}")
                if (rawFotufilmDocument != presetFotufilmDocument) {
                    add("rawFotufilmDocument current=$rawFotufilmDocument preset=$presetFotufilmDocument")
                }
                if (rawDROMode != preset.rawDROMode) {
                    add("rawDROMode current=$rawDROMode preset=${preset.rawDROMode}")
                }
                if (rawBaselineLutId != preset.rawBaselineLutId) {
                    add("rawBaselineLutId current=$rawBaselineLutId preset=${preset.rawBaselineLutId}")
                }
            }
        }
        return differences.joinToString("; ").ifEmpty { "unknown" }
    }
}

private data class ActivePresetMatchState(
    val prefs: UserPreferences,
    val presets: List<com.hinnka.mycamera.model.CameraPreset>,
    val aspectRatio: String,
    val lutId: String,
    val recipe: ColorRecipeParams,
    val effects: EffectParams
)

private data class SettingValue<T>(val value: T)

internal fun resolveMultiFrameOutputScale(
    useJpgMax: Boolean,
    useRawMax: Boolean,
    rawMaxOutputScale: Float,
    jpgMultiFrameDenoiseOutputScale: Float = MultiFrameConfig.DEFAULT_SUPER_RESOLUTION_SCALE,
): Float? = when {
    useRawMax -> MultiFrameConfig.normalizeOutputScale(
        outputScale = rawMaxOutputScale,
        fallback = MultiFrameConfig.DEFAULT_SUPER_RESOLUTION_SCALE,
    )
    useJpgMax -> MultiFrameConfig.normalizeOutputScale(jpgMultiFrameDenoiseOutputScale)
    else -> null
}

internal fun resolveLutIdForCaptureMode(
    photoLutId: String?,
    videoLutId: String?,
    separateVideoLutEnabled: Boolean,
    captureMode: CaptureMode,
    defaultLutId: String?,
): String? {
    val persistedLutId = if (captureMode == CaptureMode.VIDEO && separateVideoLutEnabled) {
        videoLutId ?: photoLutId
    } else {
        photoLutId
    }
    return persistedLutId ?: defaultLutId
}

private data class CameraFeatureUpdate(
    val captureMode: SettingValue<CaptureMode>? = null,
    val lutId: SettingValue<String?>? = null,
    val colorRecipe: SettingValue<ColorRecipeParams>? = null,
    val effects: SettingValue<EffectParams>? = null,
    val aspectRatio: SettingValue<AspectRatio>? = null,
    val useRaw: SettingValue<Boolean>? = null,
    val useJpgMax: SettingValue<Boolean>? = null,
    val useRawMax: SettingValue<Boolean>? = null,
    val ultraHdrGainMapEnabled: SettingValue<Boolean>? = null,
    val frameId: SettingValue<String?>? = null,
    val rawDcpId: SettingValue<String?>? = null,
    val rawDcpIdsByLens: SettingValue<Map<String, String?>>? = null,
    val rawHncsProfileId: SettingValue<String?>? = null,
    val rawHncsRenderIntent: SettingValue<HncsRenderIntent>? = null,
    val rawHncsFilmCurveMode: SettingValue<HncsFilmCurveMode>? = null,
    val rawRenderingEngine: SettingValue<RawRenderingEngine>? = null,
    val rawMaxSharpening: SettingValue<Float>? = null,
    val rawMaxNoiseReduction: SettingValue<Float>? = null,
    val rawMaxChromaNoiseReduction: SettingValue<Float>? = null,
    val rawExposureCompensation: SettingValue<Float>? = null,
    val rawHighlightsAdjustment: SettingValue<Float>? = null,
    val rawShadowsAdjustment: SettingValue<Float>? = null,
    val rawBlackPointCorrection: SettingValue<Float>? = null,
    val rawWhitePointCorrection: SettingValue<Float>? = null,
    val rawProfileToneMapMode: SettingValue<RawProfileToneMapMode>? = null,
    val rawOppoMasterToneMap: SettingValue<Boolean>? = null,
    val rawLumixPhotoStyle: SettingValue<LumixPhotoStyle>? = null,
    val rawCanonPictureStyle: SettingValue<CanonPictureStyle>? = null,
    val rawCanonExposureCompensationEv: SettingValue<Float>? = null,
    val rawLumixColorMatchingEnabled: SettingValue<Boolean>? = null,
    val rawHncsColorMatchingEnabled: SettingValue<Boolean>? = null,
    val rawCanonUseR5ColorMatrix: SettingValue<Boolean>? = null,
    val rawLeicaUseM9ColorMatrix: SettingValue<Boolean>? = null,
    /** 仅消费 AgX / Filmic 曲线参数。 */
    val rawEngineToneCurve: SettingValue<RawToneMappingParameters>? = null,
    val rawSpectralFilmStock: SettingValue<String?>? = null,
    val rawSpectralFilmPrint: SettingValue<String?>? = null,
    val rawFotufilmDocument: SettingValue<FotufilmDocument?>? = null,
    val droMode: SettingValue<String>? = null,
    val rawBaselineLutId: SettingValue<String?>? = null,
    val activePresetId: SettingValue<String?>? = null,
    val useMultipleExposure: SettingValue<Boolean>? = null
)

/**
 * 相机 ViewModel
 * 使用 Camera2Controller 支持隐藏摄像头
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CameraViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "CameraViewModel"
        private const val HDR_BRACKET_FRAME_COUNT = 3
        private const val HDR_BRACKET_ZERO_INDEX = 0
        private const val HDR_BRACKET_LOW_INDEX = 2
        private const val FIRST_PREVIEW_PREWARM_GRACE_MS = 1_000L
        private const val MIN_PREWARM_MEMORY_CLASS_MB = 384
        private const val EYE_FOCUS_MOVE_THRESHOLD = 0.035f
        private const val EYE_FOCUS_MAX_AF_TRIGGER_FPS = 15L
        private const val EYE_FOCUS_MIN_TRIGGER_INTERVAL_MS =
            (1_000L + EYE_FOCUS_MAX_AF_TRIGGER_FPS - 1L) / EYE_FOCUS_MAX_AF_TRIGGER_FPS
        private const val EYE_FOCUS_REFRESH_INTERVAL_MS = 1_800L
        private const val EYE_FOCUS_RETRY_INTERVAL_MS = 1_200L
        private const val PORTRAIT_MASK_MAX_AGE_NS = 1_000_000_000L
    }

    private data class HdrBracketFrame(
        val image: SafeImage,
        val captureResult: CaptureResult?,
        val originalIndex: Int,
        val timestamp: Long
    )

    private data class HdrBracketFrameOrder(
        val images: List<SafeImage>,
        val captureResults: List<CaptureResult?>
    )

    private val cameraController = Camera2Controller(application)


    // 内容仓库（单例，与 GalleryViewModel 共享）
    private val contentRepository = ContentRepository.getInstance(application)
    private val presetPackageManager = PresetPackageManager(application, contentRepository)

    private val userPreferencesRepository = contentRepository.userPreferencesRepository
    private val subjectFocusPreferences = com.hinnka.mycamera.preview.SubjectFocusPreferences(application)
    val subjectFocusEnabled = subjectFocusPreferences.enabled
    val subjectFocusCategory = subjectFocusPreferences.category
    fun setSubjectFocusEnabled(enabled: Boolean) {
        subjectFocusPreferences.setEnabled(enabled)
        previewEyeFocusProcessor.resetTracking()
        if (!enabled && !needsSceneDetection()) previewEyeFocusProcessor.releaseSubjectDetector()
        _subjectFocusLabel.value = null
    }
    fun setSubjectFocusCategory(category: String) {
        subjectFocusPreferences.setCategory(category)
        previewEyeFocusProcessor.resetTracking()
        _subjectFocusLabel.value = null
    }
    private val trackedSceneMotion = com.hinnka.mycamera.camera.TrackedSceneMotion()
    private val _coachFrameMotion = MutableStateFlow<Float?>(null)
    private val _sceneModelUnavailable = MutableStateFlow(false)
    val sceneModelUnavailable = _sceneModelUnavailable.asStateFlow()
    private var sceneSampleElapsedMs = 0L
    private val motionPriorityController = com.hinnka.mycamera.camera.MotionPriorityController()
    private val motionPriorityMutex = Mutex()
    private val _motionPriorityEnabled = MutableStateFlow(false)
    val motionPriorityEnabled = _motionPriorityEnabled.asStateFlow()
    val motionPrioritySupported get() = cameraController.supportsMotionPriorityExposure
    @Volatile private var motionGeneration = 0L
    private data class PendingMotionRestore(val cameraId: String,
        val before: com.hinnka.mycamera.camera.MotionPriorityController.Exposure,
        val target: com.hinnka.mycamera.camera.MotionPriorityController.Exposure)
    private var pendingMotionRestore: PendingMotionRestore? = null
    private fun motionExposure() = state.value.let {
        com.hinnka.mycamera.camera.MotionPriorityController.Exposure(it.isAutoExposure,it.isIsoAuto,it.isShutterSpeedAuto,it.iso,it.shutterSpeed,it.exposureBias)
    }
    fun setMotionPriorityEnabled(enabled: Boolean) {
        motionGeneration++
        _motionPriorityEnabled.value = enabled && motionPrioritySupported
        if (enabled) { previewEyeFocusProcessor.releaseSubjectDetector(); clearSceneTracking() }
        viewModelScope.launch { motionPriorityMutex.withLock { updateMotionPriority() } }
    }
    private fun manualExposureOverride() {
        motionGeneration++
        _motionPriorityEnabled.value = false
        motionPriorityController.userOverride()
        pendingMotionRestore = null
    }
    private suspend fun updateMotionPriority() {
        val current = state.value
        // A running sequence owns its frozen exposure. Restore only once that owner finishes.
        if (current.isCapturing || pendingCaptureSettings.isNotEmpty() || _advancedCameraState.value.running || _advancedCameraState.value.saving ||
            _focusBracketState.value.isRunning || _intervalCaptureState.value.isRunning) return
        val pending = pendingMotionRestore
        if (pending != null) {
            if (current.currentCameraId != pending.cameraId) pendingMotionRestore = null
            else if (current.isPreviewActive) {
                val generation = motionGeneration
                withContext(NonCancellable) {
                    cameraController.applyTemporaryMotionExposure(pending.cameraId,pending.before,pending.target) { motionGeneration==generation }
                }
                pendingMotionRestore = null
            }
            return
        }
        val exposure = motionExposure()
        val info = current.getCurrentCameraInfo()
        val shutter = info?.exposureTimeRange
        val iso = info?.isoRange
        val eligible = current.captureMode == CaptureMode.PHOTO && current.isPreviewActive &&
            _selectedAdvancedCapture.value == null && !multipleExposureState.enabled && !current.isExposureLocked
        val decision = motionPriorityController.update(_motionPriorityEnabled.value,eligible,motionPrioritySupported,
            current.currentCameraId,exposure,shutter?.lower ?: 0L,shutter?.upper ?: 0L,iso?.lower ?: 0,iso?.upper ?: 0,
            _coachFrameMotion.value.takeIf { SystemClock.elapsedRealtime()-sceneSampleElapsedMs in 0L..1000L },SystemClock.elapsedRealtime())
        val target = when (decision) {
            is com.hinnka.mycamera.camera.MotionPriorityController.Decision.Apply -> decision.exposure
            is com.hinnka.mycamera.camera.MotionPriorityController.Decision.Restore -> decision.exposure
            else -> return
        }
        if (!current.isPreviewActive) {
            pendingMotionRestore=PendingMotionRestore(current.currentCameraId,exposure,target)
            return
        }
        val generation=motionGeneration
        val actual=withContext(NonCancellable) {
            cameraController.applyTemporaryMotionExposure(current.currentCameraId,exposure,target) {motionGeneration==generation}
        }
        if(actual!=null)motionPriorityController.acknowledge(actual)
    }
    private suspend fun pauseMotionPriorityForCapture() {
        motionGeneration++;_motionPriorityEnabled.value=false
        motionPriorityMutex.withLock {
            updateMotionPriority()
            if (pendingMotionRestore != null && state.value.isPreviewActive) updateMotionPriority()
        }
    }
    private fun needsSceneDetection() = motionPriorityEnabled.value || _selectedAdvancedCapture.value?.mode == AdvancedCameraMode.SMART
    private fun clearSceneTracking() {
        _coachFrameMotion.value = null
        trackedSceneMotion.clear()
        sceneSampleElapsedMs = 0L
    }

    private val _subjectFocusLabel = MutableStateFlow<String?>(null)
    val subjectFocusLabel = _subjectFocusLabel.asStateFlow()
    private val previewEyeFocusProcessor = PreviewEyeFocusProcessor(application)

    // 计费管理器
    private val billingManager = com.hinnka.mycamera.billing.BillingManagerImpl(application)
    val isPurchased = billingManager.isPurchased

    // 快门音效播放器
    private val shutterSoundPlayer = ShutterSoundPlayer(application)
    private val captureSoundRepository = CaptureSoundRepository(application, userPreferencesRepository)
    private val _captureSoundImporting = MutableStateFlow(false)
    val captureSoundImporting = _captureSoundImporting.asStateFlow()
    private val _captureSoundErrors = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val captureSoundErrors = _captureSoundErrors.asSharedFlow()

    // 震动辅助类
    private val vibrationHelper = VibrationHelper(application)
    private val videoAudioInputManager = VideoAudioInputManager(application)

    private val locationManager = LocationManager(application)
    private var locationTrackingForeground = false

    val state: StateFlow<CameraState> = cameraController.state
    val videoNoiseReductionStatus = cameraController.videoRecorder.noiseReductionStatus
    val videoMonitoringStats = cameraController.videoRecorder.monitoringStats
    val rawVideoState = cameraController.rawVideoState
    fun toggleRawVideoRecording(fps: Int) {
        if (rawVideoState.value.recording) cameraController.stopRawVideoRecording()
        else if (!rawVideoState.value.stopping) cameraController.startRawVideoRecording(fps)
    }

    val livePhotoRecorder get() = cameraController.livePhotoRecorder
    val realtimeStabilizationCoordinator get() = cameraController.realtimeStabilizationCoordinator
    val isAlgorithmicStabilizationSupported: Boolean
        get() = cameraController.realtimeStabilizationCoordinator.isGyroscopeAvailable

    // 照片保存完成事件
    private val _imageSavedEvent = MutableSharedFlow<String?>()
    val imageSavedEvent: SharedFlow<String?> = _imageSavedEvent.asSharedFlow()

    // Shutter completion is independent of image delivery and the render/save queue.
    private val _captureCompletedEvent = MutableSharedFlow<MediaMetadata>()
    val captureCompletedEvent = _captureCompletedEvent.asSharedFlow()

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized = _isInitialized.asStateFlow()

    private val _canStartShutterAnimation = MutableStateFlow(false)
    val canStartShutterAnimation = _canStartShutterAnimation.asStateFlow()

    // LUT 相关状态
    var currentLutConfig: LutConfig? by mutableStateOf(null)
        private set

    var currentBaselineLutConfig: LutConfig? by mutableStateOf(null)
        private set

    private val _advancedEngineRequested = MutableStateFlow(com.hinnka.mycamera.camera.AdvancedCapturePreferences(getApplication()).selected() != null)
    private val _currentEnginePreviewPlan = MutableStateFlow<RawEnginePreviewPlan?>(null)
    val currentEnginePreviewPlan = _currentEnginePreviewPlan.asStateFlow()
    val videoEngineEnabled = MutableStateFlow(false)
    fun setVideoEngineEnabled(enabled: Boolean) { videoEngineEnabled.value = enabled }

    var currentLutId = MutableStateFlow("standard")
        private set

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentRecipeParams: StateFlow<ColorRecipeParams> = currentLutId.flatMapLatest { id ->
        contentRepository.lutManager.getColorRecipeParams(id)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ColorRecipeParams.DEFAULT
    )

    val currentEffectParams: StateFlow<EffectParams> = currentRecipeParams
        .map { it.toEffectParams() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, EffectParams.DEFAULT)

    val customPresets: StateFlow<List<com.hinnka.mycamera.model.CameraPreset>> = userPreferencesRepository.userPreferences
        .map { it.customPresets }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // 合并内置预设与用户自定义预设
    val allPresets: StateFlow<List<com.hinnka.mycamera.model.CameraPreset>> = userPreferencesRepository.userPreferences
        .map { prefs ->
            val deletedIds = prefs.deletedBuiltInIds.split(",").filter { it.isNotEmpty() }.toSet()
            val builtInsById = com.hinnka.mycamera.model.CameraPreset.BUILT_IN_PRESETS.associateBy { it.id }
            val orderedPresets = prefs.customPresets
                .filter { it.id !in deletedIds }
                .map { saved ->
                    builtInsById[saved.id]?.let { builtin ->
                        builtin.copy(
                            name = saved.name,
                            isFavorite = saved.isFavorite,
                            lutId = saved.lutId,
                            colorRecipe = saved.colorRecipe,
                            effects = saved.effects,
                            aspectRatio = saved.aspectRatio,
                            ultraHdrGainMapEnabled = saved.ultraHdrGainMapEnabled,
                            frameId = saved.frameId,
                            rawDcpId = saved.rawDcpId,
                            rawDcpIdsByLens = saved.rawDcpIdsByLens,
                            rawHncsProfileId = saved.rawHncsProfileId,
                            rawHncsRenderIntent = saved.rawHncsRenderIntent,
                            rawHncsFilmCurveMode = saved.rawHncsFilmCurveMode,
                            rawRenderingEngine = saved.rawRenderingEngine,
                            rawMaxSharpening = saved.rawMaxSharpening,
                            rawMaxNoiseReduction = saved.rawMaxNoiseReduction,
                            rawMaxChromaNoiseReduction = saved.rawMaxChromaNoiseReduction,
                            rawExposureCompensation = saved.rawExposureCompensation,
                            rawHighlightsAdjustment = saved.rawHighlightsAdjustment,
                            rawShadowsAdjustment = saved.rawShadowsAdjustment,
                            rawBlackPointCorrection = saved.rawBlackPointCorrection,
                            rawWhitePointCorrection = saved.rawWhitePointCorrection,
                            rawOppoMasterToneMap = saved.rawOppoMasterToneMap,
                            rawLumixPhotoStyle = saved.rawLumixPhotoStyle,
                            rawCanonPictureStyle = saved.rawCanonPictureStyle,
                            rawCanonExposureCompensationEv = saved.rawCanonExposureCompensationEv,
                            rawLumixColorMatchingEnabled = saved.rawLumixColorMatchingEnabled,
                            rawHncsColorMatchingEnabled = saved.rawHncsColorMatchingEnabled,
                            rawCanonUseR5ColorMatrix = saved.rawCanonUseR5ColorMatrix,
                            rawLeicaUseM9ColorMatrix = saved.rawLeicaUseM9ColorMatrix,
                            rawAgxBlackRelativeExposure = saved.rawAgxBlackRelativeExposure,
                            rawAgxWhiteRelativeExposure = saved.rawAgxWhiteRelativeExposure,
                            rawAgxToe = saved.rawAgxToe,
                            rawAgxShoulder = saved.rawAgxShoulder,
                            rawFilmicBlackRelativeExposure = saved.rawFilmicBlackRelativeExposure,
                            rawFilmicWhiteRelativeExposure = saved.rawFilmicWhiteRelativeExposure,
                            rawSpectralFilmStock = saved.rawSpectralFilmStock,
                            rawSpectralFilmPrint = saved.rawSpectralFilmPrint,
                            rawFotufilmDocument = saved.rawFotufilmDocument,
                            rawDROMode = saved.rawDROMode,
                            rawBaselineLutId = saved.rawBaselineLutId
                        )
                    } ?: saved
                }
            val orderedIds = orderedPresets.map { it.id }.toSet()
            val missingBuiltIns = com.hinnka.mycamera.model.CameraPreset.BUILT_IN_PRESETS
                .filter { it.id !in deletedIds && it.id !in orderedIds }
            val visibleBuiltInIds = com.hinnka.mycamera.model.CameraPreset.BUILT_IN_PRESETS
                .filter { it.id !in deletedIds }
                .map { it.id }
                .toSet()
            val hasCompleteSavedBuiltInOrder = visibleBuiltInIds.isNotEmpty() &&
                visibleBuiltInIds.all { builtInId -> orderedPresets.any { it.id == builtInId } }

            if (hasCompleteSavedBuiltInOrder) {
                orderedPresets + missingBuiltIns
            } else {
                val overridesById = orderedPresets.associateBy { it.id }
                val builtInsWithOverrides = com.hinnka.mycamera.model.CameraPreset.BUILT_IN_PRESETS
                    .filter { it.id !in deletedIds }
                    .map { builtin -> overridesById[builtin.id] ?: builtin }
                val customs = orderedPresets.filter { it.id !in visibleBuiltInIds }
                builtInsWithOverrides + customs
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.hinnka.mycamera.model.CameraPreset.BUILT_IN_PRESETS)

    val activePresetId: StateFlow<String?> = userPreferencesRepository.userPreferences
        .map { it.activePresetId }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val activePresetMatchState: StateFlow<ActivePresetMatchState?> = combine(
        combine(
            userPreferencesRepository.userPreferences,
            allPresets,
            isInitialized
        ) { prefs, presets, initialized ->
            if (initialized) prefs to presets else null
        },
        state.map { it.aspectRatio.name }.distinctUntilChanged(),
        currentLutId.flatMapLatest { lutId ->
            contentRepository.lutManager.getColorRecipeParams(lutId).map { recipe ->
                lutId to recipe
            }
        }
    ) { inputs, aspectRatio, (lutId, recipe) ->
        inputs?.let { (prefs, presets) ->
            ActivePresetMatchState(
                prefs = prefs,
                presets = presets,
                aspectRatio = aspectRatio,
                lutId = lutId,
                recipe = recipe,
                effects = recipe.toEffectParams()
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val presetApplyInProgress = MutableStateFlow(false)

    /**
     * Whether the settings currently in use differ from the selected preset.
     * Selection and matching are deliberately independent: editing a setting keeps the preset
     * selected and only marks it as modified.
     */
    val isActivePresetModified: StateFlow<Boolean> = combine(
        activePresetMatchState,
        presetApplyInProgress
    ) { matchState, applyingPreset ->
        if (applyingPreset) {
            false
        } else {
            val presetId = matchState?.prefs?.activePresetId ?: return@combine false
            val preset = matchState.presets.firstOrNull { it.id == presetId }
                ?: return@combine false
            !matchState.toPresetMatchSnapshot().matches(preset)
        }
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    var draftPreset: com.hinnka.mycamera.model.CameraPreset? = null

    private val presetApplyMutex = Mutex()
    private val presetMutationMutex = Mutex()
    private var presetApplyGeneration = 0L
    private var lutLoadJob: Job? = null
    private var lutLoadGeneration = 0L
    private var shootingModeSwitchJob: Job? = null

    fun prepareCurrentSettingsPresetDraft(name: String): com.hinnka.mycamera.model.CameraPreset {
        return createPresetFromCurrentSettings(
            id = UUID.randomUUID().toString(),
            name = name,
            isBuiltIn = false
        ).also {
            draftPreset = it
        }
    }

    private fun createPresetFromCurrentSettings(
        id: String,
        name: String,
        isBuiltIn: Boolean
    ): com.hinnka.mycamera.model.CameraPreset {
        return com.hinnka.mycamera.model.CameraPreset(
            id = id,
            name = name,
            lutId = CameraPreset.normalizeLutId(currentLutId.value),
            colorRecipe = currentRecipeParams.value.withoutIndependentEffects(),
            effects = currentRecipeParams.value.toEffectParams(),
            aspectRatio = state.value.aspectRatio.name,
            ultraHdrGainMapEnabled = ultraHdrGainMapEnabled.value,
            frameId = currentFrameId,
            rawDcpId = rawDcpId.value,
            rawDcpIdsByLens = userPreferences.value.rawDcpIdsByLens,
            rawHncsProfileId = rawHncsProfileId.value,
            rawHncsRenderIntent = rawHncsRenderIntent.value.assetValue,
            rawHncsFilmCurveMode = rawHncsFilmCurveMode.value.persistedValue,
            rawRenderingEngine = rawRenderingEngine.value.name,
            rawMaxSharpening = userPreferences.value.rawMaxSharpening,
            rawMaxNoiseReduction = userPreferences.value.rawMaxNoiseReduction,
            rawMaxChromaNoiseReduction = userPreferences.value.rawMaxChromaNoiseReduction,
            rawExposureCompensation = userPreferences.value.rawExposureCompensation,
            rawHighlightsAdjustment = userPreferences.value.rawHighlightsAdjustment,
            rawShadowsAdjustment = userPreferences.value.rawShadowsAdjustment,
            rawBlackPointCorrection = userPreferences.value.rawBlackPointCorrection,
            rawWhitePointCorrection = userPreferences.value.rawWhitePointCorrection,
            rawOppoMasterToneMap = rawToneMappingParameters.value.useOppoMasterToneMap,
            rawLumixPhotoStyle = rawToneMappingParameters.value.lumixPhotoStyle.assetName,
            rawCanonPictureStyle = rawToneMappingParameters.value.canonPictureStyle.persistedValue,
            rawCanonExposureCompensationEv = rawToneMappingParameters.value.canonExposureCompensationEv,
            rawLumixColorMatchingEnabled = rawToneMappingParameters.value.lumixColorMatchingEnabled,
            rawHncsColorMatchingEnabled = rawToneMappingParameters.value.hncsColorMatchingEnabled,
            rawCanonUseR5ColorMatrix = rawToneMappingParameters.value.canonUseR5ColorMatrix,
            rawLeicaUseM9ColorMatrix = rawToneMappingParameters.value.leicaUseM9ColorMatrix,
            rawAgxBlackRelativeExposure = rawToneMappingParameters.value.agxBlackRelativeExposure,
            rawAgxWhiteRelativeExposure = rawToneMappingParameters.value.agxWhiteRelativeExposure,
            rawAgxToe = rawToneMappingParameters.value.agxToe,
            rawAgxShoulder = rawToneMappingParameters.value.agxShoulder,
            rawFilmicBlackRelativeExposure = rawToneMappingParameters.value.filmicBlackRelativeExposure,
            rawFilmicWhiteRelativeExposure = rawToneMappingParameters.value.filmicWhiteRelativeExposure,
            rawSpectralFilmStock = rawSpectralFilmStock.value,
            rawSpectralFilmPrint = rawSpectralFilmPrint.value,
            rawFotufilmDocument = FotufilmDocumentStorage.encode(rawFotufilmDocument.value),
            rawDROMode = droMode.value,
            rawBaselineLutId = rawBaselineLutId.value,
            isBuiltIn = isBuiltIn
        )
    }

    fun getMergedRecipeParams(recipe: ColorRecipeParams = currentRecipeParams.value): ColorRecipeParams {
        return recipe
    }

    fun applyPreset(preset: com.hinnka.mycamera.model.CameraPreset?) {
        val applyGeneration = ++presetApplyGeneration
        presetApplyInProgress.value = true
        viewModelScope.launch {
            var applied = false
            try {
                presetApplyMutex.withLock {
                    if (presetApplyGeneration != applyGeneration) {
                        return@withLock
                    }
                    val currentState = state.value
                    val includePhotoSettings = currentState.captureMode == CaptureMode.PHOTO
                    val includeProfessionalSettings = includePhotoSettings &&
                        useRaw.value && currentState.isRawSupported
                    PLog.d(
                        TAG,
                        "Applying preset id=${preset?.id}, captureMode=${currentState.captureMode}, " +
                            "professional=$includeProfessionalSettings, frameId=${preset?.frameId}"
                    )
                    applyCameraFeatureUpdate(
                        preset.toCameraFeatureUpdate(
                            includePhotoSettings = includePhotoSettings,
                            includeProfessionalSettings = includeProfessionalSettings
                        ).copy(activePresetId = SettingValue(preset?.id))
                    )
                    applied = true
                }

                if (applied && presetApplyGeneration == applyGeneration) {
                    activePresetMatchState.filterNotNull().first { matchState ->
                        matchState.prefs.activePresetId == preset?.id &&
                            (preset == null || matchState.toPresetMatchSnapshot().matches(preset))
                    }
                }
            } finally {
                if (presetApplyGeneration == applyGeneration) {
                    presetApplyInProgress.value = false
                }
            }
        }
    }

    private fun com.hinnka.mycamera.model.CameraPreset?.toCameraFeatureUpdate(
        includePhotoSettings: Boolean,
        includeProfessionalSettings: Boolean
    ): CameraFeatureUpdate {
        val ratio = if (includePhotoSettings) {
            try {
                AspectRatio.valueOf(this?.aspectRatio ?: AspectRatio.RATIO_4_3.name)
            } catch (e: Exception) {
                PLog.e(TAG, "Failed to apply preset aspectRatio: ${this?.aspectRatio}", e)
                AspectRatio.RATIO_4_3
            }
        } else {
            null
        }
        val sharedUpdate = CameraFeatureUpdate(
            lutId = SettingValue(CameraPreset.normalizeLutId(this?.lutId)),
            colorRecipe = SettingValue(this?.colorRecipe ?: ColorRecipeParams.DEFAULT),
            effects = SettingValue(this?.effects ?: EffectParams.DEFAULT),
            aspectRatio = ratio?.let(::SettingValue),
            frameId = SettingValue(this?.frameId)
        )
        if (!includeProfessionalSettings) return sharedUpdate

        return sharedUpdate.copy(
            ultraHdrGainMapEnabled = SettingValue(this?.ultraHdrGainMapEnabled ?: false),
            rawDcpId = SettingValue(this?.rawDcpId),
            rawDcpIdsByLens = SettingValue(this?.rawDcpIdsByLens ?: emptyMap()),
            rawHncsProfileId = SettingValue(HncsProfileManager.DEFAULT_PROFILE_ID),
            rawHncsRenderIntent = SettingValue(HncsRenderIntent.Standard),
            rawHncsFilmCurveMode = SettingValue(
                HncsFilmCurveMode.fromPersistedValue(this?.rawHncsFilmCurveMode)
            ),
            rawRenderingEngine = SettingValue(
                RawRenderingEngine.fromPersistedName(this?.rawRenderingEngine)
            ),
            rawMaxSharpening = SettingValue(
                this?.rawMaxSharpening ?: RawSharpeningDefaults.DEFAULT_STRENGTH
            ),
            rawMaxNoiseReduction = SettingValue(
                this?.rawMaxNoiseReduction ?: RawDenoiseDefaults.RAW_MAX_LUMA_STRENGTH
            ),
            rawMaxChromaNoiseReduction = SettingValue(
                this?.rawMaxChromaNoiseReduction
                    ?: RawDenoiseDefaults.RAW_MAX_CHROMA_STRENGTH
            ),
            rawExposureCompensation = SettingValue(this?.rawExposureCompensation ?: 0f),
            rawHighlightsAdjustment = SettingValue(this?.rawHighlightsAdjustment ?: 0f),
            rawShadowsAdjustment = SettingValue(this?.rawShadowsAdjustment ?: 0f),
            rawBlackPointCorrection = SettingValue(this?.rawBlackPointCorrection ?: 0f),
            rawWhitePointCorrection = SettingValue(this?.rawWhitePointCorrection ?: 0f),
            rawOppoMasterToneMap = SettingValue(this?.rawOppoMasterToneMap ?: false),
            rawLumixPhotoStyle = SettingValue(LumixPhotoStyle.fromPersistedValue(this?.rawLumixPhotoStyle)),
            rawCanonPictureStyle = SettingValue(CanonPictureStyle.fromPersistedValue(this?.rawCanonPictureStyle)),
            rawCanonExposureCompensationEv = SettingValue(this?.rawCanonExposureCompensationEv
                ?: RawToneMappingParameters.CANON_EXPOSURE_COMPENSATION_DEFAULT),
            rawLumixColorMatchingEnabled = SettingValue(this?.rawLumixColorMatchingEnabled ?: true),
            rawHncsColorMatchingEnabled = SettingValue(this?.rawHncsColorMatchingEnabled ?: true),
            rawCanonUseR5ColorMatrix = SettingValue(this?.rawCanonUseR5ColorMatrix ?: false),
            rawLeicaUseM9ColorMatrix = SettingValue(this?.rawLeicaUseM9ColorMatrix ?: false),
            rawEngineToneCurve = SettingValue(
                this?.rawEngineToneCurve() ?: RawToneMappingParameters.DEFAULT
            ),
            rawSpectralFilmStock = SettingValue(this?.rawSpectralFilmStock),
            rawSpectralFilmPrint = SettingValue(this?.rawSpectralFilmPrint),
            rawFotufilmDocument = SettingValue(
                FotufilmDocumentStorage.decode(this?.rawFotufilmDocument, "preset ${this?.id}")
            ),
            droMode = SettingValue(
                this?.rawDROMode ?: RawProcessingPreferences.DROMode.OFF.name
            ),
            rawBaselineLutId = SettingValue(this?.rawBaselineLutId)
        )
    }

    private suspend fun applyCameraFeatureUpdate(
        update: CameraFeatureUpdate,
        reconfigureCameraIfNeeded: Boolean = true,
    ) {
        val prefs = userPreferencesRepository.userPreferences.first()
        var desiredUseRaw = prefs.useRaw
        var desiredUseJpgMax = prefs.useJpgMax
        var desiredUseRawMax = prefs.useRawMax
        var desiredUseMultipleExposure = prefs.useMultipleExposure
        var desiredRawRenderingEngine = prefs.rawRenderingEngine

        update.useRaw?.let { desiredUseRaw = it.value }
        update.useJpgMax?.let { desiredUseJpgMax = it.value }
        update.useRawMax?.let { desiredUseRawMax = it.value }
        update.useMultipleExposure?.let { desiredUseMultipleExposure = it.value }
        update.rawRenderingEngine?.let { desiredRawRenderingEngine = it.value }

        if (update.useRaw?.value == true) {
            desiredUseMultipleExposure = false
            // RAW shooting and RAW burst fusion are independent settings.
        } else if (update.useRaw?.value == false) {
            desiredUseRawMax = false
        }
        if (update.useJpgMax?.value == true) {
            desiredUseRaw = false
            desiredUseMultipleExposure = false
            desiredUseRawMax = false
        }
        if (update.useRawMax?.value == true) {
            desiredUseRaw = true
            desiredUseMultipleExposure = false
        }
        if (update.useMultipleExposure?.value == true) {
            desiredUseRaw = false
            desiredUseJpgMax = false
            desiredUseRawMax = false
        }
        val activeUseJpgMax = desiredUseJpgMax && !desiredUseRaw
        if (activeUseJpgMax && prefs.useLivePhoto) {
            cameraController.setUseLivePhoto(false)
            userPreferencesRepository.saveUseLivePhoto(false)
        }
        val desiredMultiFrameOutputScale = resolveMultiFrameOutputScale(
            useJpgMax = activeUseJpgMax,
            useRawMax = desiredUseRawMax,
            rawMaxOutputScale = prefs.rawMaxOutputScale,
            jpgMultiFrameDenoiseOutputScale = prefs.jpgMultiFrameDenoiseOutputScale,
        )
        val currentState = state.value
        val targetCaptureMode = update.captureMode?.value ?: currentState.captureMode
        val captureModeWillChange = targetCaptureMode != currentState.captureMode
        val persistLutInVideoSlot = currentState.captureMode == CaptureMode.VIDEO &&
            prefs.separateVideoLutEnabled && update.lutId != null
        val targetAspectRatio = update.aspectRatio?.value
        val needsCameraReopen =
            targetAspectRatio != null && targetAspectRatio != currentState.aspectRatio ||
                desiredUseRaw != prefs.useRaw ||
                desiredMultiFrameOutputScale != currentState.multiFrameOutputScale

        update.colorRecipe?.let {
            val recipeLutId = if (update.lutId != null) {
                update.lutId.value ?: "none"
            } else {
                currentLutId.value
            }
            val recipeWithEffects = update.effects?.value?.applyTo(it.value) ?: it.value
            contentRepository.lutManager.saveColorRecipeParams(recipeLutId, recipeWithEffects)
        }

        update.lutId?.let {
            setLut(it.value, persist = false)
        }

        update.aspectRatio?.let {
            cameraController.setAspectRatio(it.value)
        }

        if (desiredUseMultipleExposure != prefs.useMultipleExposure) {
            if (!desiredUseMultipleExposure) {
                cancelMultipleExposureSession()
            }
            multipleExposureState = multipleExposureState.copy(enabled = desiredUseMultipleExposure)
        }
        if (update.useMultipleExposure != null || desiredUseMultipleExposure != prefs.useMultipleExposure) {
            cameraController.setUseMultipleExposure(desiredUseMultipleExposure)
        }

        if (update.useRaw != null || desiredUseRaw != prefs.useRaw) {
            cameraController.setUseRaw(
                enabled = desiredUseRaw,
                // This transaction already performs the required reopen below. Avoid a
                // duplicate restart, but still let same-value calls repair a stale output.
                reconfigureCaptureOutputIfNeeded =
                    reconfigureCameraIfNeeded &&
                        !needsCameraReopen &&
                        !captureModeWillChange
            )
        }
        if (update.useJpgMax != null || update.useRawMax != null ||
            desiredMultiFrameOutputScale != currentState.multiFrameOutputScale
        ) {
            cameraController.setMultiFrameOutputScale(desiredMultiFrameOutputScale)
        }

        if (captureModeWillChange) {
            cameraController.setCaptureMode(targetCaptureMode)
            if (targetCaptureMode == CaptureMode.VIDEO) {
                // Photo mode disables the active Log profile without clearing the saved selection.
                cameraController.setVideoLogProfile(prefs.videoLogProfile)
            }
            resolveLutIdForMode(prefs, targetCaptureMode)?.let(::applyLut)
            currentSurfaceTexture = null
            cameraController.closeCamera()
        }

        update.frameId?.let {
            currentFrameId = it.value
        }

        if (update.rawDcpId != null || update.rawDcpIdsByLens != null) {
            val targetPrefs = prefs.copy(
                rawDcpId = update.rawDcpId?.value ?: prefs.rawDcpId,
                rawDcpIdsByLens = update.rawDcpIdsByLens?.value ?: prefs.rawDcpIdsByLens
            )
            prewarmRawDcp(targetPrefs.rawDcpIdForLens(currentState.currentCameraId))
        }

        val rawToneMappingUpdate = if (
            update.rawProfileToneMapMode != null ||
            update.rawOppoMasterToneMap != null || update.rawLumixPhotoStyle != null ||
            update.rawCanonPictureStyle != null || update.rawCanonExposureCompensationEv != null ||
            update.rawLumixColorMatchingEnabled != null || update.rawHncsColorMatchingEnabled != null ||
            update.rawCanonUseR5ColorMatrix != null ||
            update.rawLeicaUseM9ColorMatrix != null || update.rawEngineToneCurve != null
        ) {
            var toneMappingParameters = prefs.rawToneMappingParameters
            update.rawProfileToneMapMode?.let {
                toneMappingParameters = toneMappingParameters.withProfileToneMapMode(it.value)
            }
            update.rawOppoMasterToneMap?.let {
                toneMappingParameters = toneMappingParameters.withOppoMasterToneMap(it.value)
            }
            update.rawLumixPhotoStyle?.let {
                toneMappingParameters = toneMappingParameters.copy(lumixPhotoStyle = it.value)
            }
            update.rawCanonPictureStyle?.let {
                toneMappingParameters = toneMappingParameters.copy(canonPictureStyle = it.value)
            }
            update.rawCanonExposureCompensationEv?.let {
                toneMappingParameters = toneMappingParameters.copy(canonExposureCompensationEv = it.value)
            }
            update.rawLumixColorMatchingEnabled?.let {
                toneMappingParameters = toneMappingParameters.copy(lumixColorMatchingEnabled = it.value)
            }
            update.rawHncsColorMatchingEnabled?.let {
                toneMappingParameters = toneMappingParameters.copy(hncsColorMatchingEnabled = it.value)
            }
            update.rawCanonUseR5ColorMatrix?.let {
                toneMappingParameters = toneMappingParameters.copy(canonUseR5ColorMatrix = it.value)
            }
            update.rawLeicaUseM9ColorMatrix?.let {
                toneMappingParameters = toneMappingParameters.copy(leicaUseM9ColorMatrix = it.value)
            }
            update.rawEngineToneCurve?.let {
                toneMappingParameters = toneMappingParameters.withEngineToneCurveFrom(it.value)
            }
            PreferenceUpdateValue(toneMappingParameters)
        } else {
            null
        }

        userPreferencesRepository.saveCameraFeaturePreferences(
            CameraFeaturePreferencesUpdate(
                captureMode = update.captureMode?.let { PreferenceUpdateValue(it.value) },
                lutId = if (persistLutInVideoSlot) {
                    null
                } else {
                    update.lutId?.let { PreferenceUpdateValue(it.value) }
                },
                effects = update.effects?.let { PreferenceUpdateValue(it.value) },
                aspectRatio = update.aspectRatio?.let { PreferenceUpdateValue(it.value.name) },
                useRaw = if (update.useRaw != null || desiredUseRaw != prefs.useRaw) {
                    PreferenceUpdateValue(desiredUseRaw)
                } else {
                    null
                },
                useJpgMax = if (update.useJpgMax != null || desiredUseJpgMax != prefs.useJpgMax) {
                    PreferenceUpdateValue(desiredUseJpgMax)
                } else {
                    null
                },
                useRawMax = if (update.useRawMax != null || desiredUseRawMax != prefs.useRawMax) {
                    PreferenceUpdateValue(desiredUseRawMax)
                } else {
                    null
                },
                ultraHdrGainMapEnabled = update.ultraHdrGainMapEnabled?.let {
                    PreferenceUpdateValue(it.value)
                },
                useMultipleExposure = if (update.useMultipleExposure != null ||
                    desiredUseMultipleExposure != prefs.useMultipleExposure
                ) {
                    PreferenceUpdateValue(desiredUseMultipleExposure)
                } else {
                    null
                },
                frameId = update.frameId?.let { PreferenceUpdateValue(it.value) },
                rawDcpId = update.rawDcpId?.let { PreferenceUpdateValue(it.value) },
                rawDcpIdsByLens = update.rawDcpIdsByLens?.let { PreferenceUpdateValue(it.value) },
                rawHncsProfileId = update.rawHncsProfileId?.let {
                    PreferenceUpdateValue(it.value)
                },
                rawHncsRenderIntent = update.rawHncsRenderIntent?.let {
                    PreferenceUpdateValue(it.value)
                },
                rawHncsFilmCurveMode = update.rawHncsFilmCurveMode?.let {
                    PreferenceUpdateValue(it.value)
                },
                rawRenderingEngine = if (update.rawRenderingEngine != null ||
                    desiredRawRenderingEngine != prefs.rawRenderingEngine
                ) {
                    PreferenceUpdateValue(desiredRawRenderingEngine)
                } else {
                    null
                },
                rawToneMappingParameters = rawToneMappingUpdate,
                rawMaxSharpening = update.rawMaxSharpening?.let {
                    PreferenceUpdateValue(it.value)
                },
                rawMaxNoiseReduction = update.rawMaxNoiseReduction?.let {
                    PreferenceUpdateValue(it.value)
                },
                rawMaxChromaNoiseReduction = update.rawMaxChromaNoiseReduction?.let {
                    PreferenceUpdateValue(it.value)
                },
                rawExposureCompensation = update.rawExposureCompensation?.let {
                    PreferenceUpdateValue(it.value)
                },
                rawHighlightsAdjustment = update.rawHighlightsAdjustment?.let {
                    PreferenceUpdateValue(it.value)
                },
                rawShadowsAdjustment = update.rawShadowsAdjustment?.let {
                    PreferenceUpdateValue(it.value)
                },
                rawBlackPointCorrection = update.rawBlackPointCorrection?.let {
                    PreferenceUpdateValue(it.value)
                },
                rawWhitePointCorrection = update.rawWhitePointCorrection?.let {
                    PreferenceUpdateValue(it.value)
                },
                rawSpectralFilmStock = update.rawSpectralFilmStock?.let { PreferenceUpdateValue(it.value) },
                rawSpectralFilmPrint = update.rawSpectralFilmPrint?.let { PreferenceUpdateValue(it.value) },
                rawFotufilmDocument = update.rawFotufilmDocument?.let {
                    PreferenceUpdateValue(FotufilmDocumentStorage.encode(it.value))
                },
                droMode = update.droMode?.let {
                    PreferenceUpdateValue(RawProcessingPreferences.DROMode.fromPersistedName(it.value).name)
                },
                rawBaselineLutId = update.rawBaselineLutId?.let { PreferenceUpdateValue(it.value) },
                activePresetId = update.activePresetId?.let { PreferenceUpdateValue(it.value) }
            )
        )
        if (persistLutInVideoSlot) {
            userPreferencesRepository.saveVideoLutConfig(update.lutId.value ?: "none")
        }

        if (reconfigureCameraIfNeeded && needsCameraReopen && !captureModeWillChange) {
            reopenCamera()
        }
    }

    private fun resolveCaptureRawRenderingEngine(userPrefs: UserPreferences?): RawRenderingEngine {
        return userPrefs?.rawRenderingEngine ?: RawRenderingEngine.AdobeCurve
    }

    private fun resolveCaptureRawToneMappingParameters(
        userPrefs: UserPreferences?
    ): RawToneMappingParameters {
        val base = userPrefs?.rawToneMappingParameters ?: RawToneMappingParameters.DEFAULT
        return base.withPhotonHdr(true)
    }

    fun savePreset(preset: com.hinnka.mycamera.model.CameraPreset) {
        viewModelScope.launch {
            val resolvedPreset = presetMutationMutex.withLock {
                val normalizedPreset = preset.normalizedForPersistence()
                val currentList = userPreferencesRepository.userPreferences
                    .first()
                    .customPresets
                    .toMutableList()
                val index = currentList.indexOfFirst { it.id == normalizedPreset.id }
                if (index >= 0) {
                    currentList[index] = normalizedPreset
                } else {
                    currentList.add(normalizedPreset)
                }
                userPreferencesRepository.saveCustomPresets(currentList)
                normalizedPreset
            }
            applyPreset(resolvedPreset)
        }
    }

    /** Change management metadata without applying a different camera configuration. */
    fun updatePresetIdentity(presetId: String, name: String? = null, favorite: Boolean? = null, duplicate: Boolean = false) {
        viewModelScope.launch {
            presetMutationMutex.withLock {
                val list = userPreferencesRepository.userPreferences.first().customPresets.toMutableList()
                val source = allPresets.value.firstOrNull { it.id == presetId } ?: return@withLock
                val updated = source.copy(
                    id = if (duplicate) java.util.UUID.randomUUID().toString() else source.id,
                    name = name?.trim()?.takeIf { it.isNotEmpty() } ?: source.name,
                    isFavorite = favorite ?: source.isFavorite,
                    isBuiltIn = if (duplicate) false else source.isBuiltIn
                ).normalizedForPersistence()
                val index = list.indexOfFirst { it.id == updated.id }
                if (index >= 0) list[index] = updated else list.add(updated)
                userPreferencesRepository.saveCustomPresets(list)
            }
        }
    }

    fun resetActivePreset() {
        val presetId = activePresetId.value ?: return
        val preset = allPresets.value.firstOrNull { it.id == presetId } ?: return
        applyPreset(preset)
    }

    fun saveCurrentSettingsToActivePreset() {
        val presetId = activePresetId.value ?: return
        val preset = allPresets.value.firstOrNull { it.id == presetId } ?: return
        savePreset(
            createPresetFromCurrentSettings(
                id = preset.id,
                name = preset.name,
                isBuiltIn = preset.isBuiltIn
            ).copy(isFavorite = preset.isFavorite)
        )
    }

    /** Preview creative grading on a chosen rendered reference, separate from RAW development. */
    suspend fun renderPresetReference(preset: com.hinnka.mycamera.model.CameraPreset, source: Bitmap): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val metadata = MediaMetadata(lutId = preset.lutId,
                colorRecipeParams = preset.effects.applyTo(preset.colorRecipe))
            val rendered = contentRepository.photoProcessor.processBitmap(getApplication(), "recipe-reference-${preset.id}",
                source, metadata, useComputationalAperture = false, applyFrameWatermark = false)
            if (rendered === source) source.copy(Bitmap.Config.ARGB_8888, false) else rendered
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (failure: Exception) { PLog.w("CameraViewModel", "Recipe reference failed", failure); null }
    }

    suspend fun exportPresetPackage(
        preset: com.hinnka.mycamera.model.CameraPreset,
        displayName: String,
    ): ByteArray? = withContext(Dispatchers.IO) {
        presetPackageManager.exportPreset(preset, displayName)
    }

    suspend fun importPresetPackage(uri: Uri): Boolean = presetMutationMutex.withLock {
        withContext(NonCancellable) transaction@{
            val imported = withContext(Dispatchers.IO) {
                presetPackageManager.importPreset(uri)
            } ?: return@transaction false

            try {
                val currentPresets = userPreferencesRepository.userPreferences.first().customPresets
                userPreferencesRepository.saveCustomPresets(currentPresets + imported.preset)
            } catch (e: Exception) {
                withContext(Dispatchers.IO) {
                    presetPackageManager.rollbackImport(imported)
                    runCatching { contentRepository.refreshCustomContent() }
                }
                PLog.e(TAG, "Failed to persist imported preset: $uri", e)
                return@transaction false
            }

            withContext(Dispatchers.IO) {
                runCatching { contentRepository.refreshCustomContent() }
                    .onFailure { PLog.e(TAG, "Failed to refresh imported preset resources", it) }
            }
            true
        }
    }

    fun deletePreset(presetId: String) {
        viewModelScope.launch {
            presetMutationMutex.withLock {
                val currentList = userPreferencesRepository.userPreferences
                    .first()
                    .customPresets
                    .toMutableList()
                currentList.removeAll { it.id == presetId }
                userPreferencesRepository.saveCustomPresets(currentList)

                // 如果删除的是内置预设，将其加入 deletedBuiltInIds
                val isBuiltIn = com.hinnka.mycamera.model.CameraPreset.BUILT_IN_PRESETS.any { it.id == presetId }
                if (isBuiltIn) {
                    val currentDeleted = userPreferencesRepository.userPreferences.first().deletedBuiltInIds
                    val deletedList = currentDeleted.split(",").filter { it.isNotEmpty() }.toMutableList()
                    if (presetId !in deletedList) {
                        deletedList.add(presetId)
                        userPreferencesRepository.saveDeletedBuiltInIds(deletedList.joinToString(","))
                    }
                }

                if (activePresetId.value == presetId) {
                    userPreferencesRepository.saveActivePresetId(null)
                }
            }
        }
    }

    fun resetToDefaultPresets() {
        viewModelScope.launch {
            presetMutationMutex.withLock {
                userPreferencesRepository.saveDeletedBuiltInIds("")
                val currentList = userPreferencesRepository.userPreferences
                    .first()
                    .customPresets
                    .toMutableList()
                currentList.removeAll { it.isBuiltIn || it.id.startsWith("builtin_") }
                userPreferencesRepository.saveCustomPresets(currentList)
            }
        }
    }

    fun savePresetOrder(presets: List<com.hinnka.mycamera.model.CameraPreset>) {
        viewModelScope.launch {
            presetMutationMutex.withLock {
                val latestPresets = userPreferencesRepository.userPreferences.first().customPresets
                val latestById = latestPresets.associateBy { it.id }
                val orderedIds = presets.map { it.id }.toSet()
                val orderedPresets = presets.map { preset -> latestById[preset.id] ?: preset }
                val concurrentlyAddedPresets = latestPresets.filter { it.id !in orderedIds }
                userPreferencesRepository.saveCustomPresets(orderedPresets + concurrentlyAddedPresets)
            }
        }
    }

    private fun ActivePresetMatchState.toPresetMatchSnapshot(): PresetMatchSnapshot {
        return PresetMatchSnapshot(
            lutId = CameraPreset.normalizeLutId(lutId),
            colorRecipe = recipe,
            effects = effects,
            captureMode = prefs.captureMode,
            aspectRatio = aspectRatio,
            isProfessionalMode = prefs.captureMode == CaptureMode.PHOTO &&
                prefs.useRaw && prefs.useRawMax,
            ultraHdrGainMapEnabled = prefs.ultraHdrGainMapEnabled,
            frameId = prefs.frameId,
            rawDcpId = prefs.rawDcpId,
            rawDcpIdsByLens = prefs.rawDcpIdsByLens,
            rawHncsProfileId = prefs.rawHncsProfileId,
            rawHncsRenderIntent = prefs.rawHncsRenderIntent,
            rawHncsFilmCurveMode = prefs.rawHncsFilmCurveMode,
            rawRenderingEngine = prefs.rawRenderingEngine,
            rawMaxSharpening = prefs.rawMaxSharpening,
            rawMaxNoiseReduction = prefs.rawMaxNoiseReduction,
            rawMaxChromaNoiseReduction = prefs.rawMaxChromaNoiseReduction,
            rawExposureCompensation = prefs.rawExposureCompensation,
            rawHighlightsAdjustment = prefs.rawHighlightsAdjustment,
            rawShadowsAdjustment = prefs.rawShadowsAdjustment,
            rawBlackPointCorrection = prefs.rawBlackPointCorrection,
            rawWhitePointCorrection = prefs.rawWhitePointCorrection,
            rawOppoMasterToneMap = prefs.rawToneMappingParameters.useOppoMasterToneMap,
            rawLumixPhotoStyle = prefs.rawToneMappingParameters.lumixPhotoStyle,
            rawCanonPictureStyle = prefs.rawToneMappingParameters.canonPictureStyle,
            rawCanonExposureCompensationEv = prefs.rawToneMappingParameters.canonExposureCompensationEv,
            rawLumixColorMatchingEnabled = prefs.rawToneMappingParameters.lumixColorMatchingEnabled,
            rawHncsColorMatchingEnabled = prefs.rawToneMappingParameters.hncsColorMatchingEnabled,
            rawCanonUseR5ColorMatrix = prefs.rawToneMappingParameters.canonUseR5ColorMatrix,
            rawLeicaUseM9ColorMatrix = prefs.rawToneMappingParameters.leicaUseM9ColorMatrix,
            rawEngineToneCurve = prefs.rawToneMappingParameters.engineToneCurve(),
            rawSpectralFilmStock = prefs.rawSpectralFilmStock,
            rawSpectralFilmPrint = prefs.rawSpectralFilmPrint,
            rawFotufilmDocument = FotufilmDocumentStorage.decode(prefs.rawFotufilmDocument, "preferences"),
            rawDROMode = prefs.droMode,
            rawBaselineLutId = prefs.rawBaselineLutId
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentBaselineRecipeParams: StateFlow<ColorRecipeParams> =
        userPreferencesRepository.userPreferences.flatMapLatest { prefs ->
            val target = resolvePreviewBaselineTarget(prefs)
            val lutId = prefs.rawBaselineLutId.takeIf { target != null }
            if (target == null || lutId == null) {
                flowOf(ColorRecipeParams.DEFAULT)
            } else {
                contentRepository.lutManager.getColorRecipeParams(lutId, target)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ColorRecipeParams.DEFAULT
        )

    var availableLutList: List<LutInfo> by mutableStateOf(emptyList())
        private set

    val selectableLutList: List<LutInfo>
        get() {
            val profile = state.value.videoConfig.logProfile
            return if (state.value.captureMode == CaptureMode.VIDEO && profile.isEnabled) {
                availableLutList.filter {
                    it.id == "none" || profile.matchesLut(it.inputCurve, it.inputColorSpace)
                }
            } else availableLutList
        }

    // LUT 预览图缓存（lutId -> 预览Bitmap）
    var previewThumbnail by mutableStateOf<Bitmap?>(null)

    // 是否正在生成预览
    private var isGeneratingPreviews = false

    // 边框相关状态
    var currentFrameId: String? by mutableStateOf(null)
        private set

    var showHistogram by mutableStateOf(true)
        private set

    var availableFrameList: List<FrameInfo> by mutableStateOf(emptyList())
        private set

    var zoomRatioByMain by mutableFloatStateOf(1f)
    var isZooming by mutableStateOf(false)
    val globalMinZoom: Float
        get() = state.value.availableCameras.filter { it.lensType != LensType.FRONT }.minOfOrNull { it.minZoom * it.displayIntrinsicZoomRatio } ?: 1f
    val globalMaxZoom: Float
        get() = state.value.availableCameras.filter { it.lensType != LensType.FRONT }.maxOfOrNull { it.maxZoom * it.displayIntrinsicZoomRatio } ?: 20f

    // 付费弹窗状态
    var showPaymentDialog by mutableStateOf(false)

    var isExpanded by mutableStateOf(false)

    private var startupPrewarmJob: Job? = null
    private var startupPrewarmAttempted = false

    // 新增设置项 StateFlow
    val showLevelIndicator: Flow<Boolean> = userPreferencesRepository.userPreferences.map { it.showLevelIndicator }
    val previewAssistSettings: Flow<PreviewAssistSettings> = userPreferencesRepository.userPreferences
        .map { it.previewAssistSettings }
    val focusPeakingEnabled: Flow<Boolean> = userPreferencesRepository.userPreferences.map { it.focusPeakingEnabled }
    val eyeFocusEnabled: StateFlow<Boolean> = userPreferencesRepository.userPreferences
        .map { it.eyeFocusEnabled }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val shutterSoundEnabled: Flow<Boolean> = userPreferencesRepository.userPreferences.map { it.shutterSoundEnabled }
    val colorPaletteEnabled: StateFlow<Boolean> = userPreferencesRepository.userPreferences
        .map { it.colorPaletteEnabled }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val autoRotateEnabled: StateFlow<Boolean> = userPreferencesRepository.userPreferences
        .map { it.autoRotateEnabled }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val vibrationEnabled: Flow<Boolean> = userPreferencesRepository.userPreferences.map { it.vibrationEnabled }
    val keepScreenOn: Flow<Boolean> = userPreferencesRepository.userPreferences.map { it.keepScreenOn }
    val windowScreenBrightness: StateFlow<Float?> = userPreferencesRepository.userPreferences
        .map { it.windowScreenBrightness }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val volumeKeyAction: StateFlow<VolumeKeyAction> =
        userPreferencesRepository.userPreferences.map { it.volumeKeyAction }
            .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = VolumeKeyAction.NONE)
    val autoSaveAfterCapture: Flow<Boolean> = userPreferencesRepository.userPreferences.map { it.autoSaveAfterCapture }
    val photoSavePath: StateFlow<PhotoSavePath> = userPreferencesRepository.userPreferences
        .map { it.photoSavePath }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PhotoSavePath.DCIM_PHOTON)
    val photoSaveTreeUri: StateFlow<String?> = userPreferencesRepository.userPreferences
        .map { it.photoSaveTreeUri }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val topSheetAspectRatios: StateFlow<List<AspectRatio>> = userPreferencesRepository.userPreferences
        .map { it.topSheetAspectRatios }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AspectRatio.defaultTopSheetRatios)
    val customAspectRatios: StateFlow<List<AspectRatio>> = userPreferencesRepository.userPreferences
        .map { it.customAspectRatios }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val availablePhotoAspectRatios: StateFlow<List<AspectRatio>> = userPreferencesRepository.userPreferences
        .map { AspectRatio.entries + it.customAspectRatios }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AspectRatio.entries)
    val nrLevel: StateFlow<Int> = userPreferencesRepository.userPreferences
        .map { it.nrLevel }
        .stateIn(viewModelScope, SharingStarted.Eagerly, NoiseReductionLevel.DEFAULT)
    val useRaw: StateFlow<Boolean> = userPreferencesRepository.userPreferences
        .map { it.useRaw }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val edgeLevel: StateFlow<Int> = userPreferencesRepository.userPreferences
        .map { it.edgeLevel }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1)
    val vendorCaptureSettingsByLens: StateFlow<VendorCaptureSettingsByLens> =
        userPreferencesRepository.userPreferences
            .map { it.vendorCaptureSettingsByLens }
            .stateIn(viewModelScope, SharingStarted.Eagerly, VendorCaptureSettingsByLens.Empty)
    val customVendorKeySettings: StateFlow<CustomVendorKeySettings> =
        userPreferencesRepository.userPreferences
            .map { it.customVendorKeySettings }
            .stateIn(viewModelScope, SharingStarted.Eagerly, CustomVendorKeySettings.Empty)
    val rawRenderingEngine: StateFlow<RawRenderingEngine> = userPreferencesRepository.userPreferences
        .map { it.rawRenderingEngine }
        .stateIn(viewModelScope, SharingStarted.Eagerly, RawRenderingEngine.AdobeCurve)
    val rawToneMappingParameters: StateFlow<RawToneMappingParameters> = userPreferencesRepository.userPreferences
        .map { it.rawToneMappingParameters }
        .stateIn(viewModelScope, SharingStarted.Eagerly, RawToneMappingParameters.DEFAULT)
    val rawMaxSharpening: StateFlow<Float> = userPreferencesRepository.userPreferences
        .map { it.rawMaxSharpening }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            RawSharpeningDefaults.DEFAULT_STRENGTH,
        )
    val rawMaxQualityTuningEnabled: StateFlow<Boolean> = userPreferencesRepository.userPreferences
        .map { it.rawMaxQualityTuningEnabled }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            PhotonSensorSizeTuning.DEFAULT_RAW_MAX_QUALITY_TUNING_ENABLED,
        )

    val rawMaxNoiseReduction: StateFlow<Float> = userPreferencesRepository.userPreferences
        .map { it.rawMaxNoiseReduction }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            RawDenoiseDefaults.RAW_MAX_LUMA_STRENGTH,
        )
    val rawMaxChromaNoiseReduction: StateFlow<Float> = userPreferencesRepository.userPreferences
        .map { it.rawMaxChromaNoiseReduction }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            RawDenoiseDefaults.RAW_MAX_CHROMA_STRENGTH,
        )
    val rawSpectralFilmStock: StateFlow<String?> = userPreferencesRepository.userPreferences
        .map { it.rawSpectralFilmStock }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val rawSpectralFilmPrint: StateFlow<String?> = userPreferencesRepository.userPreferences
        .map { it.rawSpectralFilmPrint }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val rawFotufilmDocument: StateFlow<FotufilmDocument?> = userPreferencesRepository.userPreferences
        .map { it.rawFotufilmDocument }
        .distinctUntilChanged()
        .map { FotufilmDocumentStorage.decode(it, "preferences") }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val rawSpectralFilmSelection: StateFlow<SpectralFilmSelection?> = userPreferencesRepository.userPreferences
        .map { prefs ->
            prefs.rawSpectralFilmStock?.let { stock ->
                SpectralFilmSelection(
                    id = stock,
                    tuning = prefs.rawSpectralFilmTuningsByStock[stock] ?: SpectralFilmTuning.DEFAULT
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val photoQuality: Flow<Int> = userPreferencesRepository.userPreferences.map { it.photoQuality }
    val useHeicExport: Flow<Boolean> = userPreferencesRepository.userPreferences.map { it.useHeicExport }
    val useJpeg444Export: Flow<Boolean> = userPreferencesRepository.userPreferences.map { it.useJpeg444Export }

    val defaultFocalLength: Flow<Float> = userPreferencesRepository.userPreferences.map { it.defaultFocalLength }
    val zoomDisplayMode: StateFlow<ZoomDisplayMode> = userPreferencesRepository.userPreferences
        .map { ZoomDisplayMode.fromPersistedName(it.zoomDisplayMode) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ZoomDisplayMode.FOCAL_LENGTH)
    val customFocalLengths: Flow<List<Float>> = userPreferencesRepository.userPreferences.map { it.customFocalLengths }
    val hiddenFocalLengths: Flow<List<Float>> = userPreferencesRepository.userPreferences.map { it.hiddenFocalLengths }
    val customLensIds: Flow<List<String>> = userPreferencesRepository.userPreferences.map { it.customLensIds }
    val lensIdBlacklist: Flow<List<String>> = userPreferencesRepository.userPreferences.map { it.lensIdBlacklist }
    val iszLensConfigs: Flow<List<IszLensConfig>> = userPreferencesRepository.userPreferences.map { it.iszLensConfigs }
    val preferredMainCameraId: Flow<String?> = userPreferencesRepository.userPreferences.map { it.preferredMainCameraId }
    val preferredMacroCameraId: Flow<String?> = userPreferencesRepository.userPreferences.map { it.preferredMacroCameraId }
    val enableLogicalMultiCameraDiscovery: Flow<Boolean> =
        userPreferencesRepository.userPreferences.map { it.enableLogicalMultiCameraDiscovery }
    val logicalCameraBindingWhitelist: Flow<List<String>> =
        userPreferencesRepository.userPreferences.map { it.logicalCameraBindingWhitelist }
    val userPreferences: StateFlow<UserPreferences> = userPreferencesRepository.userPreferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())
    val photoLutId: StateFlow<String?> = userPreferencesRepository.userPreferences
        .map { it.lutId }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val separateVideoLutEnabled: StateFlow<Boolean> = userPreferencesRepository.userPreferences
        .map { it.separateVideoLutEnabled }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val videoLutId: StateFlow<String?> = userPreferencesRepository.userPreferences
        .map { it.videoLutId }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val rawBaselineLutId: StateFlow<String?> = userPreferencesRepository.userPreferences
        .map { it.rawBaselineLutId }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val phantomFrameId: StateFlow<String?> = userPreferencesRepository.userPreferences
        .map { it.phantomFrameId }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val rawDcpId: StateFlow<String?> = userPreferencesRepository.userPreferences
        .map { it.rawDcpId }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val rawDcpIdsByLens: StateFlow<Map<String, String?>> = userPreferencesRepository.userPreferences
        .map { it.rawDcpIdsByLens }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val rawNoiseProfileId: StateFlow<String> = userPreferencesRepository.userPreferences
        .map { it.rawNoiseProfileId }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            RawNoiseProfileManager.DEFAULT_PROFILE_ID,
        )
    val rawNoiseProfileIdsByLens: StateFlow<Map<String, String>> =
        userPreferencesRepository.userPreferences
            .map { it.rawNoiseProfileIdsByLens }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val rawHncsProfileId: StateFlow<String?> = userPreferencesRepository.userPreferences
        .map { it.rawHncsProfileId }
        .stateIn(viewModelScope, SharingStarted.Eagerly, HncsProfileManager.DEFAULT_PROFILE_ID)
    val rawHncsRenderIntent: StateFlow<HncsRenderIntent> =
        userPreferencesRepository.userPreferences
            .map { it.rawHncsRenderIntent }
            .stateIn(viewModelScope, SharingStarted.Eagerly, HncsRenderIntent.Standard)
    val rawHncsFilmCurveMode: StateFlow<HncsFilmCurveMode> =
        userPreferencesRepository.userPreferences
            .map { it.rawHncsFilmCurveMode }
            .stateIn(viewModelScope, SharingStarted.Eagerly, HncsFilmCurveMode.Standard)
    val rawExposureCompensation: StateFlow<Float> = userPreferencesRepository.userPreferences
        .map { it.rawExposureCompensation }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0f)
    val rawHighlightsAdjustment: StateFlow<Float> = userPreferencesRepository.userPreferences
        .map { it.rawHighlightsAdjustment }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0f)
    val rawShadowsAdjustment: StateFlow<Float> = userPreferencesRepository.userPreferences
        .map { it.rawShadowsAdjustment }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0f)
    val rawMinShutterSpeedNs: StateFlow<Long> = userPreferencesRepository.userPreferences
        .map { it.rawMinShutterSpeedNs }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)
    val rawDROEnabled: StateFlow<Boolean> = userPreferencesRepository.userPreferences
        .map { it.rawDROEnabled }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val rawBlackPointCorrection: StateFlow<Float> = userPreferencesRepository.userPreferences
        .map { it.rawBlackPointCorrection }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0f)
    val rawWhitePointCorrection: StateFlow<Float> = userPreferencesRepository.userPreferences
        .map { it.rawWhitePointCorrection }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0f)
    val rawLensShadingCorrectionEnabled: StateFlow<Boolean> = userPreferencesRepository.userPreferences
        .map { it.rawLensShadingCorrectionEnabled }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val rawBlackLevelModes: StateFlow<Map<String, String>> = userPreferencesRepository.userPreferences
        .map { it.rawBlackLevelModes }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val rawCustomBlackLevels: StateFlow<Map<String, Float>> = userPreferencesRepository.userPreferences
        .map { it.rawCustomBlackLevels }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val rawWhiteLevelModes: StateFlow<Map<String, String>> = userPreferencesRepository.userPreferences
        .map { it.rawWhiteLevelModes }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val rawCustomWhiteLevels: StateFlow<Map<String, Float>> = userPreferencesRepository.userPreferences
        .map { it.rawCustomWhiteLevels }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val rawCfaCorrectionModes: StateFlow<Map<String, String>> = userPreferencesRepository.userPreferences
        .map { it.rawCfaCorrectionModes }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val rawBlackLevelMode: StateFlow<String> = combine(
        state.map { it.currentCameraId }.distinctUntilChanged(),
        userPreferencesRepository.userPreferences
    ) { cameraId, prefs ->
        prefs.rawBlackLevelModes[cameraId] ?: "Default"
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "Default")
    val rawCustomBlackLevel: StateFlow<Float> = combine(
        state.map { it.currentCameraId }.distinctUntilChanged(),
        userPreferencesRepository.userPreferences
    ) { cameraId, prefs ->
        prefs.rawCustomBlackLevels[cameraId] ?: 0f
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0f)
    val rawWhiteLevelMode: StateFlow<String> = combine(
        state.map { it.currentCameraId }.distinctUntilChanged(),
        userPreferencesRepository.userPreferences
    ) { cameraId, prefs ->
        prefs.rawWhiteLevelModes[cameraId] ?: RawWhiteLevelCorrection.MODE_DEFAULT
    }.stateIn(viewModelScope, SharingStarted.Eagerly, RawWhiteLevelCorrection.MODE_DEFAULT)
    val rawCustomWhiteLevel: StateFlow<Float> = combine(
        state.map { it.currentCameraId }.distinctUntilChanged(),
        userPreferencesRepository.userPreferences
    ) { cameraId, prefs ->
        prefs.rawCustomWhiteLevels[cameraId] ?: 0f
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0f)
    val rawCfaCorrectionMode: StateFlow<String> = combine(
        state.map { it.currentCameraId }.distinctUntilChanged(),
        userPreferencesRepository.userPreferences
    ) { cameraId, prefs ->
        prefs.rawCfaCorrectionModes[cameraId] ?: RawCfaCorrection.MODE_DEFAULT
    }.stateIn(viewModelScope, SharingStarted.Eagerly, RawCfaCorrection.MODE_DEFAULT)
    val exportDngWithRawExport: StateFlow<Boolean> = userPreferencesRepository.userPreferences
        .map { it.exportDngWithRawExport }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    var availableDcps: List<DcpInfo> by mutableStateOf(emptyList())
        private set
    var availableRawNoiseProfiles: List<RawNoiseProfileInfo> by mutableStateOf(emptyList())
        private set
    val useJpgMax: StateFlow<Boolean> = userPreferencesRepository.userPreferences
        .map { it.useJpgMax }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val useMultipleExposure: StateFlow<Boolean> = userPreferencesRepository.userPreferences
        .map { it.useMultipleExposure }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val multipleExposureCount: StateFlow<Int> = userPreferencesRepository.userPreferences
        .map { it.multipleExposureCount }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 2)
    val jpgMultiFrameDenoiseFrameCount: StateFlow<Int> =
        userPreferencesRepository.userPreferences
            .map { it.jpgMultiFrameDenoiseFrameCount }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                MultiFrameConfig.DEFAULT_DENOISE_FRAME_COUNT,
            )
    val jpgMultiFrameDenoiseOutputScale: StateFlow<Float> = userPreferencesRepository.userPreferences
        .map { MultiFrameConfig.normalizeOutputScale(it.jpgMultiFrameDenoiseOutputScale) }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            MultiFrameConfig.DEFAULT_SUPER_RESOLUTION_SCALE,
        )
    val hdrPlusFrameCount: StateFlow<Int> = userPreferencesRepository.userPreferences
        .map { it.hdrPlusFrameCount }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            MultiFrameConfig.DEFAULT_HDR_PLUS_FRAME_COUNT,
        )
    val hdrPlusMergeMode: StateFlow<MgcRawMaxMode> = userPreferencesRepository.userPreferences
        .map { it.hdrPlusMergeMode }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MgcRawMaxMode.DEFAULT)
    val hdrPlusBracketExposureEnabled: StateFlow<Boolean> =
        userPreferencesRepository.userPreferences
            .map { it.hdrPlusBracketExposureEnabled }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                MultiFrameConfig.DEFAULT_HDR_PLUS_BRACKET_EXPOSURE,
            )
    val hdrPlusLongFrameExposureEv: StateFlow<Float> = userPreferencesRepository.userPreferences
        .map { it.hdrPlusLongFrameExposureEv }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MultiFrameConfig.LONG_FRAME_EXPOSURE_EV.toFloat())
    val hdrPlusShortFrameExposureEv: StateFlow<Float> = userPreferencesRepository.userPreferences
        .map { it.hdrPlusShortFrameExposureEv }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MultiFrameConfig.DEFAULT_SHORT_FRAME_EXPOSURE_EV)
    val mlAeMaxHdrRatio: StateFlow<Float> = userPreferencesRepository.userPreferences
        .map { it.mlAeMaxHdrRatio }
        .stateIn(viewModelScope, SharingStarted.Eagerly, RawSceneExposureMath.FAST_MOMENTS_MAX_HDR_RATIO)
    val useRawMax: StateFlow<Boolean> = userPreferencesRepository.userPreferences
        .map { it.useRawMax }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val rawDigitalZoomResamplingEnabled: StateFlow<Boolean> = userPreferencesRepository.userPreferences
        .map { it.rawDigitalZoomResamplingEnabled }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val rawMaxOutputScale: StateFlow<Float> = userPreferencesRepository.userPreferences
        .map {
            MultiFrameConfig.normalizeOutputScale(
                outputScale = it.rawMaxOutputScale,
                fallback = MultiFrameConfig.DEFAULT_SUPER_RESOLUTION_SCALE,
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            MultiFrameConfig.DEFAULT_SUPER_RESOLUTION_SCALE,
        )
    val rawOutputUpscaleMode: StateFlow<RawOutputUpscaleMode> =
        userPreferencesRepository.userPreferences
            .map { it.rawOutputUpscaleMode }
            .stateIn(viewModelScope, SharingStarted.Eagerly, RawOutputUpscaleMode.DEFAULT)
    val useLivePhoto: StateFlow<Boolean> = userPreferencesRepository.userPreferences
        .map { it.useLivePhoto }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val enableDevelopAnimation: StateFlow<Boolean> = userPreferencesRepository.userPreferences
        .map { it.enableDevelopAnimation }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val developAnimationStyle: StateFlow<DevelopAnimationStyle> = userPreferencesRepository.userPreferences
        .map { it.developAnimationStyle }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DevelopAnimationStyle.FILM)
    val backgroundImage: StateFlow<String> = userPreferencesRepository.userPreferences
        .map { it.backgroundImage }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "camera_bg")
    val captureButtonStyle: StateFlow<CaptureButtonStyle> = userPreferencesRepository.userPreferences
        .map { it.captureButtonStyle }
        .stateIn(viewModelScope, SharingStarted.Eagerly, CaptureButtonStyle.DEFAULT)
    val captureButtonColor: StateFlow<Int> = userPreferencesRepository.userPreferences
        .map { it.captureButtonColor }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0xFFFFFFFF.toInt())
    val captureButtonImagePath: StateFlow<String?> = userPreferencesRepository.userPreferences
        .map { it.captureButtonImagePath }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val droMode: StateFlow<String> = userPreferencesRepository.userPreferences
        .map { it.droMode }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "OFF")
    val applyUltraHDR: StateFlow<Boolean> = userPreferencesRepository.userPreferences
        .map { it.applyUltraHDR }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val colorSpace: StateFlow<ColorSpace> = userPreferencesRepository.userPreferences
        .map { it.colorSpace }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ColorSpace.SRGB)
    val logCurve: StateFlow<TransferCurve> = userPreferencesRepository.userPreferences
        .map { it.logCurve }
        .stateIn(viewModelScope, SharingStarted.Eagerly, TransferCurve.SRGB)

    val rawLut: StateFlow<String> = userPreferencesRepository.userPreferences
        .map { prefs ->
            prefs.rawLuts[prefs.logCurve.name] ?: RawProfile.defaultLutFor(prefs.colorSpace, prefs.logCurve)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, RawProfile.default.rawLut)
    val rawProfile: StateFlow<RawProfile> = userPreferencesRepository.userPreferences
        .map { prefs ->
            RawProfile.fromComponents(
                colorSpace = prefs.colorSpace,
                logCurve = prefs.logCurve,
                rawLut = prefs.rawLuts[prefs.logCurve.name]
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, RawProfile.default)

    val useP010: StateFlow<Boolean> = userPreferencesRepository.userPreferences
        .map { it.useP010 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val useP3ColorSpace: StateFlow<Boolean> = userPreferencesRepository.userPreferences
        .map { it.useP3ColorSpace }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val ultraHdrGainMapEnabled: StateFlow<Boolean> = userPreferencesRepository.userPreferences
        .map { it.ultraHdrGainMapEnabled }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val useHdrScreenMode: StateFlow<Boolean> = userPreferencesRepository.userPreferences
        .map { it.useHdrScreenMode }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val phantomMode: StateFlow<Boolean> = userPreferencesRepository.userPreferences
        .map { it.phantomMode }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val videoCodec: StateFlow<com.hinnka.mycamera.video.VideoCodec> = userPreferencesRepository.userPreferences
        .map { it.videoCodec }
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.hinnka.mycamera.video.VideoCodec.H264)
    val videoRecordingPath: StateFlow<VideoRecordingPath> = userPreferencesRepository.userPreferences
        .map { it.videoRecordingPath }
        .stateIn(viewModelScope, SharingStarted.Eagerly, VideoRecordingPath.DCIM_PHOTON)
    val videoRecordingTreeUri: StateFlow<String?> = userPreferencesRepository.userPreferences
        .map { it.videoRecordingTreeUri }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val videoLensLockEnabled: StateFlow<Boolean> = userPreferencesRepository.userPreferences
        .map { it.videoLensLockEnabled }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val videoWhiteBalanceLockEnabled: StateFlow<Boolean> = userPreferencesRepository.userPreferences
        .map { it.videoWhiteBalanceLockEnabled }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val photoPreviewStabilizationEnabled: StateFlow<Boolean> =
        userPreferencesRepository.userPreferences
            .map { it.photoPreviewStabilizationEnabled }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val videoAudioInputOptions: StateFlow<List<VideoAudioInputOption>> = videoAudioInputManager.availableInputs

    val phantomButtonHidden: StateFlow<Boolean> = userPreferencesRepository.userPreferences
        .map { it.phantomButtonHidden }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val launchCameraOnPhantomMode: StateFlow<Boolean> = userPreferencesRepository.userPreferences
        .map { it.launchCameraOnPhantomMode }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val phantomPipPreview: StateFlow<Boolean> = userPreferencesRepository.userPreferences
        .map { it.phantomPipPreview }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val phantomPipCrop: StateFlow<PhantomPipCrop> = userPreferencesRepository.userPreferences
        .map { it.phantomPipCrop }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PhantomPipCrop())
    val phantomSaveAsNew: StateFlow<Boolean> = userPreferencesRepository.userPreferences
        .map { it.phantomSaveAsNew }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val defaultVirtualAperture: Flow<Float> =
        userPreferencesRepository.userPreferences.map { it.defaultVirtualAperture }

    val mirrorFrontCamera: Flow<Boolean> = userPreferencesRepository.userPreferences.map { it.mirrorFrontCamera }
    val widgetTheme = userPreferencesRepository.userPreferences.map { it.widgetTheme }
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.hinnka.mycamera.data.WidgetTheme.FOLLOW_SYSTEM)
    val saveLocationEnabled = userPreferencesRepository.userPreferences.map { it.saveLocation }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val openAIApiKey = userPreferencesRepository.userPreferences.map { it.openAIApiKey }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val openAIUrl = userPreferencesRepository.userPreferences.map { it.openAIBaseUrl }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val openAIModel = userPreferencesRepository.userPreferences.map { it.openAIModel }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val useBuiltInAiService = userPreferencesRepository.userPreferences.map { it.useBuiltInAiService }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // 动态获取可用的 AI 模型列表 (UI 层按需触发刷新)
    private val _availableOpenAIModels = MutableStateFlow<List<String>>(emptyList())
    val availableOpenAIModels = _availableOpenAIModels.asStateFlow()

    private val _isFetchingAIModels = MutableStateFlow(false)
    val isFetchingAIModels = _isFetchingAIModels.asStateFlow()

    private var isShutterSoundEnabled = true
    private var isVibrationEnabled = true

    var glSurfaceView by mutableStateOf<CameraGLSurfaceView?>(null)
    var isEyeFocusBusy by mutableStateOf(false)
        private set
    var isEyeFocusRuntimeAvailable by mutableStateOf(true)
        private set
    private var lastEyeFocusTriggerPoint: Pair<Float, Float>? = null
    private var lastEyeFocusTriggerElapsedMs = 0L
    @Volatile
    private var latestPortraitMask: PortraitMaskSnapshot? = null

    // 保存当前的 SurfaceTexture 以便切换摄像头时重用
    private var currentSurfaceTexture: SurfaceTexture? = null
    private var cameraOpenInFlight = false
    private var cameraReopenJob: Job? = null
    private val _focusBracketState = MutableStateFlow(FocusBracketState())
    val focusBracketState = _focusBracketState.asStateFlow()
    private var focusBracketJob: Job? = null
    @Volatile private var focusBracketGeneration = 0L

    private val _intervalCaptureState = MutableStateFlow(IntervalCaptureState())
    val intervalCaptureState = _intervalCaptureState.asStateFlow()
    private var intervalCaptureJob: Job? = null
    @Volatile private var intervalCaptureGeneration = 0L

    private val _advancedCameraState = MutableStateFlow(AdvancedCameraState())
    val advancedCameraState = _advancedCameraState.asStateFlow()
    private var advancedCameraJob: Job? = null
    @Volatile private var activeAdvancedSequenceId: String? = null
    private var advancedTemporalCommittedFrames = 0
    private val advancedFinishRequested = MutableStateFlow(false)
    private var advancedSaveJob: Job? = null
    private var temporalPreviewBuffer: TemporalPreviewBuffer? = null
    private val advancedCaptureResults = java.util.concurrent.ConcurrentHashMap<Long, CompletableDeferred<String?>>()
    private val advancedExposureResults = java.util.concurrent.ConcurrentHashMap<Long, CompletableDeferred<Boolean>>()

    private var cameraErrorRecoveryJob: Job? = null

    // 用于处理音量键连续按下的时间戳，防止抖动和过快响应
    private var lastVolumeKeyEventTime = 0L
    private val VOLUME_KEY_DEBOUNCE_TIME = 200L // 毫秒

    private var hasAppliedDefaultFocalLength = false

    val captureProcessingState = CaptureProcessingQueue.state
    private val _capturedThumbnail = MutableStateFlow<CapturedThumbnail?>(null)
    val capturedThumbnail = _capturedThumbnail.asStateFlow()
    private val finalizingMultipleExposureSessions = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val focusBracketCaptureResults = java.util.concurrent.ConcurrentHashMap<Long, CompletableDeferred<String?>>()
    private val pendingCaptureSettings = java.util.concurrent.ConcurrentHashMap<Long, CaptureSettingsSnapshot>()
    // Camera-thread delivery must remain visible until processing queue admission is complete.
    private val captureProcessingHandoffs = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()
    private var multipleExposureMetadata: MediaMetadata? = null
    var multipleExposureState by mutableStateOf(MultipleExposureSessionState())
        private set

    private var burstSettings: CaptureSettingsSnapshot? = null
    private var livePhotoIndicatorJob: Job? = null
    private var burstPhotoId: String? = null
    var burstImageCount by mutableStateOf(0)
        private set

    var showGhostPermissions by mutableStateOf(false)

    var showEnhancedStabilizationUnavailableDialog by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            saveLocationEnabled.collect { applyLocationTracking(it) }
        }
        cameraController.onEnhancedStabilizationUnavailable = { fallback ->
            viewModelScope.launch(Dispatchers.Main.immediate) {
                showEnhancedStabilizationUnavailableDialog = true
                when (fallback) {
                    is EnhancedStabilizationFallback.Video -> {
                        val resolvedConfig = cameraController.state.value.videoConfig
                        userPreferencesRepository.saveVideoStabilizationConfig(
                            mode = fallback.mode,
                            resolution = resolvedConfig.resolution,
                            fps = resolvedConfig.fps,
                        )
                    }

                    EnhancedStabilizationFallback.PhotoPreview -> {
                        userPreferencesRepository.savePhotoPreviewStabilizationEnabled(false)
                    }
                }
            }
        }
        previewEyeFocusProcessor.onBusyStateChanged = { busy ->
            viewModelScope.launch(Dispatchers.Main.immediate) {
                isEyeFocusBusy = busy
                glSurfaceView?.setEyeFocusBusy(busy)
            }
        }
        previewEyeFocusProcessor.onFrameProcessed = { timing ->
            viewModelScope.launch(Dispatchers.Main.immediate) {
                glSurfaceView?.updateEyeFocusTiming(timing)
            }
        }
        previewEyeFocusProcessor.onEyeTarget = { target ->
            viewModelScope.launch(Dispatchers.Main.immediate) {
                handleEyeFocusTarget(target)
            }
        }
        previewEyeFocusProcessor.onPortraitMask = { mask ->
            latestPortraitMask = mask.copy(
                confidence = mask.confidence.copyOf(),
                cameraId = cameraController.getCurrentCameraId(),
                sensorOrientationDegrees = cameraController.getSensorOrientation(),
                isFrontFacing = cameraController.getLensFacing() ==
                    CameraCharacteristics.LENS_FACING_FRONT,
                previewToCaptureRotationDegrees = Math.floorMod(
                    capturePreviewThumbnailRotation().roundToInt(),
                    360,
                ),
            )
        }
        previewEyeFocusProcessor.onTargetLost = {
            viewModelScope.launch(Dispatchers.Main.immediate) {
                _subjectFocusLabel.value = null
                resetEyeFocusTracking(cancelCameraFocus = true, reason = "target_lost")
            }
        }
        previewEyeFocusProcessor.onInitializationError = { error ->
            viewModelScope.launch(Dispatchers.Main.immediate) {
                isEyeFocusRuntimeAvailable = false
                resetEyeFocusTracking(cancelCameraFocus = true, reason = "initialization_failed")
                userPreferencesRepository.saveEyeFocusEnabled(false)
                PLog.e(TAG, "Human eye autofocus is unavailable", error)
            }
        }
        previewEyeFocusProcessor.onSubjectError = {
            viewModelScope.launch(Dispatchers.Main.immediate) {
                _sceneModelUnavailable.value = true
                clearSceneTracking()
            }
        }
        previewEyeFocusProcessor.onSubjectAvailable = { _sceneModelUnavailable.value = false }
        previewEyeFocusProcessor.onError = { error ->
            PLog.e(TAG, "Camera vision inference failed", error)
        }
        viewModelScope.launch {
            combine(eyeFocusEnabled, subjectFocusEnabled) { eye, subject -> eye || subject }.collect { enabled ->
                if (enabled && isEyeFocusRuntimeAvailable) {
                    previewEyeFocusProcessor.prewarm()
                } else if (!enabled) {
                    previewEyeFocusProcessor.resetTracking()
                    resetEyeFocusTracking(cancelCameraFocus = true, reason = "disabled")
                }
            }
        }
        viewModelScope.launch {
            delay(500)
            while (isActive) {
                try { motionPriorityMutex.withLock { updateMotionPriority() } }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { _motionPriorityEnabled.value=false;PLog.e(TAG,"Motion priority unavailable",error) }
                delay(250)
            }
        }
        cameraController.initialize()
        viewModelScope.launch {
            cameraController.state.collect { cameraState ->
                if (cameraState.isPreviewActive) {
                    cameraOpenInFlight = false
                }
                if (
                    cameraState.focusPointSource == FocusPointSource.EYE &&
                    (
                        !cameraState.isPreviewActive ||
                            !cameraState.isAutoFocus ||
                            cameraState.isHyperfocalFocusEnabled
                    )
                ) {
                    previewEyeFocusProcessor.resetTracking()
                    resetEyeFocusTracking(cancelCameraFocus = true, reason = "camera_state_changed")
                }
            }
        }
        cameraController.onImageCaptured = { image, captureInfo, _, _, _ ->
            val photoId = burstPhotoId
            val settings = burstSettings
            if (photoId == null || settings == null) {
                image.close()
            } else {
                burstImageCount++
                CaptureProcessingQueue.enqueue(listOf(image)) {
                    val context = getApplication<Application>()
                    if (GalleryManager.loadMetadata(context, photoId) == null) {
                        prepareBurst(context, photoId, image, captureInfo, settings)
                    }
                    withContext(Dispatchers.IO) {
                        GalleryManager.saveBurstPhoto(
                            context, photoId, image, settings.preferences.autoSaveAfterCapture,
                            contentRepository.photoProcessor, settings.preferences.photoQuality,
                        )
                    }
                    _imageSavedEvent.emit(photoId)
                }
            }
        }
        cameraController.onPhotoCaptureCompleted = { id, captureInfo ->
            PLog.i(TAG, "Photo pipeline exposure complete: capture=$id pending=${pendingCaptureSettings.containsKey(id)}")
            advancedExposureResults.remove(id)?.complete(true)
            pendingCaptureSettings[id]?.let { settings ->
                val sequence = settings.sequenceProperties["advancedSequenceId"]
                val sourceIndex = settings.sequenceProperties["advancedSequenceIndex"]?.toIntOrNull()
                if (sequence != null && sequence == activeAdvancedSequenceId && sourceIndex != null) {
                    _advancedCameraState.update { it.copy(frames = maxOf(it.frames, sourceIndex + 1),
                        status = getApplication<Application>().getString(R.string.advanced_processing_source, sourceIndex + 1)) }
                }
                settings.galleryRegistrationStarted = true
                viewModelScope.launch {
                    try {
                        val metadata = buildPhotoMetadata(
                            width = captureInfo.imageWidth,
                            height = captureInfo.imageHeight,
                            captureInfo = captureInfo,
                            settings = settings,
                        )
                        if (!settings.multipleExposure && !settings.processingFinished && !settings.sequenceProperties.containsKey("addMeSessionId") && settings.sequenceProperties["advancedPrivateSource"] != "true") {
                            GalleryManager.registerProcessingPhoto(
                                getApplication(), settings.photoId, metadata, settings.displayThumbnail
                            )
                        }
                        settings.captureCompleted = true
                        publishCapturedThumbnail(settings)
                        settings.galleryRegistration.complete(Unit)
                        PLog.i(TAG, "Photo pipeline registered: capture=$id photo=${settings.photoId}")
                        if (settings.sequenceProperties.isEmpty()) _captureCompletedEvent.emit(metadata)
                    } catch (e: Exception) {
                        settings.galleryRegistration.completeExceptionally(e)
                        PLog.e(TAG, "Failed to register captured photo ${settings.photoId}", e)
                    }
                }.invokeOnCompletion { error ->
                    // A queued capture must also finish if the camera owner is cleared before registration starts.
                    if (error != null) settings.galleryRegistration.completeExceptionally(error)
                }
            }
        }
        cameraController.onPhotoCaptureReady = { photo ->
            captureProcessingHandoffs.add(photo.id)
            try {
                val settings = pendingCaptureSettings.remove(photo.id)
                PLog.i(TAG, "Photo pipeline images ready: capture=${photo.id} frames=${photo.frames.size} settings=${settings != null}")
                if (settings == null) {
                    focusBracketCaptureResults.remove(photo.id)?.complete(null)
                    advancedCaptureResults.remove(photo.id)?.complete(null)
                    advancedExposureResults.remove(photo.id)?.complete(false)
                    photo.frames.forEach { it.image.close() }
                } else {
                    val shotSettings = settings
                    shotSettings.state = photo.state
                    if (photo.state.useLivePhoto) {
                        val first = photo.frames.first()
                        shotSettings.livePhotoVideo = CompletableDeferred<Pair<File, Long>?>().also { deferred ->
                            cameraController.recordLivePhotoVideo(
                                first.image.timestamp / 1000,
                                first.captureInfo.livePhotoVideoStartTimestampUs,
                            ) { file, timestamp ->
                                deferred.complete(if (file.name == "error") null else file to timestamp)
                            }
                        }
                    }
                    CaptureProcessingQueue.enqueue(photo.frames.map { it.image }) {
                        try {
                            PLog.i(TAG, "Photo pipeline processing: capture=${photo.id} registration=${shotSettings.galleryRegistration.isCompleted}")
                            shotSettings.galleryRegistration.await()
                            shotSettings.sourceRawReference?.let { reference ->
                                shotSettings.originalThumbnail = withTimeoutOrNull(5000L) { reference.await() }
                                    ?: error(getApplication<Application>().getString(R.string.advanced_raw_reference_unavailable))
                                PLog.i(TAG, "RAW source reference ready for processing: capture=${photo.id}")
                            }
                            processCapturedPhoto(photo, shotSettings)
                        } catch (error: Exception) {
                            // Keep the failing boundary for sequence callers; do not replace an
                            // engine/storage exception with a generic missing-photo result.
                            advancedCaptureResults.remove(photo.id)?.completeExceptionally(error)
                            throw error
                        } finally {
                            try {
                                GalleryManager.finishProcessingPhoto(
                                    getApplication(), shotSettings.photoId, shotSettings.savedPhotoId != null
                                )
                                // A failed photo preparation must not orphan its already-recorded video.
                                withContext(Dispatchers.IO) {
                                    shotSettings.livePhotoVideo?.await()?.first?.delete()
                                }
                            } finally {
                                shotSettings.sourceRawReference?.close()
                                if (shotSettings.sourceRawReference != null) shotSettings.originalThumbnail = null
                                shotSettings.processingFinished = true
                                val hasSavedImage = shotSettings.savedPhotoId?.let { id ->
                                    GalleryManager.getOriginalImageFile(getApplication(), id)?.length()?.let { it > 0L } == true
                                } == true
                                PLog.i(TAG, "Photo pipeline terminal: capture=${photo.id} photo=${shotSettings.photoId} saved=$hasSavedImage")
                                focusBracketCaptureResults.remove(photo.id)?.complete(shotSettings.savedPhotoId.takeIf { hasSavedImage })
                                advancedCaptureResults.remove(photo.id)?.complete(if (hasSavedImage) shotSettings.savedPhotoId else null)
                                publishCapturedThumbnail(shotSettings)
                                val privateSession = shotSettings.sequenceProperties["advancedSequenceId"]
                                if (shotSettings.sequenceProperties["advancedPrivateSource"] == "true" && privateSession != null && closedPrivateAdvancedSessions.contains(privateSession)) {
                                    withContext(NonCancellable + Dispatchers.IO) { deletePrivateAdvancedSource(privateSession, shotSettings.photoId) }
                                }
                                val session = shotSettings.sequenceProperties["addMeSessionId"]
                                if (session != null && closedGuidedGroupSessions.contains(session)) {
                                    withContext(NonCancellable + Dispatchers.IO) { deleteGuidedGroupSource(session, shotSettings.photoId) }
                                }
                            }
                        }
                    }
                }
            } finally {
                captureProcessingHandoffs.remove(photo.id)
            }
        }
        cameraController.onPhotoCaptureFailed = { id ->
            advancedExposureResults.remove(id)?.complete(false)
            focusBracketCaptureResults.remove(id)?.complete(null)
            advancedCaptureResults.remove(id)?.completeExceptionally(
                IllegalStateException(getApplication<Application>().getString(R.string.advanced_exposure_failed))
            )
            pendingCaptureSettings.remove(id)?.let { settings ->
                settings.sourceRawReference?.close()
                settings.processingFinished = true
                publishCapturedThumbnail(settings)
                viewModelScope.launch {
                    // If exposure completion already started registration, let it finish
                    // before clearing its processing marker. Otherwise registration can
                    // recreate the marker after failure cleanup and leave the gallery stuck.
                    if (settings.galleryRegistrationStarted) {
                        try {
                            settings.galleryRegistration.await()
                        } catch (e: Exception) {
                            PLog.w(TAG, "Failed capture registration did not complete: capture=$id", e)
                        }
                    }
                    GalleryManager.finishProcessingPhoto(getApplication(), settings.photoId, false)
                }
            }
        }
        cameraController.onVideoSaved = { uri ->
            if (uri == null) com.hinnka.mycamera.video.VideoProductionManager.cancelRecording()
            if (uri != null) {
                com.hinnka.mycamera.video.VideoProductionManager.onVideoSaved(getApplication(), uri, state.value.videoConfig.fps.fps)
                viewModelScope.launch {
                    val mediaId = GalleryManager.recordVideoCapture(getApplication(), uri)
                    if (mediaId != null) {
                        _imageSavedEvent.emit(mediaId)
                    }
                }
            }
        }

        cameraController.onCameraError = { code, message, canRetry ->
            // 只记录错误日志，不在这里重试打开相机
            // 相机恢复应该由 CameraScreen 的 ON_RESUME 生命周期事件处理
            // 这样可以避免在相机被其他应用占用时的无限重试循环
            PLog.d(TAG, "onCameraError: code=$code, message=$message, canRetry=$canRetry")
            cameraOpenInFlight = false
            scheduleCameraListRefreshAfterError(code)
            resetExposureCompensationForCameraRestart()
            burstImageCount = 0
        }


        // 监听快门声音、震动和软件处理设置
        viewModelScope.launch {
            if (userPreferencesRepository.restoreCameraStartupDefaultsOnce()) {
                PLog.d(TAG, "Restored camera startup defaults once")
            }
            var firstPreferencesLogged = false
            val preferenceCollectStart = SystemClock.elapsedRealtime()
            userPreferencesRepository.userPreferences.collect {
                if (!firstPreferencesLogged) {
                    firstPreferencesLogged = true

                    StartupTrace.mark(
                        "CameraViewModel.userPreferences first collect",
                        "costMs=${SystemClock.elapsedRealtime() - preferenceCollectStart}"
                    )
                }
                cameraController.setPhotoCaptureSize(it.photoCaptureSize)
                cameraController.setPhotoFinishUpscaleEnabled(it.photoFinishUpscale)
                isShutterSoundEnabled = it.shutterSoundEnabled
                if (!isShutterSoundEnabled) shutterSoundPlayer.stopBurst()
                shutterSoundPlayer.configure(
                    it.shutterSoundFileName,
                    it.burstSoundFileName,
                )
                isVibrationEnabled = it.vibrationEnabled
                // 同步降噪等级到相机控制器
                val currentCameraState = cameraController.state.value
                if (currentCameraState.nrLevel != it.nrLevel) {
                    cameraController.setNRLevel(it.nrLevel)
                }
                // 同步锐化等级到相机控制器
                cameraController.setEdgeLevel(it.edgeLevel)
                cameraController.setVideoImageQualitySettings(it.videoNrLevel, it.videoEdgeLevel)
                if (currentCameraState.vendorCaptureSettingsByLens != it.vendorCaptureSettingsByLens) {
                    cameraController.setVendorCaptureSettingsByLens(it.vendorCaptureSettingsByLens)
                }
                if (currentCameraState.customVendorKeySettings != it.customVendorKeySettings) {
                    cameraController.setCustomVendorKeySettings(it.customVendorKeySettings)
                }
                // 同步 RAW 设置到相机控制器
                if (currentCameraState.rawCfaCorrectionModes != it.rawCfaCorrectionModes) {
                    cameraController.setRawCfaCorrectionModes(it.rawCfaCorrectionModes)
                }
                val multipleExposureEnabled = it.useMultipleExposure
                val effectiveUseRaw = it.useRaw && !multipleExposureEnabled
                val effectiveUseJpgMax =
                    it.useJpgMax && !effectiveUseRaw && !multipleExposureEnabled
                val effectiveUseRawMax =
                    it.useRawMax && effectiveUseRaw && !multipleExposureEnabled
                val effectiveMultiFrameOutputScale = resolveMultiFrameOutputScale(
                    useJpgMax = effectiveUseJpgMax,
                    useRawMax = effectiveUseRawMax,
                    rawMaxOutputScale = it.rawMaxOutputScale,
                    jpgMultiFrameDenoiseOutputScale = it.jpgMultiFrameDenoiseOutputScale,
                )
                val effectiveRawRenderingEngine = resolveCaptureRawRenderingEngine(it)
                if (effectiveRawRenderingEngine != it.rawRenderingEngine) {
                    viewModelScope.launch {
                        userPreferencesRepository.saveRawColorEngine(effectiveRawRenderingEngine)
                    }
                }
                if (currentCameraState.useMultipleExposure != multipleExposureEnabled) {
                    cameraController.setUseMultipleExposure(multipleExposureEnabled)
                }
                if (currentCameraState.useRaw != effectiveUseRaw) {
                    cameraController.setUseRaw(effectiveUseRaw)
                }
                if (currentCameraState.multiFrameOutputScale != effectiveMultiFrameOutputScale) {
                    cameraController.setMultiFrameOutputScale(effectiveMultiFrameOutputScale)
                }
                if (
                    currentCameraState.jpgMultiFrameDenoiseFrameCount !=
                    it.jpgMultiFrameDenoiseFrameCount
                ) {
                    cameraController.setJpgMultiFrameDenoiseFrameCount(
                        it.jpgMultiFrameDenoiseFrameCount
                    )
                }
                if (currentCameraState.hdrPlusMergeMode != it.hdrPlusMergeMode) {
                    cameraController.setHdrPlusMergeMode(it.hdrPlusMergeMode)
                }
                if (currentCameraState.hdrPlusFrameCount != it.hdrPlusFrameCount) {
                    cameraController.setHdrPlusFrameCount(it.hdrPlusFrameCount)
                }
                if (currentCameraState.useJpgMaxHdrComposition != it.useJpgMaxHdrComposition) {
                    cameraController.setUseJpgMaxHdrComposition(it.useJpgMaxHdrComposition)
                }
                if (
                    currentCameraState.hdrPlusBracketExposureEnabled !=
                    it.hdrPlusBracketExposureEnabled
                ) {
                    cameraController.setHdrPlusBracketExposureEnabled(
                        it.hdrPlusBracketExposureEnabled
                    )
                }
                if (currentCameraState.hdrPlusLongFrameExposureEv != it.hdrPlusLongFrameExposureEv) {
                    cameraController.setHdrPlusLongFrameExposureEv(it.hdrPlusLongFrameExposureEv)
                }
                if (currentCameraState.hdrPlusShortFrameExposureEv != it.hdrPlusShortFrameExposureEv) {
                    cameraController.setHdrPlusShortFrameExposureEv(it.hdrPlusShortFrameExposureEv)
                }
                if (multipleExposureEnabled && (it.useRaw || it.useJpgMax || it.useRawMax)) {
                    viewModelScope.launch {
                        userPreferencesRepository.saveCameraFeaturePreferences(
                            CameraFeaturePreferencesUpdate(
                                useRaw = PreferenceUpdateValue(false),
                                useJpgMax = PreferenceUpdateValue(false),
                                useRawMax = PreferenceUpdateValue(false)
                            )
                        )
                    }
                }
                if (it.useLivePhoto && effectiveUseJpgMax) {
                    viewModelScope.launch {
                        userPreferencesRepository.saveUseLivePhoto(false)
                    }
                }
                if (currentCameraState.rawMinShutterSpeedNs != it.rawMinShutterSpeedNs) {
                    cameraController.setRawMinShutterSpeedNs(it.rawMinShutterSpeedNs)
                }
                if (cameraController.state.value.meteringMode != it.meteringMode) {
                    cameraController.setMeteringMode(it.meteringMode)
                }
                cameraController.setCaptureMode(it.captureMode)
                cameraController.setVideoResolution(it.videoResolution)
                cameraController.setVideoFps(it.videoFps)
                cameraController.setVideoAspectRatio(it.videoAspectRatio)
                cameraController.setVideoLogProfile(it.videoLogProfile)
                cameraController.setVideoLogLutMode(it.videoLogLutMode)
                cameraController.setVideoBitrate(it.videoBitrate)
                cameraController.setVideoAudioInputId(it.videoAudioInputId)
                cameraController.setVideoNoiseSuppression(it.videoNoiseSuppressionEnabled)
                cameraController.setVideoWindReduction(it.videoWindReduction)
                cameraController.setVideoRecordingPath(it.videoRecordingPath, it.videoRecordingTreeUri)
                cameraController.setVideoStabilizationMode(it.videoStabilizationMode)
                cameraController.setOppoSuperStabilizationEnabled(
                    it.oppoSuperStabilizationEnabled
                )
                cameraController.setVideoEnhancedStabilizationStrength(
                    it.videoEnhancedStabilizationStrength
                )
                cameraController.setVideoEnhancedStabilizationLookahead(
                    it.videoEnhancedStabilizationLookahead
                )
                cameraController.setExternalLensStabilizationConfig(
                    it.externalLensStabilizationConfig
                )
                cameraController.setPhotoPreviewStabilizationEnabled(
                    it.photoPreviewStabilizationEnabled
                )
                cameraController.setVideoTorchEnabled(it.videoTorchEnabled)
                cameraController.setVideoLensLockEnabled(it.videoLensLockEnabled)
                cameraController.setVideoWhiteBalanceLockEnabled(it.videoWhiteBalanceLockEnabled)
                cameraController.setVideoCodec(it.videoCodec)
                cameraController.setMirrorFrontCameraEnabled(it.mirrorFrontCamera)
                multipleExposureState = multipleExposureState.copy(
                    enabled = it.useMultipleExposure,
                    targetCount = it.multipleExposureCount,
                    blendMode = if (multipleExposureState.isSessionActive || state.value.isCapturing) {
                        multipleExposureState.blendMode
                    } else it.multipleExposureBlendMode,
                )
                // 同步 Live Photo 设置到相机控制器
                cameraController.setUseLivePhoto(shouldEnableLivePhoto(it))
                // 同步 Ultra HDR 设置到相机控制器
                cameraController.setApplyUltraHDR(it.applyUltraHDR)
                // 同步 P010 设置到相机控制器
                cameraController.setUseP010(it.useP010)
                // 同步 P3 色域设置到相机控制器
                cameraController.setUseP3ColorSpace(it.useP3ColorSpace)
            }
        }

        cameraController.onLivePhotoVideoCaptured = { file, timestamp ->
            // Global listener still available for other UI needs if any
        }

        // 设置快门音效和震动回调
        cameraController.onPlayShutterSound = {
            if (isShutterSoundEnabled) {
                shutterSoundPlayer.play()
            }
            if (isVibrationEnabled) {
                vibrationHelper.vibrate()
            }
        }

        // 订阅 ContentRepository 的 StateFlow，结合用户自定义排序
        viewModelScope.launch {
            contentRepository.availableLuts.combine(
                userPreferencesRepository.userPreferences.map { it.filterOrder }
            ) { luts, order ->
                if (order.isEmpty()) {
                    luts
                } else {
                    val orderMap = order.withIndex().associate { it.value to it.index }
                    luts.sortedBy { orderMap[it.id] ?: Int.MAX_VALUE }
                }
            }.collect { sortedLuts ->
                availableLutList = sortedLuts
            }
        }

        viewModelScope.launch {
            contentRepository.availableDcps.collect { dcps ->
                availableDcps = dcps.sortedBy { it.getName() }
            }
        }

        viewModelScope.launch {
            contentRepository.availableRawNoiseProfiles.collect { profiles ->
                availableRawNoiseProfiles = profiles
            }
        }

        viewModelScope.launch {
            contentRepository.availableFrames.combine(
                userPreferencesRepository.userPreferences.map { it.frameOrder }
            ) { frames, order ->
                if (order.isEmpty()) {
                    frames
                } else {
                    val orderMap = order.withIndex().associate { it.value to it.index }
                    frames.sortedBy { orderMap[it.id] ?: Int.MAX_VALUE }
                }
            }.collect { sortedFrames ->
                availableFrameList = sortedFrames
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.userPreferences.collectLatest { prefs ->
                currentBaselineLutConfig = withContext(Dispatchers.IO) {
                    resolvePreviewBaselineLut(prefs)
                }
            }
        }

        viewModelScope.launch {
            combine(userPreferencesRepository.userPreferences, contentRepository.availableDcps,
                combine(state.map {
                    (it.captureMode == CaptureMode.PHOTO && it.useRaw && it.isRawSupported) ||
                        (it.captureMode == CaptureMode.VIDEO && it.videoConfig.logProfile == VideoLogProfile.OFF)
                }.distinctUntilChanged(), videoEngineEnabled) { eligible, enabled -> eligible to enabled }) { prefs, dcps, mode ->
                val active = mode.first && (state.value.captureMode == CaptureMode.VIDEO && mode.second ||
                    state.value.captureMode == CaptureMode.PHOTO && resolvePreviewBaselineTarget(prefs) != null)
                if (!active) null else {
                    val film = resolveRawSpectralFilmSettings(prefs)
                    RawEnginePreviewSettings(
                        engine = prefs.rawRenderingEngine,
                        dcp = dcps.firstOrNull { it.id == prefs.rawDcpId }
                            ?.takeIf { prefs.rawRenderingEngine == RawRenderingEngine.AdobeCurve },
                        exposureEv = prefs.rawExposureCompensation,
                        tone = prefs.rawToneMappingParameters,
                        hncsFilmCurve = prefs.rawHncsFilmCurveMode,
                        filmStock = requireNotNull(film.stock), filmPrint = requireNotNull(film.print), filmTuning = film.tuning,
                        fotufilmDocument = resolveRawFotufilmDocument(prefs)
                            ?.takeIf { prefs.rawRenderingEngine == RawRenderingEngine.Fotufilm },
                    )
                }
            }.distinctUntilChanged().collectLatest { selection ->
                _currentEnginePreviewPlan.value = null
                cameraController.setEnginePreviewMetadataEnabled(false)
                if (selection == null) return@collectLatest
                try {
                    val resolver = withContext(Dispatchers.IO) {
                        RawEnginePreviewResolver(getApplication(), selection)
                    }
                    if (!resolver.needsCameraMetadata) {
                        _currentEnginePreviewPlan.value = withContext(Dispatchers.Default) { resolver.resolve(null) }
                        PLog.d(TAG, "Engine preview ${selection.engine}: scene-linear sRGB -> ${selection.engine.workingColorSpace} -> sRGB")
                    } else {
                        cameraController.setEnginePreviewMetadataEnabled(true)
                        var lastFailure: String? = null
                        var loggedDomain = false
                        // StateFlow conflates frames while resolving; collectLatest could starve
                        // CCT table generation if new AWB values arrive faster than it finishes.
                        cameraController.enginePreviewSource
                            .map { if (selection.engine.isLumix) it else it?.copy(iso = 100) }
                            .distinctUntilChanged().collect { source ->
                                val result = withContext(Dispatchers.Default) { runCatching { resolver.resolve(source) } }
                                val plan = result.getOrNull()
                                if (source?.cameraId == cameraController.enginePreviewSource.value?.cameraId) {
                                    _currentEnginePreviewPlan.value = plan
                                } else {
                                    _currentEnginePreviewPlan.value = null
                                }
                                result.exceptionOrNull()?.let { error ->
                                    if (lastFailure != error.message) PLog.e(TAG, "Engine preview ${selection.engine} rejected", error)
                                    lastFailure = error.message
                                }
                                if (plan != null) {
                                    lastFailure = null
                                    if (!loggedDomain) PLog.d(TAG, "Engine preview ${plan.engine}: restore=${plan.restoration}, output=sRGB")
                                    loggedDomain = true
                                }
                                // Resolve the newest conflated metadata at most five times a second.
                                // Curve/CCT changes must not rebake the engine atlas every camera frame.
                                delay(200)
                            }
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    PLog.e(TAG, "Unable to prepare ${selection.engine} preview", error)
                } finally {
                    cameraController.setEnginePreviewMetadataEnabled(false)
                }
            }
        }

        // 加载用户偏好设置
        viewModelScope.launch {
            val prefs = userPreferencesRepository.userPreferences.firstOrNull()
            if (prefs != null) {
                cameraController.setCaptureSettingsRetention(
                    prefs.retainCaptureSettings,
                    prefs.customCaptureSettings,
                )
                // 应用保存的画面比例
                try {
                    val savedAspectRatio = AspectRatio.valueOf(prefs.aspectRatio)
                    cameraController.setAspectRatio(savedAspectRatio)
                } catch (e: IllegalArgumentException) {
                    // 如果保存的值无效，使用默认值
                }
                cameraController.setCaptureMode(prefs.captureMode)
                cameraController.setVideoResolution(prefs.videoResolution)
                cameraController.setVideoFps(prefs.videoFps)
                cameraController.setVideoAspectRatio(prefs.videoAspectRatio)
                cameraController.setVideoLogProfile(prefs.videoLogProfile)
                cameraController.setVideoLogLutMode(prefs.videoLogLutMode)
                cameraController.setVideoBitrate(prefs.videoBitrate)
                cameraController.setVideoAudioInputId(prefs.videoAudioInputId)
                cameraController.setVideoNoiseSuppression(prefs.videoNoiseSuppressionEnabled)
                cameraController.setVideoWindReduction(prefs.videoWindReduction)
                cameraController.setVideoRecordingPath(prefs.videoRecordingPath, prefs.videoRecordingTreeUri)
                cameraController.setVideoStabilizationMode(prefs.videoStabilizationMode)
                cameraController.setVideoEnhancedStabilizationStrength(
                    prefs.videoEnhancedStabilizationStrength
                )
                cameraController.setVideoEnhancedStabilizationLookahead(
                    prefs.videoEnhancedStabilizationLookahead
                )
                cameraController.setExternalLensStabilizationConfig(
                    prefs.externalLensStabilizationConfig
                )
                cameraController.setVideoTorchEnabled(prefs.videoTorchEnabled)
                cameraController.setVideoLensLockEnabled(prefs.videoLensLockEnabled)
                cameraController.setVideoWhiteBalanceLockEnabled(prefs.videoWhiteBalanceLockEnabled)
                cameraController.setVideoCodec(prefs.videoCodec)
                cameraController.setMeteringMode(prefs.meteringMode)

                // 根据启动时的拍摄模式应用照片 LUT 或独立的视频 LUT。
                resolveLutIdForMode(prefs, prefs.captureMode)?.let {
                    setLut(it, persist = false)
                }

                // 应用保存的边框配置
                if (prefs.frameId != null) {
                    currentFrameId = prefs.frameId
                }

                showHistogram = prefs.showHistogram

                // 应用保存的网格线设置
                cameraController.setShowGrid(prefs.showGrid)
                cameraController.setGridStyle(prefs.gridStyle)

                cameraController.setUseMultipleExposure(prefs.useMultipleExposure)
                cameraController.setRawCfaCorrectionModes(prefs.rawCfaCorrectionModes)
                cameraController.setMultiFrameOutputScale(
                    resolveMultiFrameOutputScale(
                        useJpgMax = prefs.useJpgMax && !prefs.useRaw &&
                            !prefs.useMultipleExposure,
                        useRawMax = prefs.useRawMax && prefs.useRaw &&
                            !prefs.useMultipleExposure,
                        rawMaxOutputScale = prefs.rawMaxOutputScale,
                        jpgMultiFrameDenoiseOutputScale = prefs.jpgMultiFrameDenoiseOutputScale,
                    )
                )
                cameraController.setJpgMultiFrameDenoiseFrameCount(
                    prefs.jpgMultiFrameDenoiseFrameCount
                )
                cameraController.setHdrPlusMergeMode(prefs.hdrPlusMergeMode)
                cameraController.setHdrPlusFrameCount(prefs.hdrPlusFrameCount)
                cameraController.setUseJpgMaxHdrComposition(prefs.useJpgMaxHdrComposition)
                cameraController.setHdrPlusBracketExposureEnabled(
                    prefs.hdrPlusBracketExposureEnabled
                )
                cameraController.setHdrPlusLongFrameExposureEv(prefs.hdrPlusLongFrameExposureEv)
                cameraController.setHdrPlusShortFrameExposureEv(prefs.hdrPlusShortFrameExposureEv)
                cameraController.setUseLivePhoto(shouldEnableLivePhoto(prefs))
                // 应用保存的虚拟光圈
                applyDefaultVirtualAperture(prefs.defaultVirtualAperture)
            } else {
                // 如果没有任何偏好设置，使用配置文件中的默认 LUT（第一个）
                val defaultLut = availableLutList.firstOrNull { it.isDefault }
                defaultLut?.let { setLut(it.id, persist = false) }
            }

            _isInitialized.value = true
            StartupTrace.mark("CameraViewModel.isInitialized set to true")
        }

        // 监听相机状态，用于同步预览渲染参数
        viewModelScope.launch {
            state.collect { currentState ->
                if (currentState.captureMode == CaptureMode.VIDEO) {
                    validateAndCancelNonMatchingVideoLut(currentState.videoConfig.logProfile, currentLutConfig)
                }
                glSurfaceView?.let { view ->
                    view.setVideoLogProfile(currentState.videoConfig.logProfile)
                    view.setMeteringPoint(currentState.meteringReferencePoint?.let { point ->
                        android.graphics.PointF(point.first, point.second)
                    })
                    view.setMeteringMode(currentState.effectiveMeteringMode)
                    view.setAutoFocus(currentState.isAutoFocus)
                }
            }
        }

        StartupTrace.mark("CameraViewModel.init end")
    }

    fun dismissEnhancedStabilizationUnavailableDialog() {
        showEnhancedStabilizationUnavailableDialog = false
    }

    fun getAvailableRawLutList(context: Context, logCurve: TransferCurve): List<String> {
        try {
            val files = logCurve.rawFolder?.let { context.assets.list(it) }
            return files?.filter { it.endsWith(".plut") }?.toList() ?: emptyList()
        } catch (e: Exception) {
            PLog.e(TAG, "Failed to list raw luts", e)
        }
        return emptyList()
    }

    fun setUseHdrScreenMode(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.saveUseHdrScreenMode(enabled)
        }
    }

    fun setRawDcpId(dcpId: String?) {
        viewModelScope.launch {
            applyCameraFeatureUpdate(
                CameraFeatureUpdate(
                    rawDcpId = SettingValue(dcpId),
                    rawProfileToneMapMode = dcpId?.let {
                        SettingValue(RawProfileToneMapMode.Profile)
                    },
                )
            )
        }
    }

    fun setRawDcpIdsByLens(rawDcpIdsByLens: Map<String, String?>) {
        viewModelScope.launch {
            applyCameraFeatureUpdate(
                CameraFeatureUpdate(
                    rawDcpIdsByLens = SettingValue(rawDcpIdsByLens),
                    rawProfileToneMapMode = if (rawDcpIdsByLens.values.any { it != null }) {
                        SettingValue(RawProfileToneMapMode.Profile)
                    } else {
                        null
                    },
                )
            )
        }
    }

    fun setRawNoiseProfileId(profileId: String) {
        viewModelScope.launch {
            userPreferencesRepository.saveRawNoiseProfileId(profileId)
        }
    }

    fun setRawNoiseProfileIdsByLens(profileIdsByLens: Map<String, String>) {
        viewModelScope.launch {
            userPreferencesRepository.saveRawNoiseProfileIdsByLens(profileIdsByLens)
        }
    }

    fun setRawHncsFilmCurveMode(mode: HncsFilmCurveMode) {
        viewModelScope.launch {
            applyCameraFeatureUpdate(
                CameraFeatureUpdate(rawHncsFilmCurveMode = SettingValue(mode))
            )
        }
    }

    fun setRawBaselineLutId(lutId: String?) {
        viewModelScope.launch {
            applyCameraFeatureUpdate(
                CameraFeatureUpdate(rawBaselineLutId = SettingValue(lutId))
            )
        }
    }
    fun setRawColorEngine(engine: RawRenderingEngine) {
        viewModelScope.launch {
            val prefs = userPreferencesRepository.userPreferences.first()
            applyCameraFeatureUpdate(
                CameraFeatureUpdate(
                    rawRenderingEngine = SettingValue(engine),
                    rawSpectralFilmStock = if (engine == RawRenderingEngine.Spektrafilm && prefs.rawSpectralFilmStock == null) {
                        SettingValue("kodak_portra_400")
                    } else {
                        null
                    },
                    rawSpectralFilmPrint = if (engine == RawRenderingEngine.Spektrafilm && prefs.rawSpectralFilmPrint == null) {
                        SettingValue("kodak_portra_endura")
                    } else {
                        null
                    },
                    rawFotufilmDocument = if (
                        engine == RawRenderingEngine.Fotufilm &&
                        FotufilmDocumentStorage.decode(prefs.rawFotufilmDocument, "preferences") == null
                    ) {
                        SettingValue(FotufilmDocumentStorage.defaultDocument())
                    } else {
                        null
                    }
                )
            )
        }
    }
    fun setRawSpectralFilmStock(stock: String?) {
        viewModelScope.launch {
            applyCameraFeatureUpdate(
                CameraFeatureUpdate(rawSpectralFilmStock = SettingValue(stock))
            )
        }
    }
    fun setRawSpectralFilmPrint(print: String?) {
        viewModelScope.launch {
            applyCameraFeatureUpdate(
                CameraFeatureUpdate(rawSpectralFilmPrint = SettingValue(print))
            )
        }
    }
    fun setRawFotufilmDocument(document: FotufilmDocument?) {
        viewModelScope.launch {
            applyCameraFeatureUpdate(
                CameraFeatureUpdate(rawFotufilmDocument = SettingValue(document))
            )
        }
    }
    fun setRawSpectralFilmSelection(selection: SpectralFilmSelection?) {
        viewModelScope.launch {
            val prefs = userPreferencesRepository.userPreferences.first()
            val previousStock = prefs.rawSpectralFilmStock
            applyCameraFeatureUpdate(
                CameraFeatureUpdate(rawSpectralFilmStock = SettingValue(selection?.id))
            )
            if (selection != null && selection.id == previousStock) {
                userPreferencesRepository.saveRawSpectralFilmTuning(selection.id, selection.tuning)
            }
        }
    }
    fun setRawToneMappingParameters(value: RawToneMappingParameters) {
        viewModelScope.launch { userPreferencesRepository.saveRawToneMappingParameters(value) }
    }
    fun setRawExposureCompensation(value: Float) {
        viewModelScope.launch { userPreferencesRepository.saveRawExposureCompensation(value) }
    }
    fun setRawHighlightsAdjustment(value: Float) {
        viewModelScope.launch { userPreferencesRepository.saveRawHighlightsAdjustment(value) }
    }
    fun setRawShadowsAdjustment(value: Float) {
        viewModelScope.launch { userPreferencesRepository.saveRawShadowsAdjustment(value) }
    }

    fun setRawMaxSharpening(value: Float) {
        viewModelScope.launch { userPreferencesRepository.saveRawMaxSharpening(value) }
    }

    fun setRawMaxQualityTuningEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.saveRawMaxQualityTuningEnabled(enabled) }
    }

    fun setRawMaxNoiseReduction(value: Float) {
        viewModelScope.launch { userPreferencesRepository.saveRawMaxNoiseReduction(value) }
    }

    fun setRawMaxChromaNoiseReduction(value: Float) {
        viewModelScope.launch { userPreferencesRepository.saveRawMaxChromaNoiseReduction(value) }
    }

    fun setRawMinShutterSpeedNs(value: Long) {
        viewModelScope.launch { userPreferencesRepository.saveRawMinShutterSpeedNs(value) }
    }
    fun setRawDROEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.updateRawDROEnabled(enabled) }
    }
    fun setRawBlackPointCorrection(value: Float) {
        viewModelScope.launch { userPreferencesRepository.saveRawBlackPointCorrection(value) }
    }
    fun setRawWhitePointCorrection(value: Float) {
        viewModelScope.launch { userPreferencesRepository.saveRawWhitePointCorrection(value) }
    }
    fun setRawLensShadingCorrectionEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.saveRawLensShadingCorrectionEnabled(enabled) }
    }
    fun setRawDngMetadataCorrections(
        cameraId: String,
        corrections: IszRawDngMetadataCorrections
    ) {
        viewModelScope.launch {
            userPreferencesRepository.saveRawDngMetadataCorrections(cameraId, corrections)
        }
    }
    fun setRawBlackLevelMode(mode: String) {
        viewModelScope.launch {
            userPreferencesRepository.saveRawBlackLevelMode(state.value.currentCameraId, mode)
        }
    }
    fun setRawCustomBlackLevel(value: Float) {
        viewModelScope.launch {
            userPreferencesRepository.saveRawCustomBlackLevel(state.value.currentCameraId, value)
        }
    }
    fun setRawWhiteLevelMode(mode: String) {
        viewModelScope.launch {
            userPreferencesRepository.saveRawWhiteLevelMode(state.value.currentCameraId, mode)
        }
    }
    fun setRawCustomWhiteLevel(value: Float) {
        viewModelScope.launch {
            userPreferencesRepository.saveRawCustomWhiteLevel(state.value.currentCameraId, value)
        }
    }
    fun setRawCfaCorrectionMode(mode: String) {
        viewModelScope.launch {
            userPreferencesRepository.saveRawCfaCorrectionMode(state.value.currentCameraId, mode)
        }
    }
    fun importRawDcp(uri: Uri, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = contentRepository.getCustomImportManager().importDcp(uri) != null
            if (success) {
                contentRepository.refreshCustomContent()
            }
            onComplete(success)
        }
    }

    fun importRawDcps(uris: List<Uri>, onComplete: (importedDcps: List<DcpInfo>, failedCount: Int) -> Unit) {
        viewModelScope.launch {
            val importedIds = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    contentRepository.getCustomImportManager().importDcp(uri)
                }
            }
            val importedDcps = if (importedIds.isNotEmpty()) {
                contentRepository.refreshCustomContent()
                val dcpById = contentRepository.getAvailableDcps().associateBy { it.id }
                importedIds.mapNotNull { dcpById[it] }
            } else {
                emptyList()
            }
            onComplete(importedDcps, uris.size - importedDcps.size)
        }
    }

    fun deleteRawDcp(dcpId: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                contentRepository.getCustomImportManager().deleteCustomDcp(dcpId)
            }
            if (success) {
                userPreferencesRepository.removeRawDcpReferences(dcpId)
                contentRepository.refreshCustomContent()
            }
            onComplete(success)
        }
    }

    fun importRawNoiseProfiles(
        uris: List<Uri>,
        onComplete: (importedProfiles: List<RawNoiseProfileInfo>, failedCount: Int) -> Unit,
    ) {
        viewModelScope.launch {
            val importedIds = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    contentRepository.getCustomImportManager().importRawNoiseProfile(uri)
                }
            }
            val importedProfiles = if (importedIds.isNotEmpty()) {
                contentRepository.refreshCustomContent()
                val profilesById = contentRepository.getAvailableRawNoiseProfiles().associateBy { it.id }
                importedIds.mapNotNull(profilesById::get)
            } else {
                emptyList()
            }
            onComplete(importedProfiles, uris.size - importedProfiles.size)
        }
    }

    fun deleteRawNoiseProfile(profileId: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                contentRepository.getCustomImportManager().deleteCustomRawNoiseProfile(profileId)
            }
            if (success) {
                userPreferencesRepository.removeRawNoiseProfileReferences(profileId)
                contentRepository.refreshCustomContent()
            }
            onComplete(success)
        }
    }

    /**
     * 打开相机（Camera2 接口）
     */
    fun openCamera(surfaceTexture: SurfaceTexture) {
        PLog.d(TAG, "openCamera")
        if (currentSurfaceTexture === surfaceTexture && (state.value.isPreviewActive || cameraOpenInFlight)) {
            PLog.d(
                TAG,
                "openCamera skipped: same SurfaceTexture active=${state.value.isPreviewActive}, inFlight=$cameraOpenInFlight"
            )
            return
        }
        currentSurfaceTexture = surfaceTexture
        requestCameraOpen(surfaceTexture)
    }

    private fun requestCameraOpen(
        surfaceTexture: SurfaceTexture,
        preserveVideoRecording: Boolean = false,
    ) {
        cameraReopenJob?.cancel()
        cameraOpenInFlight = true
        cameraReopenJob = viewModelScope.launch {
            isInitialized.first { it }
            if (currentSurfaceTexture !== surfaceTexture) return@launch
            syncVendorCaptureSettingsToController()
            cameraController.openCamera(
                surfaceTexture = surfaceTexture,
                preserveVideoRecording = preserveVideoRecording,
            )
        }
    }

    suspend fun prepareCamera(): Boolean {
        if (!cameraController.prepareCameras()) return false
        if (!hasAppliedDefaultFocalLength) {
            val defaultFocalLength = userPreferencesRepository.userPreferences
                .firstOrNull()
                ?.defaultFocalLength
                ?: 0f
            if (defaultFocalLength != 0f) {
                applyStartupDefaultFocalLength(defaultFocalLength)
            }
            hasAppliedDefaultFocalLength = true
        }
        return cameraController.preparePreviewSize()
    }

    /**
     * 关闭相机
     */
    fun closeCamera(surfaceTexture: SurfaceTexture? = null) {
        if (surfaceTexture != null && currentSurfaceTexture !== surfaceTexture) {
            PLog.d(
                TAG,
                "closeCamera skipped: stale SurfaceTexture destroyed=" +
                        "${System.identityHashCode(surfaceTexture)}, current=" +
                        "${currentSurfaceTexture?.let { System.identityHashCode(it) }}"
            )
            return
        }
        stopIntervalCapture()
        stopFocusBracket()
        stopAdvancedCapture()
        _advancedCameraState.update { it.copy(preview = null) }
        cameraReopenJob?.cancel()
        cameraOpenInFlight = false
        currentSurfaceTexture = null
        cameraController.closeCamera(expectedSurfaceTexture = surfaceTexture)
    }

    private fun scheduleCameraListRefreshAfterError(errorCode: Int) {
        val shouldRefreshCameraList = when (errorCode) {
            CameraDevice.StateCallback.ERROR_CAMERA_DEVICE,
            CameraDevice.StateCallback.ERROR_CAMERA_SERVICE,
            Camera2Controller.ERROR_CAMERA_OPEN_FAILED,
            Camera2Controller.ERROR_CAMERA_CHARACTERISTICS_UNAVAILABLE -> true

            else -> false
        }
        if (!shouldRefreshCameraList) return

        cameraErrorRecoveryJob?.cancel()
        cameraErrorRecoveryJob = viewModelScope.launch {
            delay(800)
            cameraController.refreshCameraList()
        }
    }

    private suspend fun prewarmCapturePipeline() {
        val prefs = userPreferencesRepository.userPreferences.firstOrNull()
            ?: return
        val currentState = state.value
        val captureSize = currentState.currentCaptureSize
        val rawCaptureEnabled = prefs.useRaw
        supervisorScope {
            val dcpPrewarmJob = if (rawCaptureEnabled) {
                async {
                    runCatching {
                        prewarmRawDcp(prefs.rawDcpIdForLens(currentState.currentCameraId))
                    }.onFailure { error ->
                        if (error is CancellationException) throw error
                        PLog.w(TAG, "RAW DCP prewarm failed", error)
                    }
                }
            } else {
                null
            }

            // Keep the two GL warmups sequential. They use independent contexts but still
            // share one GPU with preview, so running both at once only increases contention.
            if (rawCaptureEnabled) {
                runCatching {
                    RawDemosaicProcessor.getInstance().prewarmCapturePipeline(
                        getApplication<Application>().applicationContext,
                        prefs.rawRenderingEngine,
                        captureWidth = captureSize.width,
                        captureHeight = captureSize.height,
                    )
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    PLog.w(TAG, "RAW capture pipeline prewarm failed", error)
                }
            }
            currentCoroutineContext().ensureActive()
            runCatching {
                contentRepository.imageProcessor.prewarmCapturePipeline()
            }.onFailure { error ->
                if (error is CancellationException) throw error
                PLog.w(TAG, "LUT capture pipeline prewarm failed", error)
            }
            dcpPrewarmJob?.await()
        }
    }

    fun onFirstPreviewFrame() {
        if (startupPrewarmAttempted) return
        startupPrewarmAttempted = true
        startupPrewarmJob = viewModelScope.launch {
            delay(FIRST_PREVIEW_PREWARM_GRACE_MS)
            val appContext = getApplication<Application>().applicationContext
            val currentState = state.value
            if (
                !currentState.isPreviewActive ||
                currentState.captureMode != CaptureMode.PHOTO ||
                currentState.isCapturing
            ) {
                PLog.d(
                    TAG,
                    "Skip startup capture prewarm: preview=${currentState.isPreviewActive} " +
                        "mode=${currentState.captureMode} " +
                        "capturing=${currentState.isCapturing}",
                )
                return@launch
            }
            if (!isStartupCapturePrewarmAllowed(appContext)) return@launch
            prewarmCapturePipeline()
        }
    }

    private fun isStartupCapturePrewarmAllowed(context: Context): Boolean {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val powerManager = context.getSystemService(PowerManager::class.java)
        val constrainedMemory = activityManager?.let {
            it.isLowRamDevice || it.memoryClass < MIN_PREWARM_MEMORY_CLASS_MB
        } ?: true
        val powerSave = powerManager?.isPowerSaveMode ?: true
        val thermalStatus = powerManager?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE
        val thermallyConstrained = thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE
        val allowed = !constrainedMemory && !powerSave && !thermallyConstrained
        if (!allowed) {
            PLog.d(
                TAG,
                "Skip startup capture prewarm: lowMemory=$constrainedMemory " +
                    "memoryClass=${activityManager?.memoryClass}MB powerSave=$powerSave " +
                    "thermalStatus=$thermalStatus",
            )
        }
        return allowed
    }

    private suspend fun prewarmRawDcp(dcpId: String?) = withContext(Dispatchers.IO) {
        val dcpInfo = dcpId?.let { id ->
            contentRepository.getAvailableDcps().firstOrNull { it.id == id }
        } ?: return@withContext
        DcpProfileParser.prewarm(getApplication<Application>(), dcpInfo)
    }

    /**
     * 检查相机状态并在必要时恢复
     */
    fun checkAndRecoverCamera() {
        if (!state.value.isPreviewActive) {
            resetExposureCompensationForCameraRestart()
        }

        // 如果有保存的 SurfaceTexture，重新打开相机
        currentSurfaceTexture?.let { texture ->
            if (!state.value.isPreviewActive && !cameraOpenInFlight) {
                requestCameraOpen(texture)
            } else {
                PLog.d(
                    TAG,
                    "checkAndRecoverCamera skipped: active=${state.value.isPreviewActive}, inFlight=$cameraOpenInFlight"
                )
            }
        }
        restorePreviewLutAfterResume()
    }

    private fun resetExposureCompensationForCameraRestart() {
        if (cameraController.retainCaptureSettingsEnabled) return
        if (state.value.exposureCompensation == 0) return
        PLog.d(TAG, "Reset exposure compensation for camera restart")
        cameraController.setExposureCompensation(0)
    }

    private fun restorePreviewLutAfterResume() {
        val lutId = currentLutId.value
        val loadGeneration = lutLoadGeneration
        PLog.d(TAG, "restorePreviewLutAfterResume: lutId=$lutId")
        viewModelScope.launch {
            val candidateLut = withContext(Dispatchers.IO) {
                contentRepository.lutManager.loadLut(lutId)
            }
            if (currentLutId.value != lutId || lutLoadGeneration != loadGeneration) {
                return@launch
            }
            val profile = state.value.videoConfig.logProfile
            val loadedLut = candidateLut?.takeIf {
                state.value.captureMode != CaptureMode.VIDEO || profile.matchesLut(it.curve, it.colorSpace)
            }
            if (candidateLut != null && loadedLut == null) applyLut("none")
            currentLutConfig = loadedLut
            cameraController.setLutEnabled(loadedLut != null)
            cameraController.setLogLutActive(loadedLut?.curve?.isLog == true)
            glSurfaceView?.let { view ->
                val currentState = state.value
                view.setEnginePreview(currentEnginePreviewPlan.value)
                view.setBaselineLut(currentBaselineLutConfig)
                view.setBaselineLutEnabled(currentBaselineLutConfig != null)
                view.setBaselineParams(currentBaselineRecipeParams.value)
                view.setLut(loadedLut)
                view.setLutEnabled(loadedLut != null)
                view.setParams(currentRecipeParams.value)
                view.setColorRecipeEnabled(!currentRecipeParams.value.isDefault())
                view.setVideoLogProfile(currentState.videoConfig.logProfile)
                view.restoreRenderStateAfterResume()
            }
        }
    }

    private suspend fun buildPhotoMetadata(
        width: Int,
        height: Int,
        captureInfo: CaptureInfo,
        sharpeningValue: Float = 0f,
        noiseReductionValue: Float = 0f,
        chromaNoiseReductionValue: Float = 0f,
        captureMode: String? = null,
        multipleExposureFrameCount: Int? = null,
        baselineTarget: BaselineColorCorrectionTarget? = null,
        settings: CaptureSettingsSnapshot = snapshotCaptureSettings(),
    ): MediaMetadata {
        val lutIdToSave = settings.lutId
        val aspectRatio = settings.state.aspectRatio
        val frameIdToSave = settings.frameId
        val currentCameraId = settings.cameraId

        val sensorOrientation = settings.sensorOrientation
        val lensFacing = settings.lensFacing
        val deviceRotation = settings.deviceRotation
        val baseRotation = if (lensFacing == CameraCharacteristics.LENS_FACING_FRONT) {
            (sensorOrientation - deviceRotation + 360) % 360
        } else {
            (sensorOrientation + deviceRotation) % 360
        }

        val userPrefs: UserPreferences? = settings.preferences
        val orientationOffset = userPrefs?.cameraOrientationOffsets?.get(currentCameraId) ?: 0
        val rotation = (baseRotation + orientationOffset) % 360
        val shouldMirror = lensFacing == CameraCharacteristics.LENS_FACING_FRONT &&
            settings.preferences.mirrorFrontCamera
        val aperture = if (settings.state.isVirtualApertureEnabled) settings.state.virtualAperture else null

        val rawToneMappingParameters = resolveCaptureRawToneMappingParameters(userPrefs)
        val defaultHdrEffectEnabled = defaultHdrEffectEnabled(
            hasEmbeddedGainmap = false,
            userPrefs = userPrefs,
        )
        val baselineMetadata = resolveBaselineMetadata(
            target = baselineTarget,
            userPrefs = userPrefs,
        )
        val effectiveRawAutoExposure = resolveEffectiveRawAutoExposure()

        val spectralFilmSettings = resolveRawSpectralFilmSettings(userPrefs)

        return MediaMetadata(
            lutId = lutIdToSave,
            frameId = frameIdToSave,
            colorRecipeParams = settings.recipe,
            baselineTarget = baselineMetadata?.first,
            baselineLutId = baselineMetadata?.second,
            baselineColorRecipeParams = baselineMetadata?.third,
            sharpening = sharpeningValue,
            noiseReduction = noiseReductionValue,
            chromaNoiseReduction = chromaNoiseReductionValue,
            captureNoiseReductionLevel = settings.state.nrLevel,
            rawDcpId = userPrefs?.rawDcpIdForLens(currentCameraId),
            rawHncsProfileId = userPrefs?.rawHncsProfileId,
            rawHncsRenderIntent = userPrefs?.rawHncsRenderIntent
                ?: HncsRenderIntent.Standard,
            rawHncsFilmCurveMode = userPrefs?.rawHncsFilmCurveMode
                ?: HncsFilmCurveMode.Standard,
            rawExposureCompensation = userPrefs?.rawExposureCompensation ?: 0f,
            rawAutoExposure = effectiveRawAutoExposure,
            customProperties = rawProcessingMetadataProperties(
                userPrefs, cameraController.getCurrentSensorPhysicalAreaMm2(), state.value.zoomRatio,
            ) + settings.sequenceProperties,
            rawHighlightsAdjustment = userPrefs?.rawHighlightsAdjustment ?: 0f,
            rawShadowsAdjustment = userPrefs?.rawShadowsAdjustment ?: 0f,
            rawBlackPointCorrection = userPrefs?.rawBlackPointCorrection ?: 0f,
            rawWhitePointCorrection = userPrefs?.rawWhitePointCorrection ?: 0f,
            rawLensShadingCorrectionEnabled = userPrefs?.rawLensShadingCorrectionEnabled,
            rawBlackLevelMode = userPrefs?.rawBlackLevelModes?.get(currentCameraId) ?: "Default",
            rawCustomBlackLevel = userPrefs?.rawCustomBlackLevels?.get(currentCameraId) ?: 0f,
            rawWhiteLevelMode = userPrefs?.rawWhiteLevelModes?.get(currentCameraId)
                ?: RawWhiteLevelCorrection.MODE_DEFAULT,
            rawCustomWhiteLevel = userPrefs?.rawCustomWhiteLevels?.get(currentCameraId) ?: 0f,
            rawCfaCorrectionMode = userPrefs?.rawCfaCorrectionModes?.get(currentCameraId) ?: RawCfaCorrection.MODE_DEFAULT,
            cameraId = currentCameraId,
            rawBlackBorderCrop = settings.blackBorderCrop,
            rawRenderingEngine = resolveCaptureRawRenderingEngine(userPrefs),
            rawToneMappingParameters = rawToneMappingParameters,
            rawOutputUpscaleMode = userPrefs?.rawOutputUpscaleMode ?: RawOutputUpscaleMode.DEFAULT,
            spectralFilmStock = spectralFilmSettings.stock,
            spectralFilmPrint = spectralFilmSettings.print,
            spectralFilmCDensityGain = spectralFilmSettings.tuning.cDensityGain,
            spectralFilmMDensityGain = spectralFilmSettings.tuning.mDensityGain,
            spectralFilmYDensityGain = spectralFilmSettings.tuning.yDensityGain,
            fotufilmDocument = resolveRawFotufilmDocument(userPrefs)?.toJson(),
            width = width,
            height = height,
            ratio = aspectRatio,
            rotation = rotation,
            deviceModel = DeviceUtil.model,
            brand = captureInfo.make,
            dateTaken = captureInfo.captureTime,
            latitude = captureInfo.latitude,
            longitude = captureInfo.longitude,
            altitude = captureInfo.altitude,
            iso = captureInfo.iso,
            shutterSpeed = captureInfo.formatExposureTime(),
            focalLength = captureInfo.formatFocalLength(),
            focalLength35mm = captureInfo.formatFocalLength35mm(),
            aperture = captureInfo.formatAperture(),
            exposureBias = settings.state.exposureBias,
            droMode = settings.preferences.droMode,
            isMirrored = shouldMirror,
            colorSpace = captureInfo.colorSpace,
            computationalAperture = aperture,
            focusPointX = settings.state.focusPoint?.first,
            focusPointY = settings.state.focusPoint?.second,
            manualHdrEffectEnabled = defaultHdrEffectEnabled,
            captureMode = captureMode,
            multipleExposureFrameCount = multipleExposureFrameCount
        )
    }

    private fun resolveRawSpectralFilmSettings(
        userPrefs: UserPreferences?
    ): RawSpectralFilmSettings {
        val stock = userPrefs?.rawSpectralFilmStock ?: "kodak_portra_400"
        return RawSpectralFilmSettings(
            stock = stock,
            print = userPrefs?.rawSpectralFilmPrint ?: "kodak_portra_endura",
            tuning = (userPrefs?.rawSpectralFilmTuningsByStock?.get(stock) ?: SpectralFilmTuning.DEFAULT).normalized()
        )
    }

    /**
     * The Fotufilm document a capture records: the stored one, else the default film when the
     * capture renders with Fotufilm (as [resolveRawSpectralFilmSettings] supplies a default stock).
     */
    private fun resolveRawFotufilmDocument(userPrefs: UserPreferences?): FotufilmDocument? {
        return FotufilmDocumentStorage.decode(userPrefs?.rawFotufilmDocument, "preferences")
            ?: if (resolveCaptureRawRenderingEngine(userPrefs) == RawRenderingEngine.Fotufilm) {
                FotufilmDocumentStorage.defaultDocument()
            } else {
                null
            }
    }

    private fun resolvePreviewBaselineLut(userPrefs: UserPreferences): LutConfig? {
        val baselineLutId = userPrefs.rawBaselineLutId
            .takeIf { resolvePreviewBaselineTarget(userPrefs) != null }
        return baselineLutId?.let { contentRepository.lutManager.loadLut(it) }
    }

    private suspend fun resolveBaselineMetadata(
        target: BaselineColorCorrectionTarget?,
        userPrefs: UserPreferences? = null
    ): Triple<BaselineColorCorrectionTarget, String, ColorRecipeParams>? {
        if (target != BaselineColorCorrectionTarget.RAW) return null
        val preferences = userPrefs ?: userPreferencesRepository.userPreferences.firstOrNull() ?: return null
        val baselineLutId = preferences.rawBaselineLutId ?: return null
        val params = contentRepository.lutManager.loadColorRecipeParams(baselineLutId, target)
        return Triple(target, baselineLutId, params)
    }

    private fun isRawCaptureFormat(format: Int): Boolean {
        return when (format) {
            ImageFormat.RAW_SENSOR,
            ImageFormat.RAW10,
            ImageFormat.RAW12 -> true
            else -> false
        }
    }

    private fun currentRawBlackBorderCrop(): RawBlackBorderCrop {
        val cameraInfo = state.value.getCurrentCameraInfo() ?: return RawBlackBorderCrop()
        return cameraInfo.rawBlackBorderCrop.takeIf { cameraInfo.isVirtualIszLens }
            ?: RawBlackBorderCrop()
    }

    private fun defaultHdrEffectEnabled(
        hasEmbeddedGainmap: Boolean,
        userPrefs: UserPreferences?,
    ): Boolean {
        if (hasEmbeddedGainmap) return true
        return userPrefs?.ultraHdrGainMapEnabled ?: false
    }

    fun setUseMultipleExposure(enabled: Boolean) {
        viewModelScope.launch {
            applyCameraFeatureUpdate(
                CameraFeatureUpdate(useMultipleExposure = SettingValue(enabled))
            )
            if (enabled) {
                userPreferencesRepository.saveUseLivePhoto(false)
                cameraController.setUseLivePhoto(false)
            }
        }
    }

    fun cancelMultipleExposureSession(expectedSessionId: String? = null) {
        val sessionId = expectedSessionId ?: multipleExposureState.sessionId
        if (sessionId != null && sessionId in finalizingMultipleExposureSessions) return
        sessionId?.let { GalleryManager.clearMultipleExposureSession(getApplication(), it) }
        if (expectedSessionId != null && multipleExposureState.sessionId != expectedSessionId) return
        multipleExposureMetadata = null
        multipleExposureState = multipleExposureState.copy(
            sessionId = null,
            capturedCount = 0,
            frames = emptyList(),
            isProcessing = false,
            previewBitmap = null
        )
    }

    fun undoLastMultipleExposureFrame() {
        if (multipleExposureState.isProcessing) return
        val sessionId = multipleExposureState.sessionId ?: return
        if (sessionId in finalizingMultipleExposureSessions) return
        if (!GalleryManager.removeLastMultipleExposureFrame(getApplication(), sessionId)) return
        refreshMultipleExposurePreview(sessionId)
    }

    fun finishMultipleExposureSession() {
        if (!multipleExposureState.canFinish) return
        val captureId = _capturedThumbnail.value?.captureId
        val sessionId = multipleExposureState.sessionId ?: return
        finalizingMultipleExposureSessions.add(sessionId)
        multipleExposureState = multipleExposureState.copy(isProcessing = true)
        CaptureProcessingQueue.enqueue(emptyList()) {
            finishMultipleExposureSessionNow { photoId ->
                _capturedThumbnail.update { current ->
                    if (current?.captureId == captureId) {
                        current?.copy(savedPhotoId = photoId, processingFinished = true)
                    } else current
                }
            }
        }
    }

    private suspend fun finishMultipleExposureSessionNow(onSaved: (String) -> Unit) {
        val session = multipleExposureState
        val sessionId = session.sessionId ?: return
        val baseMetadata = multipleExposureMetadata ?: run {
            finalizingMultipleExposureSessions.remove(sessionId)
            multipleExposureState = multipleExposureState.copy(isProcessing = false)
            return
        }
        finalizingMultipleExposureSessions.add(sessionId)
        withContext(Dispatchers.IO) {
            multipleExposureState = multipleExposureState.copy(isProcessing = true)
            try {
                val context = getApplication<Application>()
                val sourceFiles = GalleryManager.getMultipleExposureFrameFiles(context, sessionId).toList()
                check(sourceFiles.size >= 2 && sourceFiles.all { it.isFile }) { "Missing composite source frames" }
                val composedBitmap = GalleryManager.composeMultipleExposurePhoto(context, sessionId, session.blendMode, sourceFiles) ?: run {
                    multipleExposureState = multipleExposureState.copy(isProcessing = false)
                    return@withContext
                }
                val shouldAutoSave = autoSaveAfterCapture.firstOrNull() ?: false
                val photoQualityValue = photoQuality.firstOrNull() ?: 95
                val sharpeningValue = 0f
                val noiseReductionValue = 0f
                val chromaNoiseReductionValue = 0f

                val photoId = GalleryManager.preparePhoto(
                    context,
                    // preparePhoto derives output dimensions from the sensor dimensions and rotation.
                    // The composed bitmap has already been cropped and rotated once.
                    baseMetadata.copy(
                        captureMode = "multiple_exposure",
                        multipleExposureFrameCount = sourceFiles.size,
                        customProperties = baseMetadata.customProperties + mapOf("multipleExposureBlendMode" to session.blendMode.name),
                    ),
                    null,
                    previewThumbnail,
                    false,
                    1.0f
                ) ?: run {
                    composedBitmap.recycle()
                    multipleExposureState = multipleExposureState.copy(isProcessing = false)
                    return@withContext
                }

                GalleryManager.saveBitmapPhoto(
                    context,
                    photoId,
                    composedBitmap,
                    shouldAutoSave,
                    contentRepository.photoProcessor,
                    sharpeningValue,
                    noiseReductionValue,
                    chromaNoiseReductionValue,
                    photoQualityValue
                )
                composedBitmap.recycle()
                GalleryManager.preserveMultipleExposureSources(
                    context, sessionId, photoId, session.blendMode, sourceFiles
                )
                finalizingMultipleExposureSessions.remove(sessionId)
                cancelMultipleExposureSession(expectedSessionId = sessionId)
                onSaved(photoId)
                _imageSavedEvent.emit(photoId)
            } catch (e: Exception) {
                PLog.e(TAG, "Failed to finish multiple exposure session", e)
                if (multipleExposureState.sessionId == sessionId) {
                    multipleExposureState = multipleExposureState.copy(isProcessing = false)
                }
            } finally {
                finalizingMultipleExposureSessions.remove(sessionId)
            }
        }
    }

    fun setVideoCodec(codec: com.hinnka.mycamera.video.VideoCodec) {
        viewModelScope.launch {
            userPreferencesRepository.saveVideoCodec(codec)
        }
    }

    fun pauseVideoRecording() {
        cameraController.pauseVideoRecording()
    }

    fun resumeVideoRecording() {
        cameraController.resumeVideoRecording()
    }

    private fun refreshMultipleExposurePreview(sessionId: String): Job {
        return viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val frameFiles = GalleryManager.getMultipleExposureFrameFiles(context, sessionId)
            val preview = if (frameFiles.isNotEmpty()) {
                GalleryManager.composeMultipleExposurePreview(context, sessionId, blendMode = multipleExposureState.blendMode)
            } else {
                null
            }
            multipleExposureState = multipleExposureState.copy(
                capturedCount = frameFiles.size,
                frames = frameFiles.mapIndexed { index, file -> MultipleExposureFrame(index + 1, file) },
                previewBitmap = preview,
                sessionId = if (frameFiles.isEmpty()) null else sessionId,
                isProcessing = false
            )
            if (frameFiles.isEmpty()) {
                multipleExposureMetadata = null
            }
        }
    }

    private suspend fun handleMultipleExposureFrameCaptured(
        image: SafeImage,
        captureInfo: CaptureInfo,
        settings: CaptureSettingsSnapshot
    ) {
        try {
            if (isRawCaptureFormat(image.format)) {
                image.close()
                PLog.w(TAG, "Multiple exposure currently supports processed YUV captures only")
                return
            }

            val context = getApplication<Application>()
            val sessionId = multipleExposureState.sessionId ?: UUID.randomUUID().toString()
            val frameIndex = multipleExposureState.capturedCount + 1
            val metadata = multipleExposureMetadata ?: buildPhotoMetadata(
                width = image.width,
                height = image.height,
                captureInfo = captureInfo,
                captureMode = "multiple_exposure",
                multipleExposureFrameCount = multipleExposureState.targetCount,
                settings = settings
            ).also { multipleExposureMetadata = it }

            val frameFile = GalleryManager.saveMultipleExposureFrame(
                context,
                sessionId,
                frameIndex,
                image,
                metadata.rotation,
                settings.state.aspectRatio,
                metadata.isMirrored,
                settings.preferences.photoQuality
            ) ?: return

            multipleExposureState = multipleExposureState.copy(
                sessionId = sessionId,
                frames = multipleExposureState.frames + MultipleExposureFrame(frameIndex, frameFile),
                capturedCount = frameIndex
            )
            if (frameIndex >= multipleExposureState.targetCount) {
                finishMultipleExposureSessionNow { markCapturedThumbnailSaved(settings, it) }
            } else {
                refreshMultipleExposurePreview(sessionId).join()
            }
        } catch (e: Exception) {
            PLog.e(TAG, "Failed to handle multiple exposure frame", e)
        }
    }

    fun stopFocusBracket() {
        focusBracketJob?.cancel()
        _focusBracketState.update {
            if (it.isRunning) it.copy(outcome = FocusBracketOutcome.STOPPED) else it
        }
    }

    fun startFocusBracket(near: Float, far: Float, frames: Int, autoMerge: Boolean = true) {
        if (advancedCameraJob?.isCompleted == false || _advancedCameraState.value.saving) return
        val current = state.value
        if (focusBracketJob?.isCompleted == false || _focusBracketState.value.isRunning || _intervalCaptureState.value.isRunning ||
            current.captureMode != CaptureMode.PHOTO || !current.isPreviewActive || current.isCapturing ||
            current.countdownValue > 0 || current.isFocusLocked || multipleExposureState.enabled ||
            !near.isFinite() || !far.isFinite() || near <= far || far < 0f ||
            near > current.minimumFocusDistance || !current.minimumFocusDistance.isFinite()
        ) return
        val count = frames.coerceIn(2, 15)
        val generation = ++focusBracketGeneration
        val sequenceId = UUID.randomUUID().toString()
        val cameraId = current.currentCameraId
        _focusBracketState.value = FocusBracketState(FocusBracketOutcome.RUNNING, count)
        focusBracketJob = viewModelScope.launch {
            try {
                val savedIds = mutableListOf<String>()
                val distances = List(count) { index -> near + (far - near) * index / (count - 1) }
                val completed = cameraController.runFocusBracket(cameraId, distances) { index, distance ->
                    CaptureProcessingQueue.state.first { it.pendingCount < 2 }
                    ensureActive()
                    val lut = getLutInfo(currentLutId.value)
                    if (lut?.isVip == true && !isPurchased.value) {
                        showPaymentDialog = true
                        false
                    } else {
                        val owner = currentCoroutineContext()[Job]
                        val slotAllowed = java.util.concurrent.atomic.AtomicBoolean(true)
                        val accepted = CompletableDeferred<Long>()
                        val saved = CompletableDeferred<String?>()
                        var captureId: Long? = null
                        try {
                            submitPhotoCapture(
                                onAccepted = { id ->
                                    captureId = id
                                    focusBracketCaptureResults[id] = saved
                                    _focusBracketState.update {
                                        if (generation == focusBracketGeneration) it.copy(accepted = it.accepted + 1) else it
                                    }
                                    accepted.complete(id)
                                },
                                shouldAccept = {
                                    slotAllowed.get() && owner?.isActive == true &&
                                        state.value.currentCameraId == cameraId && state.value.isPreviewActive &&
                                        !state.value.isAutoFocus && !state.value.isHyperfocalFocusEnabled &&
                                        kotlin.math.abs(state.value.focusDistance - distance) <= 0.02f &&
                                        !multipleExposureState.enabled
                                },
                                sequenceProperties = mapOf(
                                    "focusBracketId" to sequenceId, "focusBracketIndex" to index.toString(),
                                    "focusBracketCount" to count.toString(), "focusBracketDiopters" to distance.toString(),
                                ),
                            )
                            val admitted = withTimeoutOrNull(2000L) { accepted.await() }
                            val savedId = if (admitted == null) null else withTimeoutOrNull(120000L) { saved.await() }
                            if (savedId != null) savedIds += savedId
                            savedId != null
                        } finally {
                            slotAllowed.set(false)
                            captureId?.let { focusBracketCaptureResults.remove(it) }
                        }
                    }
                }
                // runFocusBracket restores the original lens state before post-processing begins.
                if (completed && autoMerge) {
                    _focusBracketState.update { it.copy(outcome = FocusBracketOutcome.MERGING) }
                    val mergedId = com.hinnka.mycamera.processor.CaptureSequenceMerge.merge(
                        getApplication(), savedIds,
                        com.hinnka.mycamera.processor.PhotoSequenceOperation.FOCUS_STACK,
                        contentRepository.photoProcessor,
                    )
                    _focusBracketState.update { it.copy(mergedPhotoId = mergedId) }
                    _imageSavedEvent.emit(mergedId)
                }
                _focusBracketState.update {
                    if (generation == focusBracketGeneration && it.isRunning) {
                        it.copy(outcome = if (completed) FocusBracketOutcome.COMPLETE else FocusBracketOutcome.FAILED)
                    } else it
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                PLog.e(TAG, "Focus bracket failed", error)
                _focusBracketState.update {
                    if (generation == focusBracketGeneration) it.copy(outcome = FocusBracketOutcome.FAILED, mergeFailed = autoMerge && it.outcome == FocusBracketOutcome.MERGING) else it
                }
            }
        }
    }

    private fun advancedSessionFingerprint(): List<Any?> {
        val current = state.value
        return listOf(current.currentCameraId, current.captureMode, current.useRaw,
            capturePreviewThumbnailRotation(), current.aspectRatio, current.zoomRatio,
            current.isIsoAuto, current.isShutterSpeedAuto,
            current.iso.takeUnless { current.isIsoAuto }, current.shutterSpeed.takeUnless { current.isShutterSpeedAuto },
            current.isAutoFocus, current.focusDistance.takeUnless { current.isAutoFocus },
            current.awbMode, current.awbTemperature, whiteBalanceIntent.value,
            current.isExposureLocked, current.flashMode,
            currentLutId.value, currentFrameId, getMergedRecipeParams(),
            userPreferences.value.rawRenderingEngine, userPreferences.value.rawToneMappingParameters,
            userPreferences.value.rawExposureCompensation)
    }

    fun stopAdvancedCapture() {
        advancedCameraJob?.cancel()
        _advancedCameraState.update { it.copy(status = getApplication<Application>().getString(R.string.advanced_stopped)) }
    }

    private val advancedCapturePreferences = com.hinnka.mycamera.camera.AdvancedCapturePreferences(getApplication())
    private val _selectedAdvancedCapture = MutableStateFlow(advancedCapturePreferences.selected())
    fun advancedCaptureConfiguration(): AdvancedCaptureSelection? = _selectedAdvancedCapture.value ?: advancedCapturePreferences.configuration()
    val selectedAdvancedCapture = _selectedAdvancedCapture.asStateFlow()
    fun selectAdvancedCapture(mode: AdvancedCameraMode, frames: Int, gapSeconds: Int, options: AdvancedCaptureOptions = AdvancedCaptureOptions()) {
        setMotionPriorityEnabled(false)
        if (_advancedCameraState.value.running || _advancedCameraState.value.saving) return
        _advancedEngineRequested.value = true
        advancedCapturePreferences.save(AdvancedCaptureSelection(mode, frames, if (mode == AdvancedCameraMode.HIGH_DETAIL) 0 else gapSeconds, options))
        _selectedAdvancedCapture.value = advancedCapturePreferences.selected()
    }
    fun clearAdvancedCaptureSelection() {
        if (!_advancedCameraState.value.running && !_advancedCameraState.value.saving) {
            advancedCapturePreferences.clearSelection()
            _selectedAdvancedCapture.value = null
            _advancedEngineRequested.value = false
        }
    }
    fun toggleAdvancedCaptureFromShutter(): Boolean {
        if (state.value.captureMode != CaptureMode.PHOTO) return false
        if (_advancedCameraState.value.saving) return true
        if (_advancedCameraState.value.running) {
            if (_advancedCameraState.value.mode in listOf(AdvancedCameraMode.PRE_CAPTURE, AdvancedCameraMode.PREVIEW_COMPOSITE, AdvancedCameraMode.PREVIEW_ND)) saveAdvancedPreview()
            else {
                val source = _advancedCameraState.value.mode in listOf(AdvancedCameraMode.LIVE_COMPOSITE, AdvancedCameraMode.LIGHT_GRAFFITI, AdvancedCameraMode.LIVE_ND)
                if (source) {
                    if (advancedTemporalCommittedFrames < 2) {
                        stopAdvancedCapture()
                        _advancedCameraState.update { it.copy(status = getApplication<Application>().getString(R.string.advanced_need_two_frames)) }
                        return true
                    }
                    advancedSaveJob = viewModelScope.launch {
                        _advancedCameraState.update { it.copy(saving = true) }
                        try {
                            advancedCameraJob?.cancelAndJoin()
                            _advancedCameraState.update { it.copy(saving = true) }
                            val id = SourceTemporalAccumulator(getApplication()).publish(contentRepository.photoProcessor, userPreferences.value.autoSaveAfterCapture)
                            _capturedThumbnail.value?.captureId?.let(::acknowledgeCapturedThumbnail)
                            _imageSavedEvent.emit(id)
                            val exportFailed = GalleryManager.loadMetadata(getApplication(), id)?.customProperties?.get("streamingExportStatus") == "failed"
                            _advancedCameraState.update { it.copy(status = getApplication<Application>().getString(
                                if (exportFailed) R.string.advanced_stream_saved_private else R.string.advanced_saved)) }
                        } catch (e: Exception) {
                            _advancedCameraState.update { it.copy(status = getApplication<Application>().getString(R.string.advanced_failed, e.message ?: "")) }
                        } finally { _advancedCameraState.update { it.copy(saving = false) } }
                    }
                } else if (_advancedCameraState.value.mode in listOf(AdvancedCameraMode.HIGH_DETAIL,
                    AdvancedCameraMode.NIGHT, AdvancedCameraMode.ASTRO, AdvancedCameraMode.STAR_TRAILS)) {
                    advancedFinishRequested.value = true
                    _advancedCameraState.update { it.copy(saving = true,
                        status = getApplication<Application>().getString(R.string.advanced_merging)) }
                } else stopAdvancedCapture()
            }
            return true
        }
        val selected = _selectedAdvancedCapture.value ?: return false
        startAdvancedCapture(selected.mode, selected.frames, selected.gapSeconds, selected.options)
        return true
    }

    val photoProcessor get() = contentRepository.photoProcessor

    private val privateAdvancedSources = java.util.concurrent.ConcurrentHashMap<String, MutableSet<String>>()
    private val closedPrivateAdvancedSessions = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private suspend fun deletePrivateAdvancedSource(session: String, id: String) {
        val context = getApplication<Application>()
        val metadata = GalleryManager.loadMetadata(context, id)
        if (metadata?.captureMode == "advanced_source" && metadata.customProperties["advancedPrivateSource"] == "true" && metadata.customProperties["advancedSequenceId"] == session) GalleryManager.deletePhotoOnly(context, id)
        privateAdvancedSources[session]?.remove(id)
    }
    private fun closePrivateAdvancedSession(session: String) {
        closedPrivateAdvancedSessions.add(session)
        CaptureProcessingQueue.enqueue(emptyList()) {
            withContext(NonCancellable + Dispatchers.IO) { privateAdvancedSources[session]?.toList()?.forEach { deletePrivateAdvancedSource(session, it) } }
        }
    }

    private val guidedGroupSources = java.util.concurrent.ConcurrentHashMap<String, MutableSet<String>>()
    private val closedGuidedGroupSessions = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private suspend fun deleteGuidedGroupSource(sessionId: String, id: String) {
        val context = getApplication<Application>()
        if (GalleryManager.loadMetadata(context, id)?.customProperties?.get("addMeSessionId") == sessionId) {
            GalleryManager.deletePhotoOnly(context, id)
        }
        guidedGroupSources[sessionId]?.remove(id)
    }

    /** Completion also drains sources accepted before this session was closed. */
    fun discardGuidedGroupSession(sessionId: String) {
        closedGuidedGroupSessions.add(sessionId)
        _advancedEngineRequested.value = _selectedAdvancedCapture.value != null ||
            guidedGroupSources.keys.any { !closedGuidedGroupSessions.contains(it) }
        CaptureProcessingQueue.enqueue(emptyList()) {
            withContext(NonCancellable + Dispatchers.IO) {
                guidedGroupSources[sessionId]?.toList()?.forEach { deleteGuidedGroupSource(sessionId, it) }
            }
        }
    }

    suspend fun captureGuidedGroupFrame(sessionId: String): String {
        pauseMotionPriorityForCapture()
        require(sessionId.isNotBlank() && !closedGuidedGroupSessions.contains(sessionId))
        val owner = currentCoroutineContext()[Job]
        val accepted = CompletableDeferred<Long>()
        val saved = CompletableDeferred<String?>()
        val allowed = java.util.concurrent.atomic.AtomicBoolean(true)
        var captureId: Long? = null
        try {
            _advancedEngineRequested.value = true
            check(!(state.value.useRaw && state.value.isRawSupported) || withTimeoutOrNull(15_000L) { currentEnginePreviewPlan.first { it != null && it.engine == userPreferences.value.rawRenderingEngine } } != null) {
                getApplication<Application>().getString(R.string.advanced_engine_unavailable)
            }
            CaptureProcessingQueue.state.first { it.pendingCount < 2 }
            check(cameraController.awaitPhotoCaptureReady(state.value.currentCameraId, singleSource = true) { owner?.isActive == true }) {
                getApplication<Application>().getString(R.string.advanced_camera_not_ready)
            }
            submitPhotoCapture(onAccepted = { id ->
                captureId = id; advancedCaptureResults[id] = saved; accepted.complete(id)
            }, onRejected = { accepted.completeExceptionally(IllegalStateException(getApplication<Application>().getString(R.string.advanced_camera_not_ready))) }, shouldAccept = { allowed.get() && owner?.isActive == true && !closedGuidedGroupSessions.contains(sessionId) && state.value.isPreviewActive && !multipleExposureState.enabled },
                sequenceProperties = mapOf("addMeSessionId" to sessionId))
            check(withTimeoutOrNull(5000L) { accepted.await() } != null) { getApplication<Application>().getString(R.string.advanced_admission_timeout) }
            // Exposure/image delivery already have controller deadlines. A valid accepted
            // source may legitimately take longer to render; do not abandon its terminal result.
            return coroutineScope {
                val terminalMonitor = launch {
                    while (isActive && !saved.isCompleted) {
                        delay(250L)
                        val id = captureId
                        // Camera-thread delivery installs its handoff marker before removing
                        // pending settings and clears it only after queue admission completes.
                        if (id != null && !pendingCaptureSettings.containsKey(id) &&
                            !captureProcessingHandoffs.contains(id) &&
                            CaptureProcessingQueue.state.value.pendingCount == 0 && !saved.isCompleted) {
                            saved.completeExceptionally(IllegalStateException(
                                getApplication<Application>().getString(R.string.group_source_failed)))
                        }
                    }
                }
                try {
                    saved.await() ?: error(getApplication<Application>().getString(R.string.group_source_failed))
                } finally { terminalMonitor.cancel() }
            }
        } finally { allowed.set(false); captureId?.let { advancedCaptureResults.remove(it) } }
    }

    fun recoverAdvancedCapture() {
        if (advancedCameraJob?.isActive == true || advancedSaveJob?.isActive == true) return
        _advancedCameraState.update { it.copy(saving = true) }
        advancedSaveJob = viewModelScope.launch {
            try {
                val accumulator = SourceTemporalAccumulator(getApplication())
                val recovered = accumulator.summary()
                val recoveredPreview = accumulator.render(1024)
                _advancedCameraState.update { it.copy(mode = recovered.mode, frames = recovered.frames,
                    elapsedSeconds = recovered.elapsedSeconds, total = 0, preview = recoveredPreview) }
                val id = accumulator.publish(contentRepository.photoProcessor, userPreferences.value.autoSaveAfterCapture)
                _imageSavedEvent.emit(id)
                _advancedCameraState.update { it.copy(status = getApplication<Application>().getString(R.string.advanced_saved)) }
            } catch (e: Exception) {
                _advancedCameraState.update { it.copy(status = getApplication<Application>().getString(R.string.advanced_failed, e.message ?: "")) }
            } finally { _advancedCameraState.update { it.copy(saving = false) } }
        }
    }

    fun startAdvancedCapture(requestedMode: AdvancedCameraMode, requestedFrames: Int, gapSeconds: Int, options: AdvancedCaptureOptions = AdvancedCaptureOptions()) {
        val initial = state.value
        if (advancedCameraJob?.isCompleted == false || advancedSaveJob?.isActive == true ||
            focusBracketJob?.isCompleted == false || _intervalCaptureState.value.isRunning ||
            initial.captureMode != CaptureMode.PHOTO || !initial.isPreviewActive || initial.isCapturing ||
            initial.countdownValue > 0 || multipleExposureState.enabled || glSurfaceView == null) return
        val thermalStatus = (getApplication<Application>().getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager)?.currentThermalStatus ?: 0
        val smartPlan = if (requestedMode == AdvancedCameraMode.SMART) com.hinnka.mycamera.camera.SmartCaptureAdvisor.choose(
            initial.iso, initial.shutterSpeed, captureCoachHistogram(), initial.isAutoExposure,
            initial.isExposureLocked, thermalStatus, requestedFrames,
            _coachFrameMotion.value.takeIf { SystemClock.elapsedRealtime() - sceneSampleElapsedMs in 0L..1000L }) else null
        val mode = smartPlan?.mode ?: requestedMode
        val currentLut = getLutInfo(currentLutId.value)
        if (currentLut?.isVip == true && !isPurchased.value) { showPaymentDialog = true; return }
        _advancedEngineRequested.value = true
        val preview = mode in listOf(AdvancedCameraMode.PRE_CAPTURE, AdvancedCameraMode.PREVIEW_COMPOSITE, AdvancedCameraMode.PREVIEW_ND)
        val total = (smartPlan?.frames ?: requestedFrames).coerceIn(if (mode == AdvancedCameraMode.HIGH_DETAIL) 6 else 2, 15)
        val signature = advancedSessionFingerprint()
        val exportMergedAfterCapture = userPreferences.value.autoSaveAfterCapture
        val context = getApplication<Application>()
        advancedFinishRequested.value = false
        advancedTemporalCommittedFrames = 0
        _advancedCameraState.value = AdvancedCameraState(mode = mode, running = true, total = if (preview || mode in listOf(AdvancedCameraMode.LIVE_COMPOSITE, AdvancedCameraMode.LIGHT_GRAFFITI, AdvancedCameraMode.LIVE_ND)) 0 else total,
            status = context.getString(if (mode == AdvancedCameraMode.PRE_CAPTURE) R.string.advanced_buffering else if (preview) R.string.advanced_blending else R.string.advanced_capturing))
        advancedCameraJob = viewModelScope.launch {
            var privateSourceSession: String? = null
            val started = SystemClock.elapsedRealtime()
            val elapsedTicker = launch {
                while (isActive) {
                    _advancedCameraState.update { it.copy(elapsedSeconds = ((SystemClock.elapsedRealtime() - started) / 1000L).toInt()) }
                    delay(1000L)
                }
            }
            val sequenceCaptureIds = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()
            val buffer = if (preview) TemporalPreviewBuffer(mode, options.bufferSeconds, options.bufferMemoryMb) else null
            temporalPreviewBuffer = buffer
            try {
                check(!(state.value.useRaw && state.value.isRawSupported) || withTimeoutOrNull(15_000L) { currentEnginePreviewPlan.first { it != null && it.engine == userPreferences.value.rawRenderingEngine } } != null) {
                    context.getString(R.string.advanced_engine_unavailable)
                }
                if (preview) {
                    while (isActive && SystemClock.elapsedRealtime() - started < 300_000L) {
                        check(state.value.isPreviewActive && signature == advancedSessionFingerprint()) { context.getString(R.string.advanced_settings_changed) }
                        val frameStart = SystemClock.elapsedRealtime()
                        val delivered = java.util.concurrent.atomic.AtomicReference<Bitmap?>(null)
                        val deliveryLock = Any()
                        var deliveryClosed = false
                        var input: Bitmap? = null
                        var rotated: Bitmap? = null
                        var offered = false
                        var display: TemporalPreviewBuffer.Snapshot? = null
                        try {
                            val arrived = withTimeoutOrNull(3000L) {
                                suspendCancellableCoroutine<Boolean> { continuation ->
                                    val view = glSurfaceView
                                    val receive: (Bitmap?) -> Unit = { captured ->
                                        synchronized(deliveryLock) {
                                            if (!deliveryClosed && continuation.isActive) {
                                                delivered.set(captured)
                                                continuation.resume(captured != null)
                                            } else captured?.recycle()
                                        }
                                    }
                                    if (view == null) continuation.resume(false)
                                    else if (initial.useRaw && initial.isRawSupported) view.captureEnginePreviewFrame(options.bufferMaxEdge.coerceIn(480, 1536), receive)
                                    else view.captureOriginalPreviewFrame(options.bufferMaxEdge.coerceIn(480, 1536), receive)
                                }
                            } == true
                            synchronized(deliveryLock) {
                                input = delivered.getAndSet(null)
                                deliveryClosed = true
                            }
                            val bitmap = input
                            check(arrived && bitmap != null) { context.getString(R.string.advanced_no_preview) }
                            val elapsed = SystemClock.elapsedRealtime()
                            val wall = System.currentTimeMillis()
                            val rotation = capturePreviewThumbnailRotation()
                            // Assign owned resources inside the worker before returning Unit: prompt
                            // cancellation cannot discard an untracked Bitmap result.
                            withContext(Dispatchers.Default) { rotated = rotatePreviewBitmapForCapture(bitmap, rotation) }
                            val frame = rotated ?: error(context.getString(R.string.advanced_no_preview))
                            check(signature == advancedSessionFingerprint()) { context.getString(R.string.advanced_settings_changed) }
                            var count = 0
                            withContext(Dispatchers.Default) {
                                offered = true
                                count = buffer!!.offer(frame, wall, elapsed)
                                if (mode != AdvancedCameraMode.PRE_CAPTURE && count % 4 == 0) display = buffer.snapshot()
                            }
                            ensureActive()
                            _advancedCameraState.update { it.copy(frames = count,
                                elapsedSeconds = ((elapsed - started) / 1000).toInt(), preview = display?.bitmap ?: it.preview) }
                            // The UI owns displayed snapshots and releases them through GC, avoiding
                            // recycling a bitmap still referenced by a Compose render node.
                            display = null
                        } finally {
                            synchronized(deliveryLock) {
                                deliveryClosed = true
                                delivered.getAndSet(null)?.let { if (!it.isRecycled) it.recycle() }
                            }
                            if (input !== rotated) input?.let { if (!it.isRecycled) it.recycle() }
                            if (!offered) rotated?.let { if (!it.isRecycled) it.recycle() }
                            display?.bitmap?.let { if (!it.isRecycled) it.recycle() }
                        }
                        delay((1000L / options.bufferFps.coerceIn(1, 12) - (SystemClock.elapsedRealtime() - frameStart)).coerceAtLeast(1L))
                    }
                    _advancedCameraState.update { it.copy(status = context.getString(R.string.advanced_stopped)) }
                } else {
                    val ids = mutableListOf<String>()
                    val pendingSavedSources = mutableListOf<CompletableDeferred<String?>>()
                    val sourceTemporal = mode == AdvancedCameraMode.LIVE_COMPOSITE || mode == AdvancedCameraMode.LIGHT_GRAFFITI || mode == AdvancedCameraMode.LIVE_ND
                    val accumulator = if (sourceTemporal) SourceTemporalAccumulator(context).also { it.clear() } else null
                    val sequenceId = UUID.randomUUID().toString()
                    activeAdvancedSequenceId = sequenceId
                    if (mode == AdvancedCameraMode.HIGH_DETAIL) privateSourceSession = sequenceId
                    suspend fun captureSource(index: Int): Boolean {
                        if (advancedFinishRequested.value) return false
                        if (mode == AdvancedCameraMode.ASTRO) check(com.hinnka.mycamera.processor.TripodStabilityGate.qualify(context)) {
                            context.getString(R.string.advanced_astro_stability)
                        }
                        if (advancedFinishRequested.value) return false
                        combine(CaptureProcessingQueue.state, advancedFinishRequested) { queue, finish ->
                            queue.pendingCount < 2 || finish
                        }.first { it }
                        ensureActive()
                        if (advancedFinishRequested.value) return false
                        _advancedCameraState.update { it.copy(status = if (sourceTemporal)
                            context.getString(R.string.advanced_waiting_live_source, index + 1)
                            else context.getString(R.string.advanced_waiting_camera, index + 1, total)) }
                        val cameraReady = cameraController.awaitPhotoCaptureReady(initial.currentCameraId, singleSource = true) { !advancedFinishRequested.value }
                        ensureActive()
                        if (advancedFinishRequested.value) return false
                        check(cameraReady) { context.getString(R.string.advanced_camera_not_ready) }
                        check(state.value.isPreviewActive && signature == advancedSessionFingerprint()) { context.getString(R.string.advanced_settings_changed) }
                        val owner = currentCoroutineContext()[Job]
                        val allowed = java.util.concurrent.atomic.AtomicBoolean(true)
                        val accepted = CompletableDeferred<Long>()
                        val saved = CompletableDeferred<String?>()
                        val exposed = CompletableDeferred<Boolean>()
                        var captureId: Long? = null
                        try {
                            submitPhotoCapture(onAccepted = { id ->
                                captureId = id
                                sequenceCaptureIds.add(id)
                                advancedCaptureResults[id] = saved
                                advancedExposureResults[id] = exposed
                                accepted.complete(id)
                                _advancedCameraState.update { it.copy(status = context.getString(R.string.advanced_exposing_source, index + 1)) }
                                PLog.i(TAG, "Advanced ${mode.name}: accepted frame ${index + 1}, capture=$id")
                            }, onRejected = {
                                // A shutter stop may intentionally withdraw this queued source.
                                // Preserve normal sequence finalization instead of reporting a camera fault.
                                if (!advancedFinishRequested.value && owner?.isActive == true) {
                                    accepted.completeExceptionally(IllegalStateException(context.getString(R.string.advanced_camera_not_ready)))
                                }
                            }, shouldAccept = {
                                allowed.get() && !advancedFinishRequested.value && owner?.isActive == true && state.value.isPreviewActive &&
                                    state.value.currentCameraId == initial.currentCameraId &&
                                    signature == advancedSessionFingerprint() && !multipleExposureState.enabled
                            }, sequenceProperties = mapOf("advancedSequenceId" to sequenceId, "advancedSequenceIndex" to index.toString(), "advancedSequenceMode" to mode.name) + (smartPlan?.let { mapOf("smartCaptureReason" to it.reason) } ?: emptyMap()) + if (mode == AdvancedCameraMode.HIGH_DETAIL) mapOf("advancedPrivateSource" to "true") else emptyMap())
                            val admission = withTimeoutOrNull(5000L) {
                                while (!accepted.isCompleted && !advancedFinishRequested.value) delay(20)
                                if (accepted.isCompleted) accepted.await() else null
                            }
                            if (admission == null && advancedFinishRequested.value) return false
                            check(admission != null) { context.getString(R.string.advanced_admission_timeout) }
                            check(withTimeoutOrNull(180_000L) { exposed.await() } == true) {
                                context.getString(R.string.advanced_exposure_failed)
                            }
                            if (sourceTemporal) {
                                val id = withTimeoutOrNull(180_000L) { saved.await() } ?: error(context.getString(R.string.advanced_source_save_timeout))
                                ids += id
                                _advancedCameraState.update { it.copy(status = context.getString(R.string.advanced_accumulating)) }
                                accumulator!!.offer(id, mode, options.intensity, contentRepository.photoProcessor)
                                advancedTemporalCommittedFrames = ids.size
                                val progress = accumulator.render(1024)
                                _advancedCameraState.update { it.copy(preview = progress) }
                            } else {
                                // Camera completion releases the next physical source. Rendering
                                // remains bounded by the shared processing-queue admission above.
                                pendingSavedSources += saved
                            }
                        } finally {
                            allowed.set(false)
                            captureId?.let { advancedExposureResults.remove(it) }
                            if (sourceTemporal) captureId?.let { advancedCaptureResults.remove(it) }
                        }
                        _advancedCameraState.update { it.copy(elapsedSeconds = ((SystemClock.elapsedRealtime() - started) / 1000).toInt()) }
                        if (sourceTemporal || index + 1 < total) {
                            val gapMillis = if (mode == AdvancedCameraMode.HIGH_DETAIL) 0L else gapSeconds.coerceIn(0, 60) * 1000L
                            if (gapMillis > 0) withTimeoutOrNull(gapMillis) {
                                advancedFinishRequested.first { it }
                            }
                        }
                        return true
                    }
                    if (mode == AdvancedCameraMode.MOTION_HDR) {
                        check(initial.isAutoExposure && !initial.isExposureLocked) { context.getString(R.string.advanced_hdr_requires_ae) }
                        val step = initial.getExposureCompensationStep()
                        check(step.isFinite() && step > 0f) { context.getString(R.string.advanced_hdr_requires_ae) }
                        val range = initial.getExposureCompensationRange()
                        val delta = (options.exposureSpacingEv.coerceIn(.3f, 3f) / step).toInt().coerceAtLeast(1)
                        val targets = List(total) { index ->
                            (initial.exposureCompensation + ((index - (total - 1) / 2f) * delta).toInt()).coerceIn(range.lower, range.upper)
                        }
                        check(targets.distinct().size == targets.size) { context.getString(R.string.advanced_hdr_requires_ae) }
                        check(cameraController.runExposureSequence(initial.currentCameraId, targets, ::captureSource)) {
                            context.getString(R.string.advanced_hdr_requires_ae)
                        }
                    } else if (sourceTemporal) {
                        var index = 0
                        val duration = options.durationSeconds.coerceIn(5, 3600) * 1000L
                        var accumulatedAt: Long? = null
                        while (isActive && !advancedFinishRequested.value &&
                            (index < 2 || SystemClock.elapsedRealtime() - (accumulatedAt ?: SystemClock.elapsedRealtime()) < duration)) {
                            if (!captureSource(index)) break
                            index++
                            if (accumulatedAt == null) accumulatedAt = SystemClock.elapsedRealtime()
                        }
                    } else for (index in 0 until total) {
                        if (advancedFinishRequested.value) break
                        try { captureSource(index) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (error: Exception) {
                            if (pendingSavedSources.size < 2) throw error
                            PLog.w(TAG, "Advanced source failed after ${pendingSavedSources.size} accepted sources; finalizing valid frames: ${error.message}")
                            break
                        }
                    }
                    if (!sourceTemporal) {
                        _advancedCameraState.update { it.copy(saving = true, status = context.getString(R.string.advanced_rendering_sources)) }
                        for (saved in pendingSavedSources) {
                            val completed = withTimeoutOrNull(180_000L) { saved.await() }
                            if (completed != null) ids += completed
                            else PLog.w(TAG, "Advanced source did not save; retaining other completed frames")
                        }
                    }
                    if (mode == AdvancedCameraMode.SOURCE_BURST || (mode == AdvancedCameraMode.MOTION_HDR && !options.fuseExposureBracket)) {
                        check(ids.isNotEmpty()) { context.getString(R.string.advanced_source_save_timeout) }
                        _advancedCameraState.update { it.copy(status = context.getString(R.string.advanced_saved)) }
                        return@launch
                    }
                    if (accumulator != null) {
                        _advancedCameraState.update { it.copy(saving = true) }
                        val output = accumulator.publish(contentRepository.photoProcessor, exportMergedAfterCapture)
                        _capturedThumbnail.value?.captureId?.let(::acknowledgeCapturedThumbnail)
                        _imageSavedEvent.emit(output)
                        val exportFailed = GalleryManager.loadMetadata(context, output)?.customProperties?.get("streamingExportStatus") == "failed"
                        _advancedCameraState.update { it.copy(status = context.getString(
                            if (exportFailed) R.string.advanced_stream_saved_private else R.string.advanced_saved)) }
                        return@launch
                    }
                    val minimumFrames = 2
                    check(ids.size >= minimumFrames) { context.getString(R.string.advanced_minimum_frames, minimumFrames) }
                    _advancedCameraState.update { it.copy(saving = true,
                        status = context.getString(R.string.advanced_merging)) }
                    val operation = when (mode) {
                        AdvancedCameraMode.HIGH_DETAIL -> PhotoSequenceOperation.SUPER_RESOLUTION
                        AdvancedCameraMode.NIGHT -> PhotoSequenceOperation.NIGHT_STACK
                        AdvancedCameraMode.ASTRO -> PhotoSequenceOperation.ASTRO_STACK
                        AdvancedCameraMode.STAR_TRAILS -> PhotoSequenceOperation.STAR_TRAILS
                        else -> PhotoSequenceOperation.MOTION_HDR
                    }
                    val merged = CaptureSequenceMerge.merge(context, ids, operation, contentRepository.photoProcessor,
                        onProgress = { done, count -> _advancedCameraState.update { it.copy(status = context.getString(R.string.advanced_merge_progress, done, count)) } },
                        detailInputMaxEdge = options.detailInputMaxEdge, shouldAutoSave = exportMergedAfterCapture,
                        astroHorizon = if (mode == AdvancedCameraMode.ASTRO) options.astroHorizon else null)
                    _capturedThumbnail.value?.captureId?.let(::acknowledgeCapturedThumbnail)
                    _imageSavedEvent.emit(merged)
                    val reconstruction = GalleryManager.loadMetadata(context, merged)?.customProperties?.get("sequenceReconstructionType")
                    val message = when (reconstruction) { "aligned_native" -> R.string.advanced_detail_saved_native; "reference_native" -> R.string.advanced_detail_saved_reference; else -> R.string.advanced_saved }
                    _advancedCameraState.update { it.copy(status = context.getString(message)) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                PLog.e(TAG, "Advanced capture failed", error)
                _advancedCameraState.update { it.copy(status = context.getString(R.string.advanced_failed, error.message ?: "")) }
            } finally {
                elapsedTicker.cancel()
                privateSourceSession?.let(::closePrivateAdvancedSession)
                sequenceCaptureIds.forEach { advancedExposureResults.remove(it); advancedCaptureResults.remove(it) }
                withContext(NonCancellable) { buffer?.close() }
                if (temporalPreviewBuffer === buffer) temporalPreviewBuffer = null
                activeAdvancedSequenceId = null
                _advancedCameraState.update { it.copy(running = false, saving = advancedSaveJob?.isActive == true) }
            }
        }
    }

    fun saveAdvancedPreview() {
        if (!_advancedCameraState.value.running || advancedSaveJob?.isActive == true) return
        val buffer = temporalPreviewBuffer ?: return
        val mode = _advancedCameraState.value.mode ?: return
        val shutterTime = SystemClock.elapsedRealtime()
        val signature = advancedSessionFingerprint()
        _advancedCameraState.update { it.copy(saving = true) }
        advancedSaveJob = viewModelScope.launch {
            var snapshot: TemporalPreviewBuffer.Snapshot? = null
            try {
                withContext(Dispatchers.Default) { snapshot = buffer.snapshot(shutterTime) }
                val image = snapshot ?: error(getApplication<Application>().getString(R.string.advanced_no_frame))
                check(signature == advancedSessionFingerprint()) { getApplication<Application>().getString(R.string.advanced_settings_changed) }
                advancedCameraJob?.cancelAndJoin()
                check(state.value.isPreviewActive && signature == advancedSessionFingerprint()) { getApplication<Application>().getString(R.string.advanced_settings_changed) }
                val id = savePreviewBitmapCapture(image.bitmap, "preview_" + mode.name.lowercase(), state.value.aspectRatio,
                    sequenceProperties = mapOf("temporalPreview" to "true", "previewSourceElapsedMs" to image.elapsedMs.toString(),
                        "shutterElapsedMs" to shutterTime.toString()), capturedAtMs = image.wallTimeMs)
                check(id != null) { getApplication<Application>().getString(R.string.advanced_save_failed) }
                _advancedCameraState.update { it.copy(status = getApplication<Application>().getString(R.string.advanced_saved)) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                _advancedCameraState.update { it.copy(status = getApplication<Application>().getString(R.string.advanced_failed, error.message ?: "")) }
            } finally {
                snapshot?.bitmap?.let { if (!it.isRecycled) it.recycle() }
                _advancedCameraState.update { it.copy(saving = false) }
            }
        }
    }

    fun stopIntervalCapture() {
        intervalCaptureJob?.cancel()
        intervalCaptureJob = null
        _intervalCaptureState.update { it.copy(isRunning = false) }
    }

    fun startIntervalCapture(intervalSeconds: Int, slots: Int) {
        if (advancedCameraJob?.isCompleted == false || _advancedCameraState.value.saving) return
        if (_intervalCaptureState.value.isRunning || focusBracketJob?.isCompleted == false) return
        val current = state.value
        if (current.captureMode != CaptureMode.PHOTO || !current.isPreviewActive ||
            current.isCapturing || current.countdownValue > 0 || multipleExposureState.enabled ||
            current.videoRecordingState.isProcessing
        ) return
        val currentLut = getLutInfo(currentLutId.value)
        if (currentLut?.isVip == true && !isPurchased.value) {
            showPaymentDialog = true
            return
        }
        val gapMs = intervalSeconds.coerceIn(1, 3600) * 1000L
        val count = slots.coerceIn(2, 999)
        val cameraId = current.currentCameraId
        val generation = ++intervalCaptureGeneration
        _intervalCaptureState.value = IntervalCaptureState(isRunning = true, plannedSlots = count)
        intervalCaptureJob = viewModelScope.launch {
            val startedAt = android.os.SystemClock.elapsedRealtime()
            try {
                for (slot in 0 until count) {
                    val scheduledAt = startedAt + slot * gapMs
                    delay((scheduledAt - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0L))
                    ensureActive()
                    val now = state.value
                    if (now.captureMode != CaptureMode.PHOTO || !now.isPreviewActive ||
                        now.currentCameraId != cameraId || multipleExposureState.enabled
                    ) break
                    val selectedLut = getLutInfo(currentLutId.value)
                    if (selectedLut?.isVip == true && !isPurchased.value) {
                        showPaymentDialog = true
                        break
                    }
                    // Skip busy or expired slots instead of creating a queue of burst requests.
                    if (now.isCapturing || now.countdownValue > 0 ||
                        CaptureProcessingQueue.state.value.pendingCount >= 2 ||
                        now.videoRecordingState.isProcessing ||
                        android.os.SystemClock.elapsedRealtime() >= scheduledAt + gapMs
                    ) {
                        _intervalCaptureState.update {
                            it.copy(completedSlots = slot + 1, skippedSlots = it.skippedSlots + 1)
                        }
                        continue
                    }
                    val slotAllowed = java.util.concurrent.atomic.AtomicBoolean(true)
                    val accepted = CompletableDeferred<Unit>()
                    val admissionLock = Any()
                    var outcome = 0 // pending, accepted, skipped; protected by admissionLock
                    fun markSkippedIfPending() = synchronized(admissionLock) {
                        if (outcome == 0) {
                            outcome = 2
                            _intervalCaptureState.update {
                                if (generation != intervalCaptureGeneration) it else it.copy(
                                    completedSlots = maxOf(it.completedSlots, slot + 1),
                                    skippedSlots = it.skippedSlots + 1,
                                )
                            }
                        }
                    }
                    try {
                        if (userPreferences.value.saveLocation) {
                            val location = locationManager.getCurrentLocation()
                            cameraController.setLocation(location?.latitude, location?.longitude)
                        } else cameraController.setLocation(null, null)
                        val owner = currentCoroutineContext()[Job]
                        submitPhotoCapture(
                            onAccepted = {
                                synchronized(admissionLock) {
                                    if (outcome != 1) {
                                        val wasSkipped = outcome == 2
                                        outcome = 1
                                        _intervalCaptureState.update {
                                            if (generation != intervalCaptureGeneration) it else it.copy(
                                                completedSlots = maxOf(it.completedSlots, slot + 1),
                                                acceptedCaptures = it.acceptedCaptures + 1,
                                                skippedSlots = it.skippedSlots - if (wasSkipped) 1 else 0,
                                            )
                                        }
                                    }
                                    accepted.complete(Unit)
                                }
                            },
                            shouldAccept = {
                                slotAllowed.get() && owner?.isActive == true &&
                                    state.value.currentCameraId == cameraId &&
                                    state.value.isPreviewActive &&
                                    !multipleExposureState.enabled
                            },
                        )
                        withTimeoutOrNull(minOf(gapMs, 2000L)) { accepted.await() }
                        markSkippedIfPending()
                    } finally {
                        // Prevent a camera-thread request delayed past timeout/cancel from firing later.
                        slotAllowed.set(false)
                        markSkippedIfPending()
                    }
                }
            } finally {
                // A cancelled predecessor must not stop a newly started session.
                if (generation == intervalCaptureGeneration) {
                    _intervalCaptureState.update { it.copy(isRunning = false) }
                }
            }
        }
    }

    fun capture(expectedMode: CaptureMode, source: String) {
        if (rawVideoState.value.recording || rawVideoState.value.stopping) {
            cameraController.stopRawVideoRecording()
            return
        }
        val requestedState = state.value
        PLog.i(TAG, "Capture requested: source=$source expectedMode=$expectedMode mode=${requestedState.captureMode} preview=${requestedState.isPreviewActive} recording=${requestedState.videoRecordingState.isRecording} processing=${requestedState.videoRecordingState.isProcessing}")
        if (requestedState.captureMode != expectedMode) {
            PLog.w(TAG, "Capture rejected: shooting mode changed after $source input")
            return
        }
        if (toggleAdvancedCaptureFromShutter()) return
        if (_advancedCameraState.value.saving) return
        if (advancedCameraJob?.isCompleted == false) {
            if (_advancedCameraState.value.mode in listOf(AdvancedCameraMode.PRE_CAPTURE, AdvancedCameraMode.PREVIEW_COMPOSITE, AdvancedCameraMode.PREVIEW_ND)) saveAdvancedPreview()
            else stopAdvancedCapture()
            return
        }
        if (focusBracketJob?.isCompleted == false) { stopFocusBracket(); return }
        if (_intervalCaptureState.value.isRunning) {
            stopIntervalCapture()
            return
        }
        if (state.value.captureMode == CaptureMode.VIDEO) {
            if (state.value.videoRecordingState.isProcessing) {
                return
            }
            if (state.value.videoRecordingState.isRecording) {
                cameraController.stopVideoRecording()
            } else {
                val currentState = state.value
                val orientationOffset = userPreferences.value.cameraOrientationOffsets[
                    currentState.currentCameraId
                ] ?: 0
                cameraController.startVideoRecording(
                    creativeLutConfig = currentLutConfig.takeIf { currentState.lutEnabled },
                    creativeRecipeParams = getMergedRecipeParams(),
                    orientationOffsetDegrees = orientationOffset,
                    enginePlan = if (videoEngineEnabled.value && currentState.videoConfig.logProfile == VideoLogProfile.OFF)
                        _currentEnginePreviewPlan.value else null
                )
            }
            return
        }

        startupPrewarmJob?.takeIf { it.isActive }?.let { job ->
            PLog.d(TAG, "Canceling idle capture prewarm for shutter priority")
            job.cancel()
        }

        if (userPreferences.value.saveLocation) {
            val location = locationManager.getCurrentLocation()
            cameraController.setLocation(location?.latitude, location?.longitude)
        } else {
            cameraController.setLocation(null, null)
        }

        val timerSeconds = state.value.timerSeconds

        // 检查 VIP 权限
        val currentLut = getLutInfo(currentLutId.value)
        if (currentLut?.isVip == true && !isPurchased.value) {
            showPaymentDialog = true
            return
        }

        if (timerSeconds > 0) {
            // 延时拍摄：开始倒计时
            viewModelScope.launch {
                for (i in timerSeconds downTo 1) {
                    cameraController.setCountdownValue(i)
                    delay(1000)
                }
                // 倒计时结束，拍照
                cameraController.setCountdownValue(0)
                submitPhotoCapture()
            }
        } else {
            submitPhotoCapture()
        }
    }

    private fun snapshotCaptureSettings() = CaptureSettingsSnapshot(
        state = state.value,
        preferences = userPreferences.value,
        lutId = currentLutId.value,
        frameId = currentFrameId,
        recipe = getMergedRecipeParams(),
        cameraId = cameraController.getCurrentCameraId(),
        sensorOrientation = cameraController.getSensorOrientation(),
        lensFacing = cameraController.getLensFacing(),
        deviceRotation = OrientationObserver.captureRotationDegrees.toInt(),
        blackBorderCrop = currentRawBlackBorderCrop(),
        portraitMask = capturePortraitMaskSnapshot(),
        multipleExposure = multipleExposureState.enabled,
    )

    private fun submitPhotoCapture(
        onAccepted: ((Long) -> Unit)? = null,
        onRejected: (() -> Unit)? = null,
        shouldAccept: (() -> Boolean)? = null,
        sequenceProperties: Map<String, String> = emptyMap(),
    ) {
        val source = sequenceProperties.containsKey("advancedSequenceId") || sequenceProperties.containsKey("addMeSessionId")
        val snapshot = snapshotCaptureSettings()
        val settings = snapshot.copy(sequenceProperties = sequenceProperties,
            state = if (source) snapshot.state.forSequenceSource() else snapshot.state,
            preferences = if (sequenceProperties.containsKey("addMeSessionId") || sequenceProperties["advancedPrivateSource"] == "true") snapshot.preferences.copy(autoSaveAfterCapture = false) else snapshot.preferences,
            enginePlan = if (source && snapshot.state.useRaw && snapshot.state.isRawSupported) currentEnginePreviewPlan.value else null,
            sourceRawReference = if (source && snapshot.state.useRaw && snapshot.state.isRawSupported)
                OwnedCaptureReference<Bitmap> { if (!it.isRecycled) it.recycle() } else null)
        cameraController.capture(onAccepted = { id ->
            PLog.i(TAG, "Photo pipeline accepted: capture=$id photo=${settings.photoId} source=$source engine=${settings.enginePlan?.engine}")
            settings.captureId = id
            pendingCaptureSettings[id] = settings
            settings.sequenceProperties["addMeSessionId"]?.let { session ->
                guidedGroupSources.computeIfAbsent(session) { java.util.concurrent.ConcurrentHashMap.newKeySet<String>() }.add(settings.photoId)
            }
            if (settings.sequenceProperties["advancedPrivateSource"] == "true") settings.sequenceProperties["advancedSequenceId"]?.let { session -> privateAdvancedSources.computeIfAbsent(session) { java.util.concurrent.ConcurrentHashMap.newKeySet<String>() }.add(settings.photoId) }
            onAccepted?.invoke(id)
            if (!source) capturePhotoThumbnails(settings)
            else if (settings.sourceRawReference != null) captureSequenceRawReference(settings)
            if (settings.state.useLivePhoto) {
                cameraController.setCapturingLivePhoto(true)
                livePhotoIndicatorJob?.cancel()
                livePhotoIndicatorJob = viewModelScope.launch {
                    delay(1500)
                    cameraController.setCapturingLivePhoto(false)
                }
            }
        }, shouldAccept = shouldAccept, singleSource = source, onRejected = {
            settings.sourceRawReference?.close()
            onRejected?.invoke()
        })
    }

    private fun publishCapturedThumbnail(settings: CaptureSettingsSnapshot) {
        if (settings.sequenceProperties.containsKey("addMeSessionId") || settings.sequenceProperties["advancedPrivateSource"] == "true") return
        if (!settings.captureCompleted) return
        _capturedThumbnail.update { current ->
            if (current != null && (current.captureId > settings.captureId ||
                    (current.captureId == settings.captureId && current.finalThumbnailLoaded))
            ) {
                current
            } else {
                CapturedThumbnail(
                    captureId = settings.captureId,
                    photoId = settings.photoId,
                    bitmap = settings.displayThumbnail.takeUnless {
                        settings.processingFinished && settings.savedPhotoId == null && !settings.multipleExposure
                    },
                    savedPhotoId = settings.savedPhotoId,
                    processingFinished = settings.processingFinished,
                )
            }
        }
    }

    private fun markCapturedThumbnailSaved(settings: CaptureSettingsSnapshot, photoId: String) {
        settings.savedPhotoId = photoId
        publishCapturedThumbnail(settings)
    }

    fun acknowledgeCapturedThumbnail(captureId: Long) {
        _capturedThumbnail.update { current ->
            if (current?.captureId == captureId) {
                current.copy(bitmap = null, finalThumbnailLoaded = true)
            } else current
        }
    }

    private suspend fun processCapturedPhoto(photo: CapturedPhoto, settings: CaptureSettingsSnapshot) {
        val first = photo.frames.first()
        val captureInfo = first.captureInfo
        val characteristics = first.characteristics
        val captureResult = first.captureResult
        if (settings.multipleExposure) {
            handleMultipleExposureFrameCaptured(first.image, captureInfo, settings)
        } else if (photo.state.hdrBracketCapturing) {
            processHdrBracket(
                images = photo.frames.map { it.image },
                captureResults = photo.frames.map { it.captureResult },
                zeroEvFrameCount = photo.frames.size - 2,
                expectedFrameCount = photo.frames.size,
                captureInfo = captureInfo,
                characteristics = characteristics,
                captureResult = captureResult,
                settings = settings,
            )
        } else if (photo.state.isMultiFrameEnabled) {
            val burstPlanningStartedAtMs = SystemClock.elapsedRealtime()
            val chronologicalFrames = photo.frames.map {
                PendingRawStackFrame(rawStackFrame(it.image, it.captureResult, it.frameMetadata), it.captureInfo, it.captureResult)
            }.sortedBy { it.frame.sensorTimestampNs }
            val rawMaxMode = photo.state.hdrPlusMergeMode
            val rawMaxHdrFusionEnabled = photo.state.isHdrPlusBracketExposureEnabled
            val exposurePlan = RawmaxExposurePlanner.plan(
                exposureProducts = chronologicalFrames.map { it.frame.exposureProduct },
                frameRoles = chronologicalFrames.map { it.frame.role },
                enableHdrFusion = rawMaxHdrFusionEnabled,
            )
            val normalFrames = exposurePlan.normalIndices.map(chronologicalFrames::get)
            val auxiliaryIndices = buildList {
                exposurePlan.shortIndex?.let(::add)
                addAll(exposurePlan.longIndices.toList())
            }
            val auxiliaryFrames = auxiliaryIndices.map(chronologicalFrames::get)
            exposurePlan.excludedIndices.forEach { excludedIndex ->
                chronologicalFrames[excludedIndex].frame.image.close()
            }
            val isRawStack = normalFrames.firstOrNull()?.frame?.image?.format
                ?.let(::isRawCaptureFormat) == true
            // Geometry is planned by the Radiance stacker's coarse temporal-flow graph.
            // Running a second exhaustive RAW proxy registration here blocks the
            // post-capture path on CPU and duplicates the GLES registration work.
            val orderedNormalFrames = normalFrames
            val orderedFrames = orderedNormalFrames + auxiliaryFrames
            val referencePlanLog = if (isRawStack) {
                "deferred_mgc_gles, reference=pending, "
            } else {
                "chronological, reference=0, "
            }
            PLog.i(
                TAG,
                "RAW burst plan: referenceSource=$referencePlanLog" +
                    "accepted=${orderedFrames.size}, " +
                    "normal=${orderedNormalFrames.size}, " +
                    "short=${if (exposurePlan.shortIndex != null) 1 else 0}, " +
                    "long=${exposurePlan.longIndices.size}, " +
                    "exposureRejected=${exposurePlan.excludedIndices.joinToString()}, " +
                    "geometry=GLES_TEMPORAL_GRAPH, " +
                    "costMs=${SystemClock.elapsedRealtime() - burstPlanningStartedAtMs}",
            )
            val processingFrames = if (isRawStack) {
                orderedFrames
            } else {
                auxiliaryFrames.forEach { it.frame.image.close() }
                if (auxiliaryFrames.isNotEmpty()) {
                    PLog.i(
                        TAG,
                        "Excluded ${auxiliaryFrames.size} short/auxiliary frames from " +
                            "non-RAW multi-frame accumulation",
                    )
                }
                orderedNormalFrames
            }
            val framesToProcess = processingFrames.map { it.frame }
            val referenceCaptureInfo = orderedNormalFrames.firstOrNull()?.captureInfo
                ?: captureInfo
            val referenceCaptureResult = orderedNormalFrames.firstOrNull()?.captureResult
                ?: captureResult
            processStacking(
                frames = framesToProcess,
                captureInfo = referenceCaptureInfo,
                characteristics = characteristics,
                captureResult = referenceCaptureResult,
                rawMaxHdrFusionEnabled = rawMaxHdrFusionEnabled,
                rawMaxMode = rawMaxMode,
                capturePortraitMask = settings.portraitMask,
                settings = settings,
            )
        } else {
            saveImage(first.image, captureInfo, characteristics, captureResult, settings.portraitMask, settings)
        }
    }

    fun captureVideoFrame() {
        val currentState = state.value
        if (currentState.captureMode != CaptureMode.VIDEO || !currentState.videoRecordingState.isRecording) {
            return
        }

        if (userPreferences.value.saveLocation) {
            val location = locationManager.getCurrentLocation()
            cameraController.setLocation(location?.latitude, location?.longitude)
        } else {
            cameraController.setLocation(null, null)
        }

        if (isShutterSoundEnabled) {
            shutterSoundPlayer.play()
        }
        if (isVibrationEnabled) {
            vibrationHelper.vibrate()
        }

        glSurfaceView?.capturePreviewFrame { bitmap ->
            viewModelScope.launch {
                saveVideoSnapshot(bitmap)
            }
        } ?: PLog.w(TAG, "captureVideoFrame skipped: glSurfaceView unavailable")
    }

    /**
     * 开始连拍
     */
    fun startContinuousCapture() {
        if (_intervalCaptureState.value.isRunning || focusBracketJob?.isCompleted == false) return
        if (state.value.useRaw && state.value.isRawSupported) return
        val settings = snapshotCaptureSettings()
        cameraController.startBurstCapture {
            burstImageCount = 0
            burstPhotoId = UUID.randomUUID().toString()
            burstSettings = settings
            capturePhotoThumbnails(settings)
            if (isShutterSoundEnabled) {
                shutterSoundPlayer.playBurst()
            }
        }
    }

    /**
     * 停止连拍
     */
    fun stopContinuousCapture() {
        if (state.value.useRaw && state.value.isRawSupported) return
        cameraController.stopBurstCapture()
        shutterSoundPlayer.stopBurst()
        viewModelScope.launch {
            _imageSavedEvent.emit(burstPhotoId)
        }
    }

    /**
     * 切换摄像头（前后置切换）
     */
    fun switchCamera() {
        cameraController.switchCamera()
        reopenCamera(
            preserveVideoRecording = true
        )
        zoomRatioByMain = 1f
    }

    /**
     * 切换到指定的镜头类型
     */
    fun switchToLens(cameraId: String) {
        if (isVideoLensLocked()) return
        val targetCamera = state.value.availableCameras.find { it.cameraId == cameraId }
        syncVendorCaptureSettingsToController()
        cameraController.switchToCameraId(cameraId)
        targetCamera?.let { camera ->
            setZoomRatioForCamera(camera.defaultVisibleZoomRatio(), camera.cameraId)
        }
        reopenCamera(
            preserveVideoRecording = true
        )
    }

    fun switchToLensAndSetZoomRatio(cameraId: String, ratio: Float) {
        if (isVideoLensLocked()) {
            setZoomRatio(ratio)
            return
        }
        syncVendorCaptureSettingsToController()
        cameraController.switchToCameraId(cameraId)
        setZoomRatioForCamera(ratio, cameraId)
        reopenCamera(
            preserveVideoRecording = true
        )
    }

    /**
     * 重新打开相机（切换摄像头后使用）
     */
    private fun reopenCamera(
        preserveVideoRecording: Boolean = false
    ) {
        val texture = currentSurfaceTexture
        if (texture == null) {
            cameraOpenInFlight = false
            return
        }
        requestCameraOpen(texture, preserveVideoRecording)
    }

    /**
     * 获取所有后置摄像头
     */
    fun getBackCameras(): List<CameraInfo> {
        return cameraController.getBackCameras()
    }

    /**
     * 设置曝光补偿
     */
    fun setExposureCompensation(value: Int) {
        updateUserCaptureSettings { setExposureCompensation(value) }
    }

    /**
     * 设置 ISO
     */
    fun setIso(value: Int) {
        manualExposureOverride()
        updateUserCaptureSettings { setIso(value) }
    }

    /**
     * 设置快门速度
     */
    fun setShutterSpeed(value: Long) {
        manualExposureOverride()
        updateUserCaptureSettings { setShutterSpeed(value) }
    }

    /**
     * 设置计算光圈 (等效虚化)
     */
    fun setAperture(value: Float) {
        cameraController.setAperture(value)
    }

    /**
     * 设置是否开启虚拟光圈 (等效虚化控制)
     */
    /** Uses the ordinary photo pipeline and the selected rendering recipe. */
    fun setPortraitEnabled(enabled: Boolean) {
        if (state.value.captureMode != CaptureMode.PHOTO || state.value.isCapturing) return
        if (enabled) cameraController.setAperture(2.8f)
        cameraController.setVirtualApertureEnabled(enabled)
    }

    fun setVirtualApertureAuto(enabled: Boolean) {
        cameraController.setVirtualApertureEnabled(enabled)
    }

    fun setAutoFocus(auto: Boolean) {
        updateUserCaptureSettings { setAutoFocus(auto) }
    }

    fun setFocusDistance(distance: Float) {
        updateUserCaptureSettings {
            if (state.value.isAutoFocus) setAutoFocus(false)
            setFocusDistance(distance)
        }
    }

    fun setSnapFocusDistance(preset: SnapFocusDistance) {
        updateUserCaptureSettings { setSnapFocusDistance(preset) }
    }

    fun setHyperfocalFocusEnabled(enabled: Boolean) {
        updateUserCaptureSettings { setHyperfocalFocusEnabled(enabled) }
    }

    private fun afterFocusBracket(action: () -> Unit) {
        val active = focusBracketJob
        if (active != null && !active.isCompleted) {
            stopFocusBracket()
            viewModelScope.launch { active.join(); action() }
        } else action()
    }

    private fun updateUserCaptureSettings(update: Camera2Controller.() -> Unit) {
        val active = advancedCameraJob
        if (active?.isCompleted == false) {
            stopAdvancedCapture()
            viewModelScope.launch { active.join(); updateUserCaptureSettings(update) }
            return
        }
        afterFocusBracket {
            cameraController.updateUserCaptureSettings(update) { settings ->
                viewModelScope.launch {
                    userPreferencesRepository.saveCustomCaptureSettings(settings)
                }
            }
        }
    }

    fun setRetainCaptureSettings(enabled: Boolean) {
        cameraController.setCaptureSettingsRetention(enabled) { settings ->
            viewModelScope.launch {
                userPreferencesRepository.saveCaptureSettingsRetention(enabled, settings)
            }
        }
    }

    /**
     * 设置变焦倍数
     */
    fun setZoomRatio(ratio: Float) {
        zoomRatioByMain = ratio
        val cameraInfo = state.value.getCurrentCameraInfo()
        setCameraControllerZoomRatio(ratio, cameraInfo)
    }

    private fun setZoomRatioForCamera(ratio: Float, cameraId: String) {
        zoomRatioByMain = ratio
        val cameraInfo = state.value.availableCameras.find { it.cameraId == cameraId }
        setCameraControllerZoomRatio(ratio, cameraInfo)
    }

    private fun setCameraControllerZoomRatio(ratio: Float, cameraInfo: CameraInfo?) {
        val displayIntrinsicZoomRatio = cameraInfo?.displayIntrinsicZoomRatio?.takeIf { it > 0f } ?: 1.0f
        cameraController.setZoomRatio(ratio / displayIntrinsicZoomRatio)
    }

    private fun CameraInfo.defaultVisibleZoomRatio(): Float {
        return displayIntrinsicZoomRatio.takeIf { it > 0f }
            ?: intrinsicZoomRatio.takeIf { it > 0f }
            ?: 1f
    }

    private fun syncVendorCaptureSettingsToController() {
        val settings = vendorCaptureSettingsByLens.value
        if (cameraController.state.value.vendorCaptureSettingsByLens != settings) {
            cameraController.setVendorCaptureSettingsByLens(settings)
        }
        val customSettings = customVendorKeySettings.value
        if (cameraController.state.value.customVendorKeySettings != customSettings) {
            cameraController.setCustomVendorKeySettings(customSettings)
        }
    }

    /**
     * 设置画面比例
     */
    fun setAspectRatio(ratio: AspectRatio) {
        viewModelScope.launch {
            applyCameraFeatureUpdate(
                CameraFeatureUpdate(aspectRatio = SettingValue(ratio))
            )
        }
    }

    fun setTopSheetAspectRatios(ratios: List<AspectRatio>) {
        val sanitizedRatios = AspectRatio.sanitizeTopSheetRatios(ratios)
        if (state.value.aspectRatio !in sanitizedRatios) {
            setAspectRatio(sanitizedRatios.first())
        }
        viewModelScope.launch {
            userPreferencesRepository.saveTopSheetAspectRatios(sanitizedRatios)
        }
    }

    fun addCustomAspectRatio(widthRatio: Int, heightRatio: Int) {
        val ratio = AspectRatio.custom(widthRatio, heightRatio)
        val customRatios = AspectRatio.sanitizeCustomRatios(customAspectRatios.value + ratio)
        viewModelScope.launch {
            userPreferencesRepository.saveCustomAspectRatios(customRatios)
            val selectedRatios = AspectRatio.sanitizeTopSheetRatios(topSheetAspectRatios.value + ratio)
            userPreferencesRepository.saveTopSheetAspectRatios(selectedRatios)
        }
    }

    fun deleteCustomAspectRatio(ratio: AspectRatio) {
        val customRatios = customAspectRatios.value.filterNot { it.name == ratio.name }
        val selectedRatios = topSheetAspectRatios.value.filterNot { it.name == ratio.name }
        if (state.value.aspectRatio.name == ratio.name) {
            setAspectRatio(AspectRatio.RATIO_4_3)
        }
        viewModelScope.launch {
            userPreferencesRepository.saveCustomAspectRatios(customRatios)
            userPreferencesRepository.saveTopSheetAspectRatios(selectedRatios)
        }
    }

    fun setCaptureMode(mode: CaptureMode) {
        setShootingMode(mode, useRaw = null)
    }

    /**
     * Applies the three-way shooting mode selection as one ordered operation.
     *
     * RAW is updated before leaving video mode so the public camera state never passes through
     * the other photo mode (VIDEO -> PROFESSIONAL -> PHOTO, for example). The capture-mode
     * change then owns the single camera restart required by the operation.
     */
    fun setShootingMode(mode: CaptureMode, useRaw: Boolean?) {
        stopAdvancedCapture()
        stopIntervalCapture()
        stopFocusBracket()
        if (state.value.videoRecordingState.isRecording ||
            state.value.videoRecordingState.isProcessing
        ) {
            return
        }

        shootingModeSwitchJob?.cancel()
        val rawNeedsUpdate = useRaw != null &&
            (state.value.useRaw != useRaw || this.useRaw.value != useRaw)
        if (state.value.captureMode == mode && !rawNeedsUpdate) return

        shootingModeSwitchJob = viewModelScope.launch {
            applyCameraFeatureUpdate(
                CameraFeatureUpdate(
                    captureMode = SettingValue(mode),
                    useRaw = useRaw?.let(::SettingValue),
                )
            )
        }
    }

    fun setVideoResolution(resolution: VideoResolutionPreset) {
        cameraController.setVideoResolution(resolution)
        val resolvedConfig = state.value.videoConfig
        reopenCamera()
        viewModelScope.launch {
            userPreferencesRepository.saveVideoStabilizationConfig(
                mode = resolvedConfig.stabilizationMode,
                resolution = resolvedConfig.resolution,
                fps = resolvedConfig.fps,
            )
        }
    }

    fun setVideoFps(fps: VideoFpsPreset) {
        cameraController.setVideoFps(fps)
        val resolvedConfig = state.value.videoConfig
        reopenCamera()
        viewModelScope.launch {
            userPreferencesRepository.saveVideoStabilizationConfig(
                mode = resolvedConfig.stabilizationMode,
                resolution = resolvedConfig.resolution,
                fps = resolvedConfig.fps,
            )
        }
    }

    fun setVideoAspectRatio(aspectRatio: VideoAspectRatio) {
        cameraController.setVideoAspectRatio(aspectRatio)
        reopenCamera()
        viewModelScope.launch {
            userPreferencesRepository.saveVideoAspectRatio(aspectRatio)
        }
    }

    fun setVideoStabilizationMode(mode: com.hinnka.mycamera.video.VideoStabilizationMode) {
        val previousConfig = state.value.videoConfig
        cameraController.setVideoStabilizationMode(mode)
        val resolvedConfig = state.value.videoConfig
        if (state.value.captureMode == CaptureMode.VIDEO &&
            (previousConfig.resolution != resolvedConfig.resolution ||
                previousConfig.fps != resolvedConfig.fps)
        ) {
            reopenCamera()
        }
        viewModelScope.launch {
            userPreferencesRepository.saveVideoStabilizationConfig(
                mode = resolvedConfig.stabilizationMode,
                resolution = resolvedConfig.resolution,
                fps = resolvedConfig.fps,
            )
        }
    }

    fun setPhotoPreviewStabilizationEnabled(enabled: Boolean) {
        cameraController.setPhotoPreviewStabilizationEnabled(enabled)
        val resolvedEnabled = state.value.photoPreviewStabilizationEnabled
        viewModelScope.launch {
            userPreferencesRepository.savePhotoPreviewStabilizationEnabled(resolvedEnabled)
        }
    }

    fun setOppoSuperStabilizationEnabled(enabled: Boolean) {
        cameraController.setOppoSuperStabilizationEnabled(enabled)
        viewModelScope.launch {
            userPreferencesRepository.saveOppoSuperStabilizationEnabled(enabled)
        }
    }

    fun setVideoEnhancedStabilizationStrength(strength: Float) {
        cameraController.setVideoEnhancedStabilizationStrength(strength)
        viewModelScope.launch {
            userPreferencesRepository.saveVideoEnhancedStabilizationStrength(strength)
        }
    }

    fun setVideoEnhancedStabilizationLookahead(lookahead: Int) {
        cameraController.setVideoEnhancedStabilizationLookahead(lookahead)
        viewModelScope.launch {
            userPreferencesRepository.saveVideoEnhancedStabilizationLookahead(lookahead)
        }
    }

    fun setExternalLensStabilizationConfig(config: ExternalLensStabilizationConfig) {
        val normalized = config.normalized()
        cameraController.setExternalLensStabilizationConfig(normalized)
        viewModelScope.launch {
            userPreferencesRepository.saveExternalLensStabilizationConfig(normalized)
        }
    }

    fun setVideoLogProfile(logProfile: VideoLogProfile) {
        cameraController.setVideoLogProfile(logProfile)
        reopenCamera()
        viewModelScope.launch {
            userPreferencesRepository.saveVideoLogProfile(logProfile)
        }
        validateAndCancelNonMatchingVideoLut(logProfile, currentLutConfig)
    }

    fun setVideoLogLutMode(mode: VideoLogLutMode) {
        if (state.value.videoRecordingState.isRecording || state.value.videoRecordingState.isProcessing) return
        cameraController.setVideoLogLutMode(mode)
        viewModelScope.launch { userPreferencesRepository.saveVideoLogLutMode(mode) }
    }

    /**
     * 验证并取消选择非匹配的视频 LUT
     * 如果当前处于视频模式且启用了 Log，若当前选中的 LUT 与 Log 格式不匹配，则取消该 LUT
     */
    private fun validateAndCancelNonMatchingVideoLut(logProfile: VideoLogProfile, lutConfig: LutConfig?) {
        if (logProfile != VideoLogProfile.OFF && lutConfig != null) {
            if (!logProfile.matchesLut(lutConfig.curve, lutConfig.colorSpace)) {
                PLog.d(TAG, "Cancelling selected LUT [${lutConfig.title}] because it does not match video log profile [${logProfile.name}] colorSpace/curve")
                setLut(null, persist = false)
            }
        }
    }

    fun setVideoBitrate(bitrate: VideoBitratePreset) {
        cameraController.setVideoBitrate(bitrate)
        reopenCamera()
        viewModelScope.launch {
            userPreferencesRepository.saveVideoBitrate(bitrate)
        }
    }

    fun setVideoShutterAngle(angle: Float) {
        val current=state.value
        if(current.captureMode != CaptureMode.VIDEO || current.videoRecordingState.isRecording ||
            current.videoRecordingState.isProcessing || !motionPrioritySupported || !angle.isFinite()) return
        val frameNs=1_000_000_000L/current.videoConfig.fps.fps.coerceAtLeast(1)
        val range=current.getManualShutterSpeedRange()
        val maximum=minOf(frameNs,range.upper)
        if(maximum<range.lower) return
        val exposure=(frameNs*(angle.coerceIn(1.1f,360f)/360.0)).toLong().coerceIn(range.lower,maximum)
        setShutterSpeedAuto(false)
        setShutterSpeed(exposure)
    }

    fun setVideoNoiseSuppression(enabled: Boolean) {
        if (state.value.videoRecordingState.isRecording || state.value.videoRecordingState.isProcessing) return
        cameraController.setVideoNoiseSuppression(enabled)
        viewModelScope.launch { userPreferencesRepository.saveVideoNoiseSuppression(enabled) }
    }

    fun setVideoWindReduction(level: com.hinnka.mycamera.video.VideoWindReduction) {
        if (state.value.videoRecordingState.isRecording || state.value.videoRecordingState.isProcessing) return
        cameraController.setVideoWindReduction(level)
        viewModelScope.launch { userPreferencesRepository.saveVideoWindReduction(level) }
    }

    fun setVideoAudioInputId(audioInputId: String) {
        cameraController.setVideoAudioInputId(audioInputId)
        viewModelScope.launch {
            userPreferencesRepository.saveVideoAudioInputId(audioInputId)
        }
    }

    fun setVideoRecordingPath(recordingPath: VideoRecordingPath, treeUri: String? = null) {
        cameraController.setVideoRecordingPath(recordingPath, treeUri)
        viewModelScope.launch {
            userPreferencesRepository.saveVideoRecordingPath(recordingPath, treeUri)
        }
    }

    fun setPhotoSavePath(savePath: PhotoSavePath, treeUri: String? = null) {
        viewModelScope.launch {
            userPreferencesRepository.savePhotoSavePath(savePath, treeUri)
        }
    }

    fun setVideoTorchEnabled(enabled: Boolean) {
        cameraController.setVideoTorchEnabled(enabled)
        viewModelScope.launch {
            userPreferencesRepository.saveVideoTorchEnabled(enabled)
        }
    }

    fun setVideoLensLockEnabled(enabled: Boolean) {
        cameraController.setVideoLensLockEnabled(enabled)
        viewModelScope.launch {
            userPreferencesRepository.saveVideoLensLockEnabled(enabled)
        }
    }

    fun setVideoWhiteBalanceLockEnabled(enabled: Boolean) {
        cameraController.setVideoWhiteBalanceLockEnabled(enabled)
        viewModelScope.launch {
            userPreferencesRepository.saveVideoWhiteBalanceLockEnabled(enabled)
        }
    }

    /**
     * 点击对焦
     */
    fun focusOnPoint(x: Float, y: Float, viewWidth: Int, viewHeight: Int) {
        afterFocusBracket { cameraController.focusOnPoint(x, y, viewWidth, viewHeight) }
    }

    fun moveMeteringPoint(x: Float, y: Float, viewAspectRatio: Float) {
        afterFocusBracket { cameraController.moveMeteringPoint(x, y, viewAspectRatio) }
    }

    fun moveFocusPoint(x: Float, y: Float, viewAspectRatio: Float, finished: Boolean) {
        afterFocusBracket { cameraController.moveFocusPoint(x, y, viewAspectRatio, finished) }
    }

    fun mergeMeteringPoint() {
        afterFocusBracket { cameraController.mergeMeteringPoint() }
    }

    fun lockFocusOnPoint(x: Float, y: Float, viewWidth: Int, viewHeight: Int) {
        afterFocusBracket { cameraController.lockFocusOnPoint(x, y, viewWidth, viewHeight) }
    }

    /** AF lock preserves the separately positioned AE region. */
    fun setFocusLocked(locked: Boolean) {
        afterFocusBracket {
            if (!locked) cameraController.unlockFocusOnly()
            else {
                val current = state.value
                if (current.supportsPointAutoFocus && current.isAutoFocus) {
                    val point = current.focusPoint ?: (0.5f to 0.5f)
                    cameraController.focusOnNormalizedPoint(point.first, point.second,
                        com.hinnka.mycamera.camera.FocusPointSource.MANUAL,
                        lockFocus = true, preserveMeteringPoint = true)
                }
            }
        }
    }

    fun unlockFocus() {
        afterFocusBracket { cameraController.unlockFocus() }
    }

    fun toggleFlash() {
        cameraController.setFlashMode(
            when (state.value.flashMode) {
                0 -> 1
                1 -> 2
                2 -> 0
                else -> 0
            }
        )
    }

    /**
     * 设置曝光自动模式
     */
    fun setAutoExposure(enabled: Boolean) {
        manualExposureOverride()
        updateUserCaptureSettings { setAutoExposure(enabled) }
    }

    /**
     * 设置 ISO 自动模式
     */
    fun setIsoAuto(enabled: Boolean) {
        manualExposureOverride()
        updateUserCaptureSettings { setIsoAuto(enabled) }
    }

    /**
     * 设置快门自动模式
     */
    fun setShutterSpeedAuto(enabled: Boolean) {
        manualExposureOverride()
        updateUserCaptureSettings { setShutterSpeedAuto(enabled) }
    }

    /**
     * 设置白平衡模式
     */
    fun setAwbMode(mode: Int) {
        updateUserCaptureSettings { setAwbMode(mode) }
    }

    /**
     * 设置白平衡色温
     */
    fun setAwbTemperature(kelvin: Int) {
        updateUserCaptureSettings { setAwbTemperature(kelvin) }
    }

    fun setMeteringMode(mode: MeteringMode) {
        cameraController.setMeteringMode(mode)
        viewModelScope.launch {
            userPreferencesRepository.saveMeteringMode(mode)
        }
    }

    // ==================== 计费相关方法 ====================

    /**
     * 发起购买
     */
    fun purchase(activity: android.app.Activity) {
        billingManager.purchase(activity)
    }

    // ==================== 自定义导入相关方法 ====================

    /**
     * 获取自定义导入管理器
     */
    fun getCustomImportManager() = contentRepository.getCustomImportManager()

    /**
     * 刷新自定义内容（在导入新的LUT或边框后调用）
     * StateFlow 会自动通知订阅者更新
     */
    fun refreshCustomContent() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // 重新初始化内容仓库
                // StateFlow 会自动更新 availableLutList 和 availableFrameList
                contentRepository.refreshCustomContent()
            }
            PLog.d(TAG, "Custom content refreshed via ContentRepository")
        }
    }

    /**
     * 复制 LUT
     */
    fun copyLut(lut: LutInfo, copyName: String) {
        viewModelScope.launch {
            val newLutId = withContext(Dispatchers.IO) {
                contentRepository.getCustomImportManager().copyLut(lut, copyName)
            }
            if (newLutId != null) {
                withContext(Dispatchers.IO) {
                    // 同时复制色彩配方
                    val params = contentRepository.lutManager.loadColorRecipeParams(lut.id)
                    contentRepository.lutManager.saveColorRecipeParams(newLutId, params)

                    // 更新排序顺序：放在原版下面
                    val currentOrder = userPreferencesRepository.userPreferences.first().filterOrder.toMutableList()
                    if (currentOrder.isEmpty()) {
                        // 如果当前没有排序，则从当前列表初始化并插入
                        val allIds = availableLutList.map { it.id }.toMutableList()
                        val index = allIds.indexOf(lut.id)
                        if (index != -1) {
                            allIds.add(index + 1, newLutId)
                        } else {
                            allIds.add(newLutId)
                        }
                        userPreferencesRepository.saveFilterOrder(allIds)
                    } else {
                        val index = currentOrder.indexOf(lut.id)
                        if (index != -1) {
                            currentOrder.add(index + 1, newLutId)
                        } else {
                            currentOrder.add(newLutId)
                        }
                        userPreferencesRepository.saveFilterOrder(currentOrder)
                    }

                    // 刷新列表
                    contentRepository.refreshCustomContent()
                }
            }
        }
    }

    /**
     * 获取滤镜排序顺序
     */
    val filterOrder: Flow<List<String>> = userPreferencesRepository.userPreferences.map { it.filterOrder }

    /**
     * 获取边框排序顺序
     */
    val frameOrder: Flow<List<String>> = userPreferencesRepository.userPreferences.map { it.frameOrder }

    /**
     * 获取分类排序顺序
     */
    val categoryOrder: Flow<List<String>> = userPreferencesRepository.userPreferences.map { it.categoryOrder }

    private val _lutSelectorMode = MutableStateFlow(LutSelectorMode.Style)
    val lutSelectorMode: StateFlow<LutSelectorMode> = _lutSelectorMode.asStateFlow()
    private var lutSelectorModeSelectionGeneration = 0

    init {
        val initializationGeneration = lutSelectorModeSelectionGeneration
        viewModelScope.launch {
            val persistedMode = userPreferencesRepository.userPreferences.first().lutSelectorMode
            if (lutSelectorModeSelectionGeneration == initializationGeneration) {
                _lutSelectorMode.value = persistedMode
            }
        }
    }

    /**
     * 保存滤镜排序顺序
     */
    fun saveFilterOrder(order: List<String>) {
        viewModelScope.launch {
            userPreferencesRepository.saveFilterOrder(order)
        }
    }

    /**
     * 保存边框排序顺序
     */
    fun saveFrameOrder(order: List<String>) {
        viewModelScope.launch {
            userPreferencesRepository.saveFrameOrder(order)
        }
    }

    /**
     * 保存分类排序顺序
     */
    fun saveCategoryOrder(order: List<String>) =
        viewModelScope.launch {
            userPreferencesRepository.saveCategoryOrder(order)
        }

    suspend fun retainLutCategories(categories: List<String>) {
        userPreferencesRepository.retainLutCategories(categories)
    }

    fun setLutSelectorMode(mode: LutSelectorMode) {
        if (_lutSelectorMode.value == mode) return
        lutSelectorModeSelectionGeneration++
        _lutSelectorMode.value = mode
        viewModelScope.launch {
            userPreferencesRepository.saveLutSelectorMode(mode)
        }
    }

    // ==================== LUT 相关方法 ====================

    /**
     * 设置当前 LUT
     */
    fun setLut(lutId: String?, persist: Boolean = true) {
        val normalizedLutId = lutId ?: "none"
        val profile = state.value.videoConfig.logProfile
        if (state.value.captureMode == CaptureMode.VIDEO && profile.isEnabled && normalizedLutId != "none") {
            val info = availableLutList.firstOrNull { it.id == normalizedLutId }
            if (info != null && !profile.matchesLut(info.inputCurve, info.inputColorSpace)) {
                if (!persist) applyLut("none")
                return
            }
        }
        applyLut(normalizedLutId)

        if (persist) {
            val useVideoSlot = state.value.captureMode == CaptureMode.VIDEO &&
                userPreferences.value.separateVideoLutEnabled
            viewModelScope.launch {
                if (useVideoSlot) {
                    userPreferencesRepository.saveVideoLutConfig(normalizedLutId)
                } else {
                    userPreferencesRepository.saveLutConfig(normalizedLutId)
                }
            }
        }
    }

    private val photoStylePreferences = com.hinnka.mycamera.data.PhotoStylePreferences(application)
    private val _whiteBalanceIntent = MutableStateFlow(photoStylePreferences.loadWhiteBalanceIntent())
    val whiteBalanceIntent = _whiteBalanceIntent.asStateFlow()
    init { cameraController.setWhiteBalanceIntent(_whiteBalanceIntent.value) }

    fun setWhiteBalanceIntent(value: com.hinnka.mycamera.model.WhiteBalanceIntent) {
        _whiteBalanceIntent.value = value
        photoStylePreferences.saveWhiteBalanceIntent(value)
        cameraController.setWhiteBalanceIntent(value)
    }

    fun setExposureLocked(locked: Boolean) = cameraController.setExposureLocked(locked)

    fun setPhotographicStyle(value: com.hinnka.mycamera.model.PhotographicStyle) {
        val recipe = value.applyTo(currentRecipeParams.value)
        val lutId = currentLutId.value
        photoStylePreferences.save(value)
        viewModelScope.launch {
            contentRepository.lutManager.saveColorRecipeParams(lutId, recipe)
        }
    }

    private var lutIntensitySaveJob: Job? = null

    fun updateLutIntensity(intensity: Float) {
        val lutId = currentLutId.value
        if (lutId == "none") return
        val current = currentRecipeParams.value
        val updated = current.copy(lutIntensity = RecipeParam.LUT_INTENSITY.clamp(intensity))
        lutIntensitySaveJob?.cancel()
        lutIntensitySaveJob = viewModelScope.launch {
            delay(200)
            contentRepository.lutManager.saveColorRecipeParams(lutId, updated)
        }
    }

    fun setPhotoLut(lutId: String?) {
        val normalizedLutId = lutId ?: "none"
        val shouldApply = state.value.captureMode != CaptureMode.VIDEO ||
            !userPreferences.value.separateVideoLutEnabled
        if (shouldApply) {
            applyLut(normalizedLutId)
        }
        viewModelScope.launch {
            userPreferencesRepository.saveLutConfig(normalizedLutId)
        }
    }

    fun setVideoLut(lutId: String?) {
        val normalizedLutId = lutId ?: "none"
        if (state.value.captureMode == CaptureMode.VIDEO &&
            userPreferences.value.separateVideoLutEnabled
        ) {
            applyLut(normalizedLutId)
        }
        viewModelScope.launch {
            userPreferencesRepository.saveVideoLutConfig(normalizedLutId)
        }
    }

    fun setSeparateVideoLutEnabled(enabled: Boolean) {
        if (userPreferences.value.separateVideoLutEnabled == enabled) return
        viewModelScope.launch {
            val currentPreferences = userPreferencesRepository.userPreferences.first()
            val initialVideoLutId = currentPreferences.videoLutId
                ?: currentPreferences.lutId
                ?: currentLutId.value
            userPreferencesRepository.saveSeparateVideoLutEnabled(enabled, initialVideoLutId)
            if (state.value.captureMode == CaptureMode.VIDEO) {
                val updatedPreferences = currentPreferences.copy(
                    separateVideoLutEnabled = enabled,
                    videoLutId = currentPreferences.videoLutId ?: initialVideoLutId
                )
                resolveLutIdForMode(updatedPreferences, CaptureMode.VIDEO)?.let {
                    applyLut(it)
                }
            }
        }
    }

    private fun resolveLutIdForMode(preferences: UserPreferences, mode: CaptureMode): String? {
        return resolveLutIdForCaptureMode(
            photoLutId = preferences.lutId,
            videoLutId = preferences.videoLutId,
            separateVideoLutEnabled = preferences.separateVideoLutEnabled,
            captureMode = mode,
            defaultLutId = availableLutList.firstOrNull { it.isDefault }?.id,
        )
    }

    private fun applyLut(normalizedLutId: String) {
        val loadGeneration = ++lutLoadGeneration
        lutLoadJob?.cancel()
        val profile = state.value.videoConfig.logProfile
        if (state.value.captureMode == CaptureMode.VIDEO && profile.isEnabled &&
            !profile.matchesLut(currentLutConfig?.curve, currentLutConfig?.colorSpace)) {
            currentLutConfig = null
            cameraController.setLogLutActive(false)
        }
        currentLutId.value = normalizedLutId
        if (normalizedLutId == "none") {
            currentLutConfig = null
            // LUT 已禁用，通知相机控制器
            cameraController.setLogLutActive(false)
            cameraController.setLutEnabled(false)
        } else {
            val hadActiveLut = currentLutConfig != null
            if (!hadActiveLut) {
                // 首次启动时先保持”未启用”状态，避免 Live Photo 在 LUT 文件尚未加载完成前录入原始画面。
                cameraController.setLutEnabled(false)
            }
            lutLoadJob = viewModelScope.launch {
                val loadedLut = withContext(Dispatchers.IO) {
                    contentRepository.lutManager.loadLut(normalizedLutId)
                }
                if (lutLoadGeneration != loadGeneration || currentLutId.value != normalizedLutId) {
                    return@launch
                }
                if (state.value.captureMode == CaptureMode.VIDEO) {
                    val logProfile = state.value.videoConfig.logProfile
                    if (logProfile != VideoLogProfile.OFF && loadedLut != null) {
                        if (!logProfile.matchesLut(loadedLut.curve, loadedLut.colorSpace)) {
                            PLog.d(TAG, "Deselecting newly selected LUT [${loadedLut.title}] because it does not match video log profile [${logProfile.name}] colorSpace/curve")
                            setLut(null, persist = false)
                            return@launch
                        }
                    }
                }
                currentLutConfig = loadedLut
                cameraController.setLogLutActive(loadedLut?.curve?.isLog == true)
                cameraController.setLutEnabled(loadedLut != null)
                if (lutLoadGeneration == loadGeneration) {
                    lutLoadJob = null
                }
            }
            if (hadActiveLut) {
                cameraController.setLutEnabled(true)
            }
        }
    }

    /**
     * 预览下一个滤镜（不切换）
     */
    fun peekNextLut(): LutInfo? {
        val luts = selectableLutList
        if (luts.isEmpty()) return null
        val currentIndex = luts.indexOfFirst { it.id == currentLutId.value }
        val nextIndex = if (currentIndex == -1 || currentIndex == luts.size - 1) 0 else currentIndex + 1
        return luts[nextIndex]
    }

    /**
     * 预览上一个滤镜（不切换）
     */
    fun peekPreviousLut(): LutInfo? {
        val luts = selectableLutList
        if (luts.isEmpty()) return null
        val currentIndex = luts.indexOfFirst { it.id == currentLutId.value }
        val prevIndex = if (currentIndex <= 0) luts.size - 1 else currentIndex - 1
        return luts[prevIndex]
    }

    /**
     * 切换到下一个滤镜
     */
    fun switchToNextLut(): LutInfo? {
        val nextLut = peekNextLut() ?: return null
        setLut(nextLut.id)
        vibrationHelper.vibrate()
        return nextLut
    }

    /**
     * 切换到上一个滤镜
     */
    fun switchToPreviousLut(): LutInfo? {
        val previousLut = peekPreviousLut() ?: return null
        setLut(previousLut.id)
        vibrationHelper.vibrate()
        return previousLut
    }

    fun updateLut() {
        viewModelScope.launch {
            val preferences = userPreferencesRepository.userPreferences.first()
            val newLutId = resolveLutIdForMode(preferences, state.value.captureMode) ?: return@launch
            if (currentLutId.value != newLutId) {
                setLut(newLutId, persist = false)
            }
        }
    }

    /**
     * 设置是否应用 Ultra HDR 策略
     */
    fun setApplyUltraHDR(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.saveApplyUltraHDR(enabled)
        }
    }

    fun setSaveLocation(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.saveSaveLocation(enabled)
        }
    }

    fun setLocationTrackingForeground(foreground: Boolean) {
        locationTrackingForeground = foreground
        applyLocationTracking(saveLocationEnabled.value)
    }

    private fun applyLocationTracking(saveLocation: Boolean) {
        if (locationTrackingForeground && saveLocation) {
            locationManager.startUpdates()
        } else {
            locationManager.stopUpdates()
        }
    }

    fun setOpenAIApiKey(key: String) {
        viewModelScope.launch {
            userPreferencesRepository.saveOpenAIApiKey(key)
        }
    }

    fun setOpenAIUrl(url: String) {
        viewModelScope.launch {
            userPreferencesRepository.saveOpenAIBaseUrl(url)
        }
    }

    fun setOpenAIModel(model: String) {
        viewModelScope.launch {
            userPreferencesRepository.saveOpenAIModel(model)
        }
    }

    fun setUseBuiltInAiService(use: Boolean) {
        if (use && !isPurchased.value) {
            showPaymentDialog = true
            return
        }
        viewModelScope.launch {
            userPreferencesRepository.saveUseBuiltInAiService(use)
        }
    }

    /**
     * 查询可用的 AI 模型列表
     */
    fun fetchAvailableAIModels() {
        if (_isFetchingAIModels.value) return

        viewModelScope.launch {
            _isFetchingAIModels.value = true

            try {
                val context = getApplication<Application>()
                val client = OpenAIApiClient()
                client.initialize(context)
                val result = client.getAvailableModels()
                result.onSuccess { models ->
                    _availableOpenAIModels.value = models
                    // 如果当前选择的模型为空且有可用模型，自动选择第一个
                    if (openAIModel.value.isNullOrBlank() && models.isNotEmpty()) {
                        setOpenAIModel(models.first())
                    }
                }.onFailure { e ->
                    PLog.e(TAG, "Failed to fetch AI models", e)
                }
            } catch (e: Exception) {
                PLog.e(TAG, "Error initializing OpenAIApiClient for model fetch", e)
            } finally {
                _isFetchingAIModels.value = false
            }
        }
    }

    /**
     * 设置是否启用 P010
     */
    fun setUseP010(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.saveUseP010(enabled)
        }
    }

    fun setUseP3ColorSpace(enabled: Boolean) {
        cameraController.setUseP3ColorSpace(enabled)
        viewModelScope.launch {
            userPreferencesRepository.saveUseP3ColorSpace(enabled)
        }
        reopenCamera()
    }

    fun setUltraHdrGainMapEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.saveUltraHdrGainMapEnabled(enabled)
        }
    }

    /**
     * 获取 LUT 信息
     */
    fun getLutInfo(id: String): LutInfo? {
        return contentRepository.lutManager.getLutInfo(id)
    }

    /** RAW HDR profile generation needs the same unfiltered viewfinder target as a normal photo. */
    private fun captureSequenceRawReference(settings: CaptureSettingsSnapshot) {
        val reference = settings.sourceRawReference ?: return
        val failure = getApplication<Application>().getString(R.string.advanced_raw_reference_unavailable)
        val view = glSurfaceView ?: run {
            reference.fail(IllegalStateException(failure))
            return
        }
        val rotation = capturePreviewThumbnailRotation()
        view.captureOriginalPreviewFrame(640) { bitmap ->
            if (bitmap == null) {
                reference.fail(IllegalStateException(failure))
            } else {
                try {
                    val rotated = rotatePreviewBitmapForCapture(bitmap, rotation)
                    if (rotated !== bitmap && !bitmap.isRecycled) bitmap.recycle()
                    if (reference.offer(rotated)) {
                        PLog.i(TAG, "RAW source reference captured: capture=${settings.captureId} size=${rotated.width}x${rotated.height}")
                    }
                } catch (error: Exception) {
                    if (!bitmap.isRecycled) bitmap.recycle()
                    reference.fail(error)
                }
            }
        }
    }

    private fun capturePhotoThumbnails(settings: CaptureSettingsSnapshot) {
        val glView = glSurfaceView ?: run {
            previewThumbnail = null
            return
        }
        val rotation = capturePreviewThumbnailRotation()
        glView.capturePhotoPreviewFrames(
            onDisplayCaptured = { bitmap ->
                val thumbnail = rotatePreviewBitmapForCapture(bitmap, rotation)
                settings.displayThumbnail = thumbnail
                GalleryManager.updateProcessingThumbnail(settings.photoId, thumbnail)
                publishCapturedThumbnail(settings)
            },
            onOriginalCaptured = { bitmap ->
                val thumbnail = rotatePreviewBitmapForCapture(bitmap, rotation)
                settings.originalThumbnail = thumbnail
                previewThumbnail = thumbnail
            },
        )
    }

    /**
     * 从相机捕获预览帧并生成所有 LUT 的预览图
     */
    fun generateThumbnail(onGenerated: ((Bitmap?) -> Unit)? = null) {
        val isGeneralPreviewRequest = onGenerated == null
        if (isGeneralPreviewRequest && isGeneratingPreviews) {
            PLog.d(TAG, "Already generating previews, skipping")
            return
        }

        if (isGeneralPreviewRequest) isGeneratingPreviews = true

        val glView = glSurfaceView
        if (glView != null) {
            val thumbnailRotation = capturePreviewThumbnailRotation()
            glView.captureOriginalPreviewFrame { bitmap ->
                val thumbnail = bitmap?.let {
                    rotatePreviewBitmapForCapture(it, thumbnailRotation)
                }
                previewThumbnail = thumbnail
                onGenerated?.invoke(thumbnail)
                if (isGeneralPreviewRequest) isGeneratingPreviews = false
            }
        } else {
            previewThumbnail = null
            onGenerated?.invoke(null)
            if (isGeneralPreviewRequest) isGeneratingPreviews = false
        }
    }

    private fun capturePreviewThumbnailRotation(): Float {
        val deviceRotation = OrientationObserver.captureRotationDegrees.toInt()
        val lensFacing = cameraController.getLensFacing()
        val baseRotation = if (lensFacing == CameraCharacteristics.LENS_FACING_FRONT) {
            (360 - deviceRotation) % 360
        } else {
            deviceRotation
        }
        val currentCameraId = cameraController.getCurrentCameraId()
        val orientationOffset = userPreferences.value.cameraOrientationOffsets[currentCameraId] ?: 0
        return ((baseRotation + orientationOffset) % 360).toFloat()
    }

    private fun rotatePreviewBitmapForCapture(bitmap: Bitmap, rotationDegrees: Float): Bitmap {
        if (rotationDegrees == 0f) {
            return bitmap
        }
        val sourceWidth = bitmap.width
        val sourceHeight = bitmap.height
        val rotated = BitmapUtils.rotate(bitmap, rotationDegrees)
        PLog.d(
            TAG,
            "Preview bitmap rotated for capture: ${sourceWidth}x${sourceHeight}, rotation=$rotationDegrees"
        )
        return rotated
    }

    suspend fun applyLut(bitmap: Bitmap): Bitmap = withContext(Dispatchers.IO) {
        currentLutConfig?.let { lut ->
            val params = getMergedRecipeParams(contentRepository.lutManager.loadColorRecipeParams(currentLutId.value))
            contentRepository.imageProcessor.applyLut(
                bitmap = bitmap,
                lutConfig = lut,
                colorRecipeParams = params
            )
        } ?: bitmap
    }

    /** The display preview already includes its look; only composite the captured photo's frame. */
    suspend fun renderCaptureAnimationFrame(bitmap: Bitmap, metadata: MediaMetadata): Bitmap =
        withContext(Dispatchers.IO) {
            val context = getApplication<Application>()
            val frameId = metadata.frameId ?: return@withContext bitmap
            val manager = contentRepository.frameManager
            val template = manager.loadTemplate(frameId) ?: return@withContext bitmap
            val frameMetadata = manager.resolveFrameLocation(
                template,
                metadata.copy(
                    customProperties = metadata.customProperties.ifEmpty {
                        manager.loadCustomProperties(frameId)
                    }
                )
            )
            // FrameRenderer holds mutable Canvas/Paint state; do not share it with photo export.
            FrameRenderer(context, contentRepository.lutManager).render(bitmap, template, frameMetadata)
        }

    @Volatile private var coachHistogramElapsedMs = 0L
    @Volatile private var coachHistogramCameraId: String? = null
    fun captureCoachHistogram(): IntArray? = state.value.histogram.takeIf {
        coachHistogramCameraId == state.value.currentCameraId &&
            SystemClock.elapsedRealtime() - coachHistogramElapsedMs in 0L..2000L
    }
    fun handleHistogramUpdate(histogram: IntArray) {
        coachHistogramElapsedMs = SystemClock.elapsedRealtime()
        coachHistogramCameraId = state.value.currentCameraId
        cameraController.updateHistogram(histogram)
    }

    fun handleMeteringUpdate(totalWeight: Double, weightedSumLuminance: Double) {
        cameraController.calculateAutoMetering(totalWeight, weightedSumLuminance)
    }

    fun handleHighlightPointUpdate(x: Float, y: Float) {
        cameraController.updateHighlightPoint(x, y)
    }

    fun handleEyeFocusInputUpdate(frame: EyeFocusPreviewFrame) {
        val cameraState = state.value
        val acceptsPortraitMask = cameraState.captureMode == CaptureMode.PHOTO &&
            cameraState.useRaw &&
            cameraState.isRawSupported &&
            cameraState.isPreviewActive &&
            !cameraState.isCapturing
        val acceptsEyeFocus = (eyeFocusEnabled.value || subjectFocusEnabled.value) &&
            (isEyeFocusRuntimeAvailable || subjectFocusEnabled.value) &&
            cameraState.isPreviewActive &&
            cameraState.isAutoFocus &&
            !cameraState.isHyperfocalFocusEnabled &&
            !cameraState.isCapturing &&
            (cameraState.focusPoint == null || cameraState.focusPointSource == FocusPointSource.EYE)
        val acceptsCoach = needsSceneDetection() && cameraState.captureMode == CaptureMode.PHOTO &&
            cameraState.isPreviewActive && !cameraState.isCapturing
        if (!acceptsEyeFocus && !acceptsPortraitMask && !acceptsCoach) {
            frame.bitmap.recycle()
            completeEyeFocusInput()
            return
        }

        if (
            !previewEyeFocusProcessor.processFrame(
                frame = frame,
                detectEyeTarget = acceptsEyeFocus || acceptsCoach,
                createFaceMask = acceptsPortraitMask,
                subjectCategory = if (subjectFocusEnabled.value) subjectFocusCategory.value else if (acceptsCoach) "auto" else null,
            )
        ) {
            frame.bitmap.recycle()
            completeEyeFocusInput()
        }
    }

    private fun handleEyeFocusTarget(target: PreviewEyeFocusProcessor.EyeTarget) {
        val cameraState = state.value
        if (needsSceneDetection() && cameraState.isPreviewActive && !cameraState.isCapturing) {
            val now = SystemClock.elapsedRealtime()
            _coachFrameMotion.value = trackedSceneMotion.observe(target.bounds?.centerX() ?: target.x,
                target.bounds?.centerY() ?: target.y, target.label, now)
            sceneSampleElapsedMs = now
        }
        if (
            (!eyeFocusEnabled.value && !subjectFocusEnabled.value) ||
            (!isEyeFocusRuntimeAvailable && !subjectFocusEnabled.value) ||
            !cameraState.isPreviewActive ||
            !cameraState.isAutoFocus ||
            cameraState.isHyperfocalFocusEnabled ||
            cameraState.isCapturing ||
            (cameraState.focusPoint != null && cameraState.focusPointSource == FocusPointSource.MANUAL)
        ) {
            return
        }

        _subjectFocusLabel.value = target.label.takeIf { subjectFocusEnabled.value }
        cameraController.updateEyeFocusTarget(target.x, target.y)
        if (!cameraState.supportsPointAutoFocus) {
            return
        }

        val now = SystemClock.elapsedRealtime()
        val previousPoint = lastEyeFocusTriggerPoint
        val elapsed = now - lastEyeFocusTriggerElapsedMs
        val moved = previousPoint != null && hypot(
            target.x - previousPoint.first,
            target.y - previousPoint.second,
        ) >= EYE_FOCUS_MOVE_THRESHOLD
        val retryAfterFailure = cameraState.focusPointSource == FocusPointSource.EYE &&
            cameraState.focusSuccess == false && elapsed >= EYE_FOCUS_RETRY_INTERVAL_MS
        val periodicRefresh = previousPoint != null && elapsed >= EYE_FOCUS_REFRESH_INTERVAL_MS
        val shouldTrigger = previousPoint == null || moved || retryAfterFailure || periodicRefresh
        if (shouldTrigger && !cameraState.isFocusing && elapsed >= EYE_FOCUS_MIN_TRIGGER_INTERVAL_MS) {
            lastEyeFocusTriggerPoint = Pair(target.x, target.y)
            lastEyeFocusTriggerElapsedMs = now
            cameraController.focusOnNormalizedPoint(
                normX = target.x,
                normY = target.y,
                source = FocusPointSource.EYE,
            )
        }
    }

    private fun completeEyeFocusInput() {
        isEyeFocusBusy = false
        glSurfaceView?.setEyeFocusBusy(false)
    }

    private fun resetEyeFocusTracking(cancelCameraFocus: Boolean, reason: String) {
        clearSceneTracking()
        lastEyeFocusTriggerPoint = null
        lastEyeFocusTriggerElapsedMs = 0L
        if (cancelCameraFocus) {
            cameraController.cancelEyeFocus(reason)
        }
    }

    private fun capturePortraitMaskSnapshot(): PortraitMaskSnapshot? {
        val mask = latestPortraitMask ?: return null
        val ageNanos = SystemClock.elapsedRealtimeNanos() - mask.sampleElapsedRealtimeNanos
        if (ageNanos !in 0L..PORTRAIT_MASK_MAX_AGE_NS) return null
        if (mask.cameraId != cameraController.getCurrentCameraId()) return null
        if (mask.sensorOrientationDegrees != cameraController.getSensorOrientation()) return null
        val isFrontFacing = cameraController.getLensFacing() ==
            CameraCharacteristics.LENS_FACING_FRONT
        if (mask.isFrontFacing != isFrontFacing) return null
        return mask.copy(confidence = mask.confidence.copyOf())
    }

    fun setFrame(frameId: String?) {
        if (currentFrameId == frameId) return
        currentFrameId = frameId
        viewModelScope.launch {
            applyCameraFeatureUpdate(
                CameraFeatureUpdate(frameId = SettingValue(frameId))
            )
        }
    }

    /**
     * 获取边框的自定义属性
     */
    suspend fun getFrameCustomProperties(frameId: String): Map<String, String> {
        return contentRepository.frameManager.loadCustomProperties(frameId)
    }

    /**
     * 保存边框的自定义属性
     */
    suspend fun saveFrameCustomProperties(frameId: String, properties: Map<String, String>) {
        contentRepository.frameManager.saveCustomProperties(frameId, properties)
    }

    fun loadFrameEditorDraft(frameId: String?, imageFrame: Boolean = false): FrameEditorDraft {
        return contentRepository.frameManager.createEditorDraft(frameId, imageFrame)
    }

    suspend fun saveFrameEditorDraft(draft: FrameEditorDraft): String? = withContext(Dispatchers.IO) {
        val savedId = contentRepository.frameManager.saveEditorDraft(draft)
        if (savedId != null) {
            contentRepository.refreshCustomContent()
        }
        savedId
    }

    fun importFrameEditorImage(uri: Uri, frameIdHint: String? = null): String? {
        return contentRepository.frameManager.importEditorFrameImage(uri, frameIdHint)
    }

    fun readFrameEditorImageDesignSize(path: String) =
        contentRepository.frameRenderer.readImageFrameDesignSize(path)

    suspend fun renderFrameEditorPreview(draft: FrameEditorDraft, selectionColor: Int? = null): Bitmap =
        withContext(Dispatchers.Default) {
            val source = FramePreviewFactory.createPreviewBitmap(draft.layout.designSize)
            val template = draft.toTemplate(draft.editableFrameId ?: draft.sourceFrameId ?: "preview_frame")
            val metadata = FramePreviewFactory.createPreviewMetadata(source.width, source.height)
            val selectedId = draft.effectiveSelectedElementId
            val selectedElement = template.elements.getOrNull(draft.elements.indexOfFirst { it.draftId == selectedId })
                ?: template.elementsTop?.getOrNull(draft.elementsTop.orEmpty().indexOfFirst { it.draftId == selectedId })
            val selection = if (selectionColor != null && selectedElement != null) {
                FramePreviewSelection(selectedElement, selectionColor)
            } else null
            var rendered: Bitmap? = null
            try {
                contentRepository.frameRenderer.render(source, template, metadata, selection).also { rendered = it }
            } finally {
                if (rendered !== source) source.recycle()
            }
        }

    /**
     * 设置是否显示直方图
     */
    fun saveShowHistogram(show: Boolean) {
        showHistogram = show
        // 保存到用户偏好设置
        viewModelScope.launch {
            userPreferencesRepository.saveShowHistogram(show)
        }
    }

    // ==================== 延时拍摄和网格线相关方法 ====================

    /** 拍照模式：YUV 多帧降噪。 */
    fun setUseJpgMax(enabled: Boolean) {
        viewModelScope.launch {
            applyCameraFeatureUpdate(
                CameraFeatureUpdate(useJpgMax = SettingValue(enabled))
            )
        }
    }

    fun setJpgMultiFrameDenoiseFrameCount(count: Int) {
        val normalizedCount = MultiFrameConfig.normalizeDenoiseFrameCount(count)
        cameraController.setJpgMultiFrameDenoiseFrameCount(normalizedCount)
        viewModelScope.launch {
            userPreferencesRepository.saveJpgMultiFrameDenoiseFrameCount(normalizedCount)
        }
    }

    fun setJpgMultiFrameDenoiseOutputScale(scale: Float) {
        viewModelScope.launch {
            userPreferencesRepository.saveJpgMultiFrameDenoiseOutputScale(
                MultiFrameConfig.normalizeOutputScale(scale)
            )
        }
    }

    fun setHdrPlusEnabled(enabled: Boolean) {
        if (state.value.captureMode != CaptureMode.PHOTO || !state.value.useRaw || state.value.isCapturing) return
        viewModelScope.launch {
            applyCameraFeatureUpdate(CameraFeatureUpdate(useRawMax = SettingValue(enabled)))
        }
    }

    fun setHdrPlusMergeMode(mode: MgcRawMaxMode) {
        cameraController.setHdrPlusMergeMode(mode)
        val initialFrameCount = if (mode == MgcRawMaxMode.HDR_PLUS_INSPIRED &&
            cameraController.state.value.hdrPlusFrameCount < 7) 7 else null
        initialFrameCount?.let(cameraController::setHdrPlusFrameCount)
        viewModelScope.launch {
            userPreferencesRepository.saveHdrPlusMergeMode(mode)
            initialFrameCount?.let { userPreferencesRepository.saveHdrPlusFrameCount(it) }
        }
    }

    fun setHdrPlusFrameCount(count: Int) {
        val normalizedCount = MultiFrameConfig.normalizeHdrPlusFrameCount(
            count,
            bracketExposureEnabled = cameraController.state.value.hdrPlusBracketExposureEnabled,
        )
        cameraController.setHdrPlusFrameCount(normalizedCount)
        viewModelScope.launch {
            userPreferencesRepository.saveHdrPlusFrameCount(normalizedCount)
        }
    }

    fun setHdrPlusBracketExposureEnabled(enabled: Boolean) {
        cameraController.setHdrPlusBracketExposureEnabled(enabled)
        viewModelScope.launch {
            userPreferencesRepository.saveHdrPlusBracketExposureEnabled(enabled)
        }
    }

    fun setHdrPlusLongFrameExposureEv(value: Float) {
        val normalized = MultiFrameConfig.normalizeLongFrameExposureEv(value)
        cameraController.setHdrPlusLongFrameExposureEv(normalized)
        viewModelScope.launch { userPreferencesRepository.saveHdrPlusLongFrameExposureEv(normalized) }
    }

    fun setHdrPlusShortFrameExposureEv(value: Float) {
        val normalized = MultiFrameConfig.normalizeShortFrameExposureEv(value)
        cameraController.setHdrPlusShortFrameExposureEv(normalized)
        viewModelScope.launch { userPreferencesRepository.saveHdrPlusShortFrameExposureEv(normalized) }
    }

    fun setMlAeMaxHdrRatio(value: Float) {
        viewModelScope.launch { userPreferencesRepository.saveMlAeMaxHdrRatio(value) }
    }

    fun setMultipleExposureBlendMode(mode: MultipleExposureBlendMode) {
        if (multipleExposureState.isSessionActive || multipleExposureState.isProcessing || state.value.isCapturing) return
        multipleExposureState = multipleExposureState.copy(blendMode = mode)
        viewModelScope.launch { userPreferencesRepository.saveMultipleExposureBlendMode(mode) }
    }

    fun setMultipleExposureCount(count: Int) {
        val normalizedCount = count.coerceIn(2, 9)
        multipleExposureState = multipleExposureState.copy(targetCount = normalizedCount)
        viewModelScope.launch {
            userPreferencesRepository.saveMultipleExposureCount(normalizedCount)
        }
    }

    fun setRawDigitalZoomResamplingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.saveRawDigitalZoomResamplingEnabled(enabled)
        }
    }

    fun setRawMaxOutputScale(scale: Float) {
        viewModelScope.launch {
            val prefs = userPreferencesRepository.userPreferences.first()
            val normalizedScale = prefs.rawOutputUpscaleMode.resolveOutputScale(scale)
            userPreferencesRepository.saveRawMaxOutputScale(normalizedScale)
            cameraController.setMultiFrameOutputScale(
                resolveMultiFrameOutputScale(
                    useJpgMax = prefs.useJpgMax && !prefs.useRaw &&
                        !prefs.useMultipleExposure,
                    useRawMax = prefs.useRawMax && prefs.useRaw &&
                        !prefs.useMultipleExposure,
                    rawMaxOutputScale = normalizedScale,
                    jpgMultiFrameDenoiseOutputScale = prefs.jpgMultiFrameDenoiseOutputScale,
                )
            )
        }
    }

    /**
     * 设置 RAWmax 输出放大算法。MGC RAISR 与原版一致只支持 2x per-shift 放大，
     * 因此选中时输出倍率由偏好层锁定为 2x，与 Lanczos 连续倍率放大互斥。
     */
    fun setRawOutputUpscaleMode(mode: RawOutputUpscaleMode) {
        viewModelScope.launch {
            userPreferencesRepository.saveRawOutputUpscaleMode(mode)
            val prefs = userPreferencesRepository.userPreferences.first()
            cameraController.setMultiFrameOutputScale(
                resolveMultiFrameOutputScale(
                    useJpgMax = prefs.useJpgMax && !prefs.useRaw &&
                        !prefs.useMultipleExposure,
                    useRawMax = prefs.useRawMax && prefs.useRaw &&
                        !prefs.useMultipleExposure,
                    rawMaxOutputScale = prefs.rawMaxOutputScale,
                    jpgMultiFrameDenoiseOutputScale = prefs.jpgMultiFrameDenoiseOutputScale,
                )
            )
        }
    }

    private fun shouldEnableLivePhoto(prefs: UserPreferences): Boolean {
        // RAWmax supports Live Photo. A saved JPGmax preference is inactive in RAW mode.
        val activeUseJpgMax = prefs.useJpgMax && !prefs.useRaw
        return prefs.useLivePhoto && !activeUseJpgMax && !prefs.useMultipleExposure &&
            prefs.captureMode == CaptureMode.PHOTO
    }

    /**
     * 设置是否启用 Live Photo
     */
    fun setUseLivePhoto(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                applyCameraFeatureUpdate(
                    CameraFeatureUpdate(
                        useJpgMax = SettingValue(false),
                        useMultipleExposure = SettingValue(false),
                    )
                )
            }
            cameraController.setUseLivePhoto(enabled)
            userPreferencesRepository.saveUseLivePhoto(enabled)
        }
    }

    fun setEnableDevelopAnimation(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.saveEnableDevelopAnimation(enabled)
        }
    }

    fun setDevelopAnimationStyle(style: DevelopAnimationStyle) {
        viewModelScope.launch {
            userPreferencesRepository.saveDevelopAnimationStyle(style)
        }
    }

    /**
     * 切换延时拍摄档位（0s → 3s → 5s → 10s → 0s）
     */
    fun toggleTimer() {
        val currentTimer = state.value.timerSeconds
        val nextTimer = when (currentTimer) {
            0 -> 3
            3 -> 5
            5 -> 10
            10 -> 0
            else -> 0
        }
        cameraController.setTimerSeconds(nextTimer)
    }

    /**
     * 切换网格线显示
     */
    fun toggleGrid() {
        setShowGrid(!state.value.showGrid)
    }

    /**
     * 设置是否显示网格线
     */
    fun setShowGrid(show: Boolean) {
        cameraController.setShowGrid(show)
        viewModelScope.launch {
            userPreferencesRepository.saveShowGrid(show)
        }
    }

    fun setGridStyle(style: GridStyle) {
        cameraController.setGridStyle(style)
        viewModelScope.launch {
            userPreferencesRepository.saveGridStyle(style)
        }
    }

    /**
     * 切换 RAW 格式拍摄
     */
    fun toggleRaw() {
        val nextValue = !useRaw.value
        setUseRaw(nextValue)
    }

    fun setPhotoCaptureSize(size: String?) {
        if (state.value.isCapturing || state.value.videoRecordingState.isRecording) return
        viewModelScope.launch { userPreferencesRepository.savePhotoCaptureSize(size) }
    }

    fun setEnhancedZoom(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.saveEnhancedZoom(enabled) }
    }

    fun setPhotoFinishUpscale(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.savePhotoFinishUpscale(enabled) }
    }

    fun setUseRaw(useRaw: Boolean) {
        viewModelScope.launch {
            applyCameraFeatureUpdate(
                CameraFeatureUpdate(useRaw = SettingValue(useRaw))
            )
        }
    }

    fun setExportDngWithRawExport(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.saveExportDngWithRawExport(enabled)
        }
    }

    // ==================== 新增设置项方法 ====================

    /**
     * 设置是否显示水平仪
     */
    fun setShowLevelIndicator(show: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.saveShowLevelIndicator(show)
        }
    }

    /**
     * 设置手动对焦时是否显示峰值对焦
     */
    fun setPreviewAssistSettings(settings: PreviewAssistSettings) {
        viewModelScope.launch {
            userPreferencesRepository.savePreviewAssistSettings(settings.normalized())
        }
    }

    fun setFocusPeakingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.saveFocusPeakingEnabled(enabled)
        }
    }

    fun setEyeFocusEnabled(enabled: Boolean) {
        if (enabled && isEyeFocusRuntimeAvailable) {
            previewEyeFocusProcessor.prewarm()
        } else if (!enabled) {
            previewEyeFocusProcessor.resetTracking()
            resetEyeFocusTracking(cancelCameraFocus = true, reason = "disabled")
        }
        viewModelScope.launch {
            userPreferencesRepository.saveEyeFocusEnabled(enabled)
        }
    }

    /**
     * 复制边框并把副本插入到原边框后面。
     */
    fun copyFrame(frame: FrameInfo, copyName: String) {
        viewModelScope.launch {
            val newFrameId = withContext(Dispatchers.IO) {
                contentRepository.getCustomImportManager().copyFrame(frame, copyName)
            }
            if (newFrameId != null) {
                withContext(Dispatchers.IO) {
                    val currentOrder = userPreferencesRepository.userPreferences.first().frameOrder.toMutableList()
                    if (currentOrder.isEmpty()) {
                        val allIds = availableFrameList.map { it.id }.toMutableList()
                        val index = allIds.indexOf(frame.id)
                        if (index != -1) {
                            allIds.add(index + 1, newFrameId)
                        } else {
                            allIds.add(newFrameId)
                        }
                        userPreferencesRepository.saveFrameOrder(allIds)
                    } else {
                        val index = currentOrder.indexOf(frame.id)
                        if (index != -1) {
                            currentOrder.add(index + 1, newFrameId)
                        } else {
                            currentOrder.add(newFrameId)
                        }
                        userPreferencesRepository.saveFrameOrder(currentOrder)
                    }
                    contentRepository.refreshCustomContent()
                }
            }
        }
    }

    /**
     * 设置是否启用快门声音
     */
    fun setShutterSoundEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.saveShutterSoundEnabled(enabled)
        }
    }

    fun importCaptureSound(isBurst: Boolean, uri: Uri) {
        updateCaptureSound(R.string.settings_shutter_sound_import_failed) {
            captureSoundRepository.import(isBurst, uri)
        }
    }

    fun resetCaptureSound(isBurst: Boolean) {
        updateCaptureSound(R.string.settings_shutter_sound_save_failed) {
            captureSoundRepository.reset(isBurst)
        }
    }

    private fun updateCaptureSound(errorMessage: Int, operation: suspend () -> Unit) {
        if (_captureSoundImporting.value) return
        _captureSoundImporting.value = true
        viewModelScope.launch {
            try {
                operation()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PLog.e(TAG, "Cannot update capture sound", e)
                _captureSoundErrors.emit(errorMessage)
            } finally {
                _captureSoundImporting.value = false
            }
        }
    }

    /**
     * 设置是否启用拍摄震动
     */
    fun setColorPaletteEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.saveColorPaletteEnabled(enabled) }
    }

    fun setAutoRotateEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.saveAutoRotateEnabled(enabled) }
    }

    fun setVibrationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.saveVibrationEnabled(enabled)
        }
    }

    /**
     * 设置是否保持屏幕常亮
     */
    fun setKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.saveKeepScreenOn(enabled)
        }
    }

    fun setWindowScreenBrightness(value: Float?) {
        viewModelScope.launch {
            userPreferencesRepository.saveWindowScreenBrightness(value)
        }
    }

    /**
     * 设置音量键操作
     */
    fun setVolumeKeyAction(action: VolumeKeyAction) {
        viewModelScope.launch {
            userPreferencesRepository.saveVolumeKeyAction(action)
        }
    }

    /**
     * 处理音量键按下
     * @return 是否消费了该事件
     */
    fun handleVolumeKey(isUp: Boolean): Boolean {
        val action = volumeKeyAction.value
        if (action == VolumeKeyAction.NONE) return false

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastVolumeKeyEventTime < VOLUME_KEY_DEBOUNCE_TIME) {
            return true // 还在冷却时间内，消费事件但不做处理
        }
        lastVolumeKeyEventTime = currentTime

        return when (action) {
            VolumeKeyAction.CAPTURE -> {
                capture(state.value.captureMode, "volume-key")
                true
            }

            VolumeKeyAction.EXPOSURE_COMPENSATION -> {
                val currentEV = state.value.exposureCompensation
                val range = state.value.getExposureCompensationRange()
                if (range.lower == 0 && range.upper == 0) return true // 不支持曝光补偿

                if (isUp) {
                    if (currentEV < range.upper) {
                        setExposureCompensation(currentEV + 1)
                    }
                } else {
                    if (currentEV > range.lower) {
                        setExposureCompensation(currentEV - 1)
                    }
                }
                true
            }

            VolumeKeyAction.ZOOM -> {
                handleVolumeZoom(isUp)
                true
            }
        }
    }

    /**
     * 处理音量键变焦切换
     * 逻辑：切换到下一个/上一个 ZoomStop
     */
    private fun handleVolumeZoom(isUp: Boolean) {
        val currentState = state.value
        val availableCameras = currentState.availableCameras
        val currentCamera = currentState.getCurrentCameraInfo() ?: return

        // 1. 获取主摄
        val mainCamera = availableCameras.find {
            it.lensType == if (currentCamera.lensType == LensType.FRONT) LensType.FRONT else LensType.BACK_MAIN
        } ?: return

        // 2. 计算变焦档位 (逻辑同步自 ZoomControlBar.kt)
        val lensZoomStops = calculateLensZoomStops(availableCameras, currentCamera)
        val zoomStops = allZoomStops(
            lensZoomStops,
            mainCamera,
            currentCamera,
            userPreferences.value.customFocalLengths,
            userPreferences.value.hiddenFocalLengths
        )

        if (zoomStops.isEmpty()) return

        // 3. 找到当前或者最近的档位索引
        val currentZoomRatio = zoomRatioByMain
        var currentIndex = zoomStops.indexOfFirst { abs(it - currentZoomRatio) < 0.05f }

        if (currentIndex == -1) {
            // 如果不在已知档位，找到最近的一个
            currentIndex = zoomStops.indices.minByOrNull { abs(zoomStops[it] - currentZoomRatio) } ?: 0
        }

        // 4. 计算下一个索引
        val nextIndex = if (isUp) {
            (currentIndex + 1).coerceAtMost(zoomStops.lastIndex)
        } else {
            (currentIndex - 1).coerceAtLeast(0)
        }

        if (nextIndex != currentIndex) {
            val targetZoom = zoomStops[nextIndex]

            if (isCurrentLensCustomZoomRatioStop(targetZoom)) {
                setZoomRatio(targetZoom)
                return
            }

            // 5. 检查是否需要切换镜头 (逻辑同步自 ZoomControlBar.kt)
            val optimalLens = findOptimalLens(targetZoom, availableCameras, currentCamera.cameraId)
            if (optimalLens != null && optimalLens.cameraId != currentCamera.cameraId) {
                switchToLensAndSetZoomRatio(optimalLens.cameraId, targetZoom)
            } else {
                setZoomRatio(targetZoom)
            }
        }
    }

    /**
     * 计算变焦档位
     */
    fun calculateLensZoomStops(
        cameras: List<CameraInfo>,
        currentCamera: CameraInfo?
    ): List<Float> {
        val stops = mutableListOf<Float>()

        val filter: (CameraInfo) -> Boolean = if (currentCamera?.lensType == LensType.FRONT) {
            { it.lensType == LensType.FRONT }
        } else {
            { it.lensType != LensType.FRONT && it.lensType != LensType.BACK_MACRO }
        }

        // 添加各个镜头的固有变焦比例
        cameras.filter(filter).forEach { camera ->
            val displayZoomRatio = camera.displayIntrinsicZoomRatio
            if (displayZoomRatio > 0) {
                // 避免添加极其接近的变焦倍率（例如 1.0 和 1.0006）
                if (stops.none { abs(it - displayZoomRatio) < 0.01f }) {
                    stops.add(displayZoomRatio)
                }
            }
        }
        return stops.sorted()
    }

    /**
     * 计算变焦档位
     */
    fun allZoomStops(
        lensZoomStops: List<Float>,
        mainCamera: CameraInfo?,
        currentCamera: CameraInfo?,
        customFocalLengths: List<Float> = emptyList(),
        hiddenFocalLengths: List<Float> = emptyList()
    ): List<Float> {
        val stops = mutableListOf<Float>()

        if (currentCamera?.lensType == LensType.FRONT) {
            stops.addAll(lensZoomStops)
            if (stops.none { abs(it - 2f) <= 0.1f }) {
                stops.add(2f)
            }
            customFocalLengths.forEach { value ->
                CustomFocalLengthValue.toZoomRatio(value, mainCamera, currentCamera)?.let { zoom ->
                    if (stops.none { abs(it - zoom) <= 0.01f }) {
                        stops.add(zoom)
                    }
                }
            }
            return stops.sorted()
        }

        mainCamera ?: return lensZoomStops.sorted()

        // 1. 添加并过滤原生镜头焦段
        if (mainCamera.focalLength35mmEquivalent > 0) {
            val filteredLensStops = lensZoomStops.filter { zoom ->
                val fl = zoom * mainCamera.focalLength35mmEquivalent
                hiddenFocalLengths.none { abs(it - fl) < 0.5f }
            }
            stops.addAll(filteredLensStops)
        } else {
            stops.addAll(lensZoomStops)
        }

        addDefaultMinimumZoomStop(stops, lensZoomStops, mainCamera, hiddenFocalLengths)

        // 2. 添加自定义焦段/倍率 (不参与隐藏过滤)
        customFocalLengths.forEach { value ->
            CustomFocalLengthValue.toZoomRatio(value, mainCamera, currentCamera)?.let { zoom ->
                if (stops.none { abs(it - zoom) <= 0.01f }) {
                    stops.add(zoom)
                }
            }
        }

        return stops.sorted()
    }

    private fun addDefaultMinimumZoomStop(
        stops: MutableList<Float>,
        lensZoomStops: List<Float>,
        mainCamera: CameraInfo,
        hiddenFocalLengths: List<Float> = emptyList()
    ) {
        val mainZoom = mainCamera.displayIntrinsicZoomRatio
        val hasSmallerLens = lensZoomStops.any { it < mainZoom - 0.01f }
        val minimumZoom = mainCamera.minZoom * mainZoom

        if (hasSmallerLens || minimumZoom >= mainZoom - 0.01f) return

        val isHidden = if (mainCamera.focalLength35mmEquivalent > 0) {
            val minimumFocalLength = minimumZoom * mainCamera.focalLength35mmEquivalent
            hiddenFocalLengths.any { abs(it - minimumFocalLength) < 0.5f }
        } else {
            false
        }

        if (!isHidden && stops.none { abs(it - minimumZoom) <= 0.01f }) {
            stops.add(minimumZoom)
        }
    }

    /**
     * 根据变焦倍率找到最佳镜头
     */
    fun findOptimalLens(
        targetZoom: Float,
        cameras: List<CameraInfo>,
        currentCameraId: String
    ): CameraInfo? {
        if (isVideoLensLocked()) {
            return cameras.firstOrNull { it.cameraId == currentCameraId }
        }
        val currentLensType = cameras.find { it.cameraId == currentCameraId }?.lensType
        val zoomableCameras =
            cameras.filter { if (currentLensType == LensType.FRONT) it.lensType == LensType.FRONT else (it.lensType != LensType.FRONT && it.lensType != LensType.BACK_MACRO) }
        if (zoomableCameras.isEmpty()) return null
        val candidates = zoomableCameras
            .filter { it.displayIntrinsicZoomRatio <= targetZoom + 0.01f }
        val bestZoom = candidates.maxOfOrNull { it.displayIntrinsicZoomRatio }
            ?: zoomableCameras.minOfOrNull { it.displayIntrinsicZoomRatio }
            ?: return null
        val tiedCandidates = candidates.filter { abs(it.displayIntrinsicZoomRatio - bestZoom) <= 0.01f }
            .ifEmpty { zoomableCameras.filter { abs(it.displayIntrinsicZoomRatio - bestZoom) <= 0.01f } }
        return tiedCandidates.firstOrNull { it.cameraId == currentCameraId }
            ?: tiedCandidates.firstOrNull()
    }

    fun isCurrentLensCustomZoomRatioStop(targetZoom: Float): Boolean {
        val currentState = state.value
        val currentCamera = currentState.getCurrentCameraInfo() ?: return false
        val mainCamera = currentState.availableCameras.find {
            it.lensType == if (currentCamera.lensType == LensType.FRONT) LensType.FRONT else LensType.BACK_MAIN
        } ?: return false
        return userPreferences.value.customFocalLengths.any { value ->
            CustomFocalLengthValue.isZoomRatio(value) &&
                    abs((CustomFocalLengthValue.toZoomRatio(value, mainCamera, currentCamera) ?: return@any false) - targetZoom) <= 0.01f
        }
    }

    fun isVideoLensLocked(): Boolean {
        val currentState = state.value
        return currentState.videoConfig.shouldLockLens(
            captureMode = currentState.captureMode,
            isRecording = currentState.videoRecordingState.isRecording
        )
    }

    /**
     * 设置是否拍摄后自动保存
     */
    fun setAutoSaveAfterCapture(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.saveAutoSaveAfterCapture(enabled)
        }
    }

    fun addCustomFocalLength(focalLength: Float) {
        viewModelScope.launch {
            val prefs = userPreferencesRepository.userPreferences.first()
            val list = prefs.customFocalLengths.toMutableList()
            if (list.none { CustomFocalLengthValue.matches(it, focalLength) }) {
                list.add(focalLength)
                userPreferencesRepository.saveCustomFocalLengths(
                    list.sortedBy { CustomFocalLengthValue.sortKey(it, state.value.getCurrentCameraInfo()) }
                )
            }
        }
    }

    fun removeCustomFocalLength(focalLength: Float) {
        viewModelScope.launch {
            val prefs = userPreferencesRepository.userPreferences.first()
            val list = prefs.customFocalLengths.toMutableList()
            list.removeAll { CustomFocalLengthValue.matches(it, focalLength) }
            userPreferencesRepository.saveCustomFocalLengths(list)

            // 如果删除了当前的默认焦段，重置为0
            if (CustomFocalLengthValue.matches(prefs.defaultFocalLength, focalLength)) {
                userPreferencesRepository.saveDefaultFocalLength(0f)
            }
        }
    }

    fun toggleFocalLengthVisibility(focalLength: Float) {
        viewModelScope.launch {
            val prefs = userPreferencesRepository.userPreferences.first()
            val list = prefs.hiddenFocalLengths.toMutableList()
            val index = list.indexOfFirst { abs(it - focalLength) < 0.5f }
            if (index != -1) {
                list.removeAt(index)
            } else {
                list.add(focalLength)
                // 如果隐藏了当前的默认焦段，重置默认焦段为0
                if (abs(prefs.defaultFocalLength - focalLength) < 0.5f) {
                    userPreferencesRepository.saveDefaultFocalLength(0f)
                }
            }
            userPreferencesRepository.saveHiddenFocalLengths(list)
        }
    }

    /**
     * 设置 RAW 色彩空间
     */
    fun setColorSpace(colorSpace: ColorSpace) {
        viewModelScope.launch {
            userPreferencesRepository.saveColorSpace(colorSpace)
        }
    }

    /**
     * 设置 RAW Log 曲线
     */
    fun setLogCurve(logCurve: TransferCurve) {
        viewModelScope.launch {
            userPreferencesRepository.saveLogCurve(logCurve)
        }
    }

    fun setRawProfile(rawProfile: RawProfile) {
        viewModelScope.launch {
            userPreferencesRepository.saveRawProfile(rawProfile)
        }
    }

    /**
     * 为指定颜色推荐最合适的 LUT 列表
     */
    suspend fun recommendLutsForColor(color: Int): List<LutInfo> = withContext(Dispatchers.IO) {
        contentRepository.lutManager.recommendLutsForColor(color)
    }

    /**
     * 设置降噪等级
     */
    fun setNRLevel(level: Int) {
        viewModelScope.launch {
            userPreferencesRepository.saveNRLevel(level)
        }
    }

    /**
     * 设置锐化等级
     */
    fun setEdgeLevel(level: Int) {
        viewModelScope.launch {
            userPreferencesRepository.saveEdgeLevel(level)
        }
    }

    fun setVideoNRLevel(level: Int) {
        viewModelScope.launch {
            userPreferencesRepository.saveVideoNRLevel(level)
        }
    }

    fun setVideoEdgeLevel(level: Int) {
        viewModelScope.launch {
            userPreferencesRepository.saveVideoEdgeLevel(level)
        }
    }

    fun setVendorCaptureSettings(lensId: String, settings: VendorCaptureSettings) {
        viewModelScope.launch {
            userPreferencesRepository.saveVendorCaptureSettingsForLens(lensId, settings)
        }
    }

    fun upsertCustomVendorKey(key: CustomVendorKey) {
        viewModelScope.launch {
            userPreferencesRepository.upsertCustomVendorKey(key)
        }
    }

    fun removeCustomVendorKey(id: String) {
        viewModelScope.launch {
            userPreferencesRepository.removeCustomVendorKey(id)
        }
    }

    /**
     * 设置照片质量
     */
    fun setPhotoQuality(quality: Int) {
        viewModelScope.launch {
            userPreferencesRepository.savePhotoQuality(quality)
        }
    }

    fun setUseHeicExport(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.saveUseHeicExport(enabled)
        }
    }

    fun setUseJpeg444Export(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.saveUseJpeg444Export(enabled)
        }
    }

    /**
     * 设置摄像头方向偏移
     * @param cameraId 摄像头 ID
     * @param offset 旋转偏移角度 (0, 90, 180, 270)
     */
    fun setCameraOrientationOffset(cameraId: String, offset: Int) {
        viewModelScope.launch {
            userPreferencesRepository.saveCameraOrientationOffset(cameraId, offset)
        }
    }

    /**
     * 设置默认焦段
     */
    fun setDefaultFocalLength(focalLength: Float) {
        viewModelScope.launch {
            userPreferencesRepository.saveDefaultFocalLength(focalLength)
        }
    }

    fun saveZoomDisplayMode(mode: ZoomDisplayMode) {
        viewModelScope.launch {
            userPreferencesRepository.saveZoomDisplayMode(mode.name)
        }
    }


    fun setCustomLensIds(value: String) {
        viewModelScope.launch {
            val lensIds = value.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
            userPreferencesRepository.saveCustomLensIds(lensIds)
            cameraController.refreshCameraList()
        }
    }

    fun setLensIdBlacklist(value: String) {
        viewModelScope.launch {
            val lensIds = value.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
            userPreferencesRepository.saveLensIdBlacklist(lensIds)
            cameraController.refreshCameraList()
        }
    }

    fun addIszLensConfig(
        baseCameraId: String,
        iszZoomRatio: Float,
        isMacro: Boolean,
        portraitRawBlackBorderCrop: RawBlackBorderCrop,
        rawDngMetadataCorrections: IszRawDngMetadataCorrections,
        settings: VendorCaptureSettings
    ) {
        viewModelScope.launch {
            val normalizedBaseCameraId = baseCameraId.trim()
            if (normalizedBaseCameraId.isEmpty() || iszZoomRatio < 1f) return@launch

            val baseCamera = state.value.availableCameras.firstOrNull {
                it.cameraId == normalizedBaseCameraId && !it.isVirtualIszLens
            } ?: return@launch
            val config = IszLensConfig(
                baseCameraId = normalizedBaseCameraId,
                iszZoomRatio = iszZoomRatio,
                isMacro = isMacro,
                rawBlackBorderCrop = IszLensConfig.portraitCropToSensor(
                    portraitCrop = portraitRawBlackBorderCrop,
                    sensorRotation = baseCamera.sensorOrientation,
                ),
                vendorCaptureProfileId = settings.toVirtualLensProfileId()
            )
            val prefs = userPreferencesRepository.userPreferences.first()
            val updatedConfigs = (prefs.iszLensConfigs
                .filterNot { it.virtualCameraId == config.virtualCameraId } + config)
                .distinctBy { it.virtualCameraId }
            userPreferencesRepository.saveIszLensConfigs(updatedConfigs)
            userPreferencesRepository.saveVendorCaptureSettingsForLens(config.virtualCameraId, settings)
            userPreferencesRepository.saveRawDngMetadataCorrections(
                config.virtualCameraId,
                rawDngMetadataCorrections
            )
            cameraController.refreshCameraList()
        }
    }

    fun removeIszLensConfig(config: IszLensConfig) {
        viewModelScope.launch {
            val prefs = userPreferencesRepository.userPreferences.first()
            val updatedConfigs = prefs.iszLensConfigs
                .filterNot { it.virtualCameraId == config.virtualCameraId }
            userPreferencesRepository.saveIszLensConfigs(updatedConfigs)
            userPreferencesRepository.saveVendorCaptureSettingsForLens(
                config.virtualCameraId,
                VendorCaptureSettings.Empty
            )
            userPreferencesRepository.clearRawDngMetadataCorrections(config.virtualCameraId)
            cameraController.refreshCameraList()
        }
    }

    suspend fun discoverMainCameraIdOptions(): List<String> = withContext(Dispatchers.IO) {
        CameraDiscovery(getApplication()).discoverMainCameraIdOptions()
    }

    suspend fun discoverMacroCameraIdOptions(): List<String> = withContext(Dispatchers.IO) {
        CameraDiscovery(getApplication()).discoverMacroCameraIdOptions()
    }

    fun setPreferredMainCameraId(cameraId: String?) {
        viewModelScope.launch {
            userPreferencesRepository.savePreferredMainCameraId(cameraId)
            cameraController.refreshCameraList()
        }
    }

    fun setPreferredMacroCameraId(cameraId: String?) {
        viewModelScope.launch {
            userPreferencesRepository.savePreferredMacroCameraId(cameraId)
            cameraController.refreshCameraList()
        }
    }

    fun setEnableLogicalMultiCameraDiscovery(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.saveEnableLogicalMultiCameraDiscovery(enabled)
            cameraController.refreshCameraList()
        }
    }

    fun setLogicalCameraBindingWhitelist(value: String) {
        viewModelScope.launch {
            userPreferencesRepository.saveLogicalCameraBindingWhitelist(value.split(","))
            cameraController.refreshCameraList()
        }
    }

    /**
     * 首次打开 CameraDevice 前选择默认焦段，避免先打开主摄再切换。
     */
    private fun applyStartupDefaultFocalLength(focalLength: Float) {
        val currentState = state.value
        val availableCameras = currentState.availableCameras
        val currentCamera = currentState.getCurrentCameraInfo() ?: return

        // 找到主摄来计算变焦倍率
        val mainCamera = availableCameras.find {
            it.lensType == if (currentCamera.lensType == LensType.FRONT) LensType.FRONT else LensType.BACK_MAIN
        } ?: return

        val targetZoom = CustomFocalLengthValue.toZoomRatio(focalLength, mainCamera, currentCamera) ?: return

        if (CustomFocalLengthValue.isZoomRatio(focalLength)) {
            setZoomRatio(targetZoom)
            PLog.d(
                TAG,
                "Applied default focal length: ${CustomFocalLengthValue.displayText(focalLength)} " +
                        "on current lens ${currentCamera.cameraId} (zoom: $targetZoom)"
            )
            return
        }

        // 找到该变焦倍率下的最佳镜头
        val optimalLens = findOptimalLens(targetZoom, availableCameras, currentCamera.cameraId)
        if (optimalLens != null && optimalLens.cameraId != currentCamera.cameraId) {
            cameraController.switchToCameraId(optimalLens.cameraId)
            setZoomRatioForCamera(targetZoom, optimalLens.cameraId)
        } else {
            setZoomRatio(targetZoom)
        }
        PLog.d(TAG, "Applied default focal length: ${CustomFocalLengthValue.displayText(focalLength)} (zoom: $targetZoom)")
    }

    /**
     * 获取摄像头方向偏移
     * @param cameraId 摄像头 ID
     * @return 旋转偏移角度的 Flow
     */
    fun getCameraOrientationOffset(cameraId: String): Flow<Int> {
        return userPreferencesRepository.userPreferences.map { prefs ->
            prefs.cameraOrientationOffsets[cameraId] ?: 0
        }
    }

    /**
     * 保存图片
     */
    private suspend fun saveImage(
        image: SafeImage,
        captureInfo: CaptureInfo,
        characteristics: CameraCharacteristics?,
        captureResult: CaptureResult?,
        capturePortraitMask: PortraitMaskSnapshot?,
        settings: CaptureSettingsSnapshot,
    ) {
        try {
            PLog.d(TAG, "saveImage started - dimensions: ${image.width}x${image.height}, format: ${image.format}")
            val context = getApplication<Application>()

            // 保存当前配置信息
            val lutIdToSave = settings.lutId
            val aspectRatio = settings.state.aspectRatio
            val frameIdToSave = settings.frameId
            val shouldAutoSave = settings.preferences.autoSaveAfterCapture
            val userPrefs: UserPreferences? = settings.preferences
            val isRawCapture = isRawCaptureFormat(image.format)
            val sharpeningValue = resolveCaptureSharpening(
                isRawCapture = isRawCapture,
                userPrefs = userPrefs,
            )
            val denoiseStrengths = resolveCaptureDenoiseStrengths(
                isRawCapture = isRawCapture,
                userPrefs = userPrefs,
            )
            val noiseReductionValue = denoiseStrengths.editableLuma
            val chromaNoiseReductionValue = denoiseStrengths.editableChroma
            val photoQualityValue = settings.preferences.photoQuality
            val droModeString = settings.preferences.droMode
            val droModeForProcessing =
                RawProcessingPreferences.DROMode.fromPersistedName(droModeString)
            val currentCameraId = settings.cameraId

            // 计算旋转角度
            val sensorOrientation = settings.sensorOrientation
            val lensFacing = settings.lensFacing
            val deviceRotation = settings.deviceRotation

            // 基础旋转角度计算
            val baseRotation = if (lensFacing == CameraCharacteristics.LENS_FACING_FRONT) {
                (sensorOrientation - deviceRotation + 360) % 360
            } else {
                (sensorOrientation + deviceRotation) % 360
            }

            // 获取用户配置的摄像头方向偏移
            val orientationOffset = userPrefs?.cameraOrientationOffsets?.get(currentCameraId) ?: 0

            // 应用方向偏移
            val rotation = (baseRotation + orientationOffset) % 360

            val shouldMirror = lensFacing == CameraCharacteristics.LENS_FACING_FRONT &&
                    settings.preferences.mirrorFrontCamera

            val aperture = if (settings.state.isVirtualApertureEnabled) settings.state.virtualAperture else null
            val baselineTarget = if (isRawCapture) {
                BaselineColorCorrectionTarget.RAW
            } else {
                null
            }
            val rawToneMappingParameters = resolveCaptureRawToneMappingParameters(userPrefs)
            val defaultHdrEffectEnabled = defaultHdrEffectEnabled(
                hasEmbeddedGainmap = false,
                userPrefs = userPrefs,
            )
            val baselineMetadata = resolveBaselineMetadata(baselineTarget, userPrefs)
            val effectiveRawAutoExposure = resolveEffectiveRawAutoExposure()
            val spectralFilmSettings = resolveRawSpectralFilmSettings(userPrefs)
            val captureExposureBias = settings.state.exposureBias
            val captureExposureCompensationEv = captureInfo.exposureCompensation ?: 0f

            // 创建统一的 PhotoMetadata，包含编辑配置和拍摄信息
            val metadata = MediaMetadata(
                lutId = lutIdToSave,
                frameId = frameIdToSave,
                colorRecipeParams = settings.recipe,
                baselineTarget = baselineMetadata?.first,
                baselineLutId = baselineMetadata?.second,
                baselineColorRecipeParams = baselineMetadata?.third,
                sharpening = sharpeningValue,
                noiseReduction = noiseReductionValue,
                chromaNoiseReduction = chromaNoiseReductionValue,
                rawDenoiseValue = denoiseStrengths.bakedLuma,
                rawChromaDenoiseValue = denoiseStrengths.bakedChroma,
                captureNoiseReductionLevel = settings.state.nrLevel,
                rawDcpId = userPrefs?.rawDcpIdForLens(currentCameraId),
                rawHncsProfileId = userPrefs?.rawHncsProfileId,
                rawHncsRenderIntent = userPrefs?.rawHncsRenderIntent
                    ?: HncsRenderIntent.Standard,
                rawHncsFilmCurveMode = userPrefs?.rawHncsFilmCurveMode
                    ?: HncsFilmCurveMode.Standard,
                rawExposureCompensation = userPrefs?.rawExposureCompensation ?: 0f,
                rawAutoExposure = effectiveRawAutoExposure,
                customProperties = RawCaptureExposureCompensationMetadata.write(
                    rawProcessingMetadataProperties(
                        userPrefs, cameraController.getCurrentSensorPhysicalAreaMm2(), state.value.zoomRatio,
                    ),
                    captureExposureCompensationEv,
                ) + settings.sequenceProperties,
                rawHighlightsAdjustment = userPrefs?.rawHighlightsAdjustment ?: 0f,
                rawShadowsAdjustment = userPrefs?.rawShadowsAdjustment ?: 0f,
                rawBlackPointCorrection = userPrefs?.rawBlackPointCorrection ?: 0f,
                rawWhitePointCorrection = userPrefs?.rawWhitePointCorrection ?: 0f,
                rawLensShadingCorrectionEnabled = userPrefs?.rawLensShadingCorrectionEnabled,
                rawBlackLevelMode = userPrefs?.rawBlackLevelModes?.get(currentCameraId) ?: "Default",
                rawCustomBlackLevel = userPrefs?.rawCustomBlackLevels?.get(currentCameraId) ?: 0f,
                rawWhiteLevelMode = userPrefs?.rawWhiteLevelModes?.get(currentCameraId)
                    ?: RawWhiteLevelCorrection.MODE_DEFAULT,
                rawCustomWhiteLevel = userPrefs?.rawCustomWhiteLevels?.get(currentCameraId) ?: 0f,
                rawCfaCorrectionMode = userPrefs?.rawCfaCorrectionModes?.get(currentCameraId) ?: RawCfaCorrection.MODE_DEFAULT,
                cameraId = currentCameraId,
                rawBlackBorderCrop = settings.blackBorderCrop,
                rawRenderingEngine = resolveCaptureRawRenderingEngine(userPrefs),
                rawToneMappingParameters = rawToneMappingParameters,
                rawOutputUpscaleMode = userPrefs?.rawOutputUpscaleMode ?: RawOutputUpscaleMode.DEFAULT,
                spectralFilmStock = spectralFilmSettings.stock,
                spectralFilmPrint = spectralFilmSettings.print,
                spectralFilmCDensityGain = spectralFilmSettings.tuning.cDensityGain,
                spectralFilmMDensityGain = spectralFilmSettings.tuning.mDensityGain,
                spectralFilmYDensityGain = spectralFilmSettings.tuning.yDensityGain,
                fotufilmDocument = resolveRawFotufilmDocument(userPrefs)?.toJson(),
                width = image.width,
                height = image.height,
                ratio = aspectRatio,
                rotation = rotation,
                deviceModel = DeviceUtil.model,
                brand = captureInfo.make,
                dateTaken = captureInfo.captureTime,
                latitude = captureInfo.latitude,
                longitude = captureInfo.longitude,
                altitude = captureInfo.altitude,
                iso = captureInfo.iso,
                shutterSpeed = captureInfo.formatExposureTime(),
                focalLength = captureInfo.formatFocalLength(),
                focalLength35mm = captureInfo.formatFocalLength35mm(),
                aperture = captureInfo.formatAperture(),
                exposureBias = captureExposureBias,
                droMode = droModeString,
                isMirrored = shouldMirror,
                colorSpace = captureInfo.colorSpace,
                computationalAperture = aperture,
                focusPointX = settings.state.focusPoint?.first,
                focusPointY = settings.state.focusPoint?.second,
                manualHdrEffectEnabled = defaultHdrEffectEnabled,
                captureMode = when { settings.sequenceProperties.containsKey("addMeSessionId") -> "add_me_source"; settings.sequenceProperties["advancedPrivateSource"] == "true" -> "advanced_source"; else -> null },
            )

            val livePhotoVideoDeferred = settings.livePhotoVideo

            val resolvedCharacteristics = characteristics ?: run {
                PLog.e(TAG, "Failed to save image: camera characteristics unavailable")
                return
            }
            val photoId =
                GalleryManager.preparePhoto(
                    context,
                    metadata,
                    captureResult,
                    settings.displayThumbnail,
                    settings.state.useLivePhoto,
                    1.0f,
                    includeCropRegionInOutputSize = shouldIncludeCropRegionInOutputSize(image.format),
                    photoId = settings.photoId,
                )
            if (photoId == null) {
                PLog.e(TAG, "Failed to save image")
                return
            }

            withContext(Dispatchers.IO) {
                GalleryManager.saveVideo(context, photoId, livePhotoVideoDeferred)

                GalleryManager.savePhoto(
                    context,
                    photoId,
                    image,
                    settings.originalThumbnail,
                    rotation,
                    aspectRatio,
                    resolvedCharacteristics,
                    captureResult,
                    shouldAutoSave,
                    contentRepository.photoProcessor,
                    sharpeningValue,
                    noiseReductionValue,
                    chromaNoiseReductionValue,
                    photoQualityValue,
                    exposureBias = captureExposureBias,
                    captureExposureCompensationEv = captureExposureCompensationEv,
                    exportDngWithRawExport = settings.preferences.exportDngWithRawExport,
                    capturePortraitMask = capturePortraitMask,
                    engineRenderer = settings.enginePlan?.let { plan ->
                        { bitmap: Bitmap -> contentRepository.imageProcessor.renderEngineBitmap(bitmap, plan) }
                    },
                )
            }
            if (settings.sequenceProperties.isNotEmpty()) {
                check(GalleryManager.getOriginalImageFile(context, photoId)?.length()?.let { it > 0L } == true) {
                    context.getString(R.string.advanced_source_original_missing)
                }
            }
            PLog.d(TAG, "Image saved: $photoId, LUT: $lutIdToSave, Frame: $frameIdToSave")
            markCapturedThumbnailSaved(settings, photoId)
            if (!settings.sequenceProperties.containsKey("addMeSessionId") && settings.sequenceProperties["advancedPrivateSource"] != "true") _imageSavedEvent.emit(photoId)
        } catch (e: Exception) {
            PLog.e(TAG, "Failed to save image", e)
            if (settings.sequenceProperties.isNotEmpty()) throw e
        } finally {
            image.close()
        }
    }

    private suspend fun saveVideoSnapshot(bitmap: Bitmap) {
        savePreviewBitmapCapture(
            bitmap = bitmap,
            metadataCaptureMode = "video_snapshot",
            ratio = mapVideoAspectRatioToPhotoAspectRatio(state.value.videoConfig.aspectRatio)
        )
    }

    private suspend fun savePreviewBitmapCapture(
        bitmap: Bitmap,
        metadataCaptureMode: String,
        ratio: AspectRatio?,
        sequenceProperties: Map<String, String> = emptyMap(),
        capturedAtMs: Long? = null,
    ): String? {
        try {
            val context = getApplication<Application>()
            val currentState = state.value
            val userPrefs = userPreferences.value
            val frozenLutId = currentLutId.value
            val frozenFrameId = currentFrameId
            val frozenRecipe = getMergedRecipeParams()
            val shouldAutoSave = userPrefs.autoSaveAfterCapture
            val sharpeningValue = 0f
            val noiseReductionValue = 0f
            val chromaNoiseReductionValue = 0f
            val photoQualityValue = userPrefs.photoQuality
            val shouldMirror = cameraController.getLensFacing() == CameraCharacteristics.LENS_FACING_FRONT &&
                    (userPrefs?.mirrorFrontCamera ?: true)
            val currentCameraId = cameraController.getCurrentCameraId()
            val effectiveRawAutoExposure = resolveEffectiveRawAutoExposure()
            val spectralFilmSettings = resolveRawSpectralFilmSettings(userPrefs)
            val captureInfo = cameraController.rebuildCaptureInfo(
                result = null,
                imageWidth = bitmap.width,
                imageHeight = bitmap.height,
                latitude = currentState.latitude,
                longitude = currentState.longitude
            )
            val computationalAperture = if (currentState.isVirtualApertureEnabled) {
                currentState.virtualAperture
            } else {
                null
            }

            val metadata = MediaMetadata(
                lutId = frozenLutId,
                frameId = frozenFrameId,
                colorRecipeParams = frozenRecipe,
                sharpening = sharpeningValue,
                noiseReduction = noiseReductionValue,
                chromaNoiseReduction = chromaNoiseReductionValue,
                captureNoiseReductionLevel = currentState.nrLevel,
                rawDcpId = userPrefs?.rawDcpIdForLens(currentCameraId),
                rawHncsProfileId = userPrefs?.rawHncsProfileId,
                rawHncsRenderIntent = userPrefs?.rawHncsRenderIntent
                    ?: HncsRenderIntent.Standard,
                rawHncsFilmCurveMode = userPrefs?.rawHncsFilmCurveMode
                    ?: HncsFilmCurveMode.Standard,
                rawExposureCompensation = userPrefs?.rawExposureCompensation ?: 0f,
                rawAutoExposure = effectiveRawAutoExposure,
                customProperties = rawProcessingMetadataProperties(
                    userPrefs, cameraController.getCurrentSensorPhysicalAreaMm2(), state.value.zoomRatio,
                ) + sequenceProperties,
                rawHighlightsAdjustment = userPrefs?.rawHighlightsAdjustment ?: 0f,
                rawShadowsAdjustment = userPrefs?.rawShadowsAdjustment ?: 0f,
                rawBlackPointCorrection = userPrefs?.rawBlackPointCorrection ?: 0f,
                rawWhitePointCorrection = userPrefs?.rawWhitePointCorrection ?: 0f,
                rawLensShadingCorrectionEnabled = userPrefs?.rawLensShadingCorrectionEnabled,
                rawBlackLevelMode = userPrefs?.rawBlackLevelModes?.get(currentCameraId) ?: "Default",
                rawCustomBlackLevel = userPrefs?.rawCustomBlackLevels?.get(currentCameraId) ?: 0f,
                rawWhiteLevelMode = userPrefs?.rawWhiteLevelModes?.get(currentCameraId)
                    ?: RawWhiteLevelCorrection.MODE_DEFAULT,
                rawCustomWhiteLevel = userPrefs?.rawCustomWhiteLevels?.get(currentCameraId) ?: 0f,
                rawCfaCorrectionMode = userPrefs?.rawCfaCorrectionModes?.get(currentCameraId) ?: RawCfaCorrection.MODE_DEFAULT,
                cameraId = currentCameraId,
                rawBlackBorderCrop = currentRawBlackBorderCrop(),
                rawRenderingEngine = resolveCaptureRawRenderingEngine(userPrefs),
                rawToneMappingParameters = resolveCaptureRawToneMappingParameters(userPrefs),
                spectralFilmStock = spectralFilmSettings.stock,
                spectralFilmPrint = spectralFilmSettings.print,
                spectralFilmCDensityGain = spectralFilmSettings.tuning.cDensityGain,
                spectralFilmMDensityGain = spectralFilmSettings.tuning.mDensityGain,
                spectralFilmYDensityGain = spectralFilmSettings.tuning.yDensityGain,
                fotufilmDocument = resolveRawFotufilmDocument(userPrefs)?.toJson(),
                width = bitmap.width,
                height = bitmap.height,
                ratio = ratio,
                rotation = 0,
                deviceModel = DeviceUtil.model,
                brand = captureInfo.make,
                dateTaken = capturedAtMs ?: captureInfo.captureTime,
                latitude = captureInfo.latitude,
                longitude = captureInfo.longitude,
                altitude = captureInfo.altitude,
                iso = captureInfo.iso.takeUnless { sequenceProperties["temporalPreview"] == "true" },
                shutterSpeed = captureInfo.formatExposureTime().takeUnless { sequenceProperties["temporalPreview"] == "true" },
                focalLength = captureInfo.formatFocalLength(),
                focalLength35mm = captureInfo.formatFocalLength35mm(),
                aperture = captureInfo.formatAperture(),
                exposureBias = currentState.exposureBias,
                droMode = droMode.value,
                isMirrored = shouldMirror,
                colorSpace = captureInfo.colorSpace,
                computationalAperture = computationalAperture,
                focusPointX = currentState.focusPoint?.first,
                focusPointY = currentState.focusPoint?.second,
                captureMode = metadataCaptureMode
            )

            val photoId = withContext(NonCancellable + Dispatchers.IO) {
                val id = UUID.randomUUID().toString()
                try {
                    check(GalleryManager.preparePhoto(context, metadata, null, bitmap, false, 1f,
                        includeCropRegionInOutputSize = false, photoId = id) != null) { "Cannot prepare preview output" }
                    GalleryManager.saveBitmapPhoto(context, id, bitmap, shouldAutoSave,
                        contentRepository.photoProcessor, sharpeningValue, noiseReductionValue,
                        chromaNoiseReductionValue, photoQualityValue)
                    check(GalleryManager.getOriginalImageFile(context, id)?.length()?.let { it > 0L } == true) { "Preview output was not saved" }
                    id
                } catch (failure: Throwable) {
                    GalleryManager.deletePhotoOnly(context, id)
                    throw failure
                }
            }
            PLog.d(TAG, "Preview bitmap capture saved: $photoId, mode=$metadataCaptureMode")
            _imageSavedEvent.emit(photoId)
            return photoId
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            PLog.e(TAG, "Failed to save preview bitmap capture: $metadataCaptureMode", e)
            return null
        }
    }

    private fun mapVideoAspectRatioToPhotoAspectRatio(aspectRatio: VideoAspectRatio): AspectRatio? {
        return when (aspectRatio) {
            VideoAspectRatio.RATIO_16_9 -> AspectRatio.RATIO_16_9
            else -> null
        }
    }

    private fun rawStackFrame(
        image: SafeImage,
        captureResult: CaptureResult?,
        metadata: CapturedFrameMetadata?,
    ): RawStackFrame {
        return RawStackFrame(
            image = image,
            sensorTimestampNs = metadata?.sensorTimestampNs ?: image.timestamp,
            frameNumber = metadata?.frameNumber ?: -1L,
            exposureTimeNs = metadata?.exposureTimeNs
                ?: captureResult?.get(CaptureResult.SENSOR_EXPOSURE_TIME)
                ?: 0L,
            sensitivityIso = metadata?.sensitivityIso
                ?: captureResult?.get(CaptureResult.SENSOR_SENSITIVITY)
                ?: 0,
            minimumSensitivityIso = metadata?.minimumSensitivityIso ?: 0,
            maximumAnalogSensitivityIso = metadata?.maximumAnalogSensitivityIso ?: 0,
            exposureProduct = metadata?.exposureProduct
                ?: captureResult?.let(::captureExposureProduct)
                ?: 1.0,
            desiredExposureProduct = metadata?.desiredExposureProduct
                ?: captureResult?.let { result ->
                    RawExposureMath.productOrNull(
                        result.request.get(android.hardware.camera2.CaptureRequest.SENSOR_EXPOSURE_TIME),
                        result.request.get(android.hardware.camera2.CaptureRequest.SENSOR_SENSITIVITY),
                    )
                },
            focusDistanceDiopters = metadata?.focusDistanceDiopters
                ?: captureResult?.get(CaptureResult.LENS_FOCUS_DISTANCE)
                ?: Float.NaN,
            lensState = metadata?.lensState ?: captureResult?.get(CaptureResult.LENS_STATE),
            rollingShutterSkewNs = metadata?.rollingShutterSkewNs
                ?: captureResult?.get(CaptureResult.SENSOR_ROLLING_SHUTTER_SKEW),
            gyroWindow = metadata?.gyroWindow,
            channelNoiseProfile = metadata?.channelNoiseProfile,
            dynamicBlackLevelByCfaPosition = metadata
                ?.dynamicBlackLevelByCfaPosition
                ?.copyOf()
                ?: captureResult
                    ?.get(CaptureResult.SENSOR_DYNAMIC_BLACK_LEVEL)
                    ?.takeIf { it.size >= 4 }
                    ?.copyOf(4),
            role = when (
                metadata?.multiFrameCaptureRole
                    ?: (captureResult?.request?.tag as? MultiFrameCaptureRole)
            ) {
                MultiFrameCaptureRole.SHORT -> RawBurstFrameRole.HIGHLIGHT_SHORT
                MultiFrameCaptureRole.LONG -> RawBurstFrameRole.SHADOW_LONG
                else -> RawBurstFrameRole.NORMAL
            },
        )
    }

    private suspend fun processStacking(
        frames: List<RawStackFrame>,
        captureInfo: CaptureInfo,
        characteristics: CameraCharacteristics?,
        captureResult: CaptureResult?,
        rawMaxHdrFusionEnabled: Boolean,
        rawMaxMode: MgcRawMaxMode,
        capturePortraitMask: PortraitMaskSnapshot?,
        settings: CaptureSettingsSnapshot,
    ) {
        try {
            val images = frames.map { it.image }
            PLog.d(TAG, "processStacking started - image size ${images.size}")
            val context = getApplication<Application>()

            // 保存当前配置信息
            val lutIdToSave = settings.lutId
            val aspectRatio = settings.state.aspectRatio
            val frameIdToSave = settings.frameId
            val shouldAutoSave = settings.preferences.autoSaveAfterCapture
            val isRawStack = images.firstOrNull()?.format?.let(::isRawCaptureFormat) == true
            val userPrefs: UserPreferences? = settings.preferences
            val sharpeningValue = resolveCaptureSharpening(
                isRawCapture = isRawStack,
                userPrefs = userPrefs,
            )
            val denoiseStrengths = resolveCaptureDenoiseStrengths(
                isRawCapture = isRawStack,
                userPrefs = userPrefs,
            )
            val noiseReductionValue = denoiseStrengths.editableLuma
            val chromaNoiseReductionValue = denoiseStrengths.editableChroma
            val photoQualityValue = settings.preferences.photoQuality
            val droModeString = settings.preferences.droMode
            val currentCameraId = settings.cameraId

            // 计算旋转角度
            val sensorOrientation = settings.sensorOrientation
            val lensFacing = settings.lensFacing
            val deviceRotation = settings.deviceRotation

            // 基础旋转角度计算
            val baseRotation = if (lensFacing == CameraCharacteristics.LENS_FACING_FRONT) {
                (sensorOrientation - deviceRotation + 360) % 360
            } else {
                (sensorOrientation + deviceRotation) % 360
            }

            val shouldMirror = lensFacing == CameraCharacteristics.LENS_FACING_FRONT &&
                    settings.preferences.mirrorFrontCamera

            // 获取用户配置的摄像头方向偏移
            val orientationOffset = userPrefs?.cameraOrientationOffsets?.get(currentCameraId) ?: 0

            // 应用方向偏移
            val rotation = (baseRotation + orientationOffset) % 360

            val rawSpatialOutputMode = if (isRawStack) {
                rawMaxMode.outputMode
            } else {
                MgcSpatialOutputMode.BAYER
            }
            val rawMaxMergeMethod = if (isRawStack) {
                rawMaxMode.mergeMethod
            } else {
                MgcMergeMethod.SPATIAL_BAYER
            }
            val configuredSuperResScale = when {
                isRawStack && settings.preferences.useRawMax && rawSpatialOutputMode == MgcSpatialOutputMode.RGB ->
                    settings.state.multiFrameOutputScale
                    ?.let(MultiFrameConfig::normalizeOutputScale)
                    ?: MultiFrameConfig.MIN_OUTPUT_SCALE
                isRawStack -> 1f
                else -> MultiFrameConfig.normalizeOutputScale(
                    userPrefs?.jpgMultiFrameDenoiseOutputScale
                        ?: MultiFrameConfig.DEFAULT_SUPER_RESOLUTION_SCALE
                )
            }
            val superResScale = PhotoResolutionPolicy.captureScaleForFinish2x(
                configuredSuperResScale, userPrefs?.photoFinishUpscale == true)
            val useSuperRes = superResScale > MultiFrameConfig.MIN_OUTPUT_SCALE
            if (isRawStack) {
                PLog.i(
                    TAG,
                    "RAWmax mergeMode=${rawMaxMode.name} output layout=${rawSpatialOutputMode.name} " +
                        "outputScale=$superResScale " +
                        "superResolution=${rawSpatialOutputMode == MgcSpatialOutputMode.RGB}",
                )
            }

            val aperture = if (settings.state.isVirtualApertureEnabled) settings.state.virtualAperture else null
            val baselineTarget = if (isRawStack) {
                BaselineColorCorrectionTarget.RAW
            } else {
                null
            }
            val rawToneMappingParameters = resolveCaptureRawToneMappingParameters(userPrefs)
            val defaultHdrEffectEnabled = defaultHdrEffectEnabled(
                hasEmbeddedGainmap = false,
                userPrefs = userPrefs,
            )
            val baselineMetadata = resolveBaselineMetadata(baselineTarget, userPrefs)
            val effectiveRawAutoExposure = resolveEffectiveRawAutoExposure()
            val spectralFilmSettings = resolveRawSpectralFilmSettings(userPrefs)
            val captureExposureBias = settings.state.exposureBias
            val captureExposureCompensationEv = captureInfo.exposureCompensation ?: 0f

            // 创建统一的 PhotoMetadata，包含编辑配置和拍摄信息
            val metadata = MediaMetadata(
                lutId = lutIdToSave,
                frameId = frameIdToSave,
                colorRecipeParams = settings.recipe,
                baselineTarget = baselineMetadata?.first,
                baselineLutId = baselineMetadata?.second,
                baselineColorRecipeParams = baselineMetadata?.third,
                sharpening = sharpeningValue,
                noiseReduction = noiseReductionValue,
                chromaNoiseReduction = chromaNoiseReductionValue,
                rawDenoiseValue = denoiseStrengths.bakedLuma,
                rawChromaDenoiseValue = denoiseStrengths.bakedChroma,
                captureNoiseReductionLevel = settings.state.nrLevel,
                rawDcpId = userPrefs?.rawDcpIdForLens(currentCameraId),
                rawHncsProfileId = userPrefs?.rawHncsProfileId,
                rawHncsRenderIntent = userPrefs?.rawHncsRenderIntent
                    ?: HncsRenderIntent.Standard,
                rawHncsFilmCurveMode = userPrefs?.rawHncsFilmCurveMode
                    ?: HncsFilmCurveMode.Standard,
                rawExposureCompensation = userPrefs?.rawExposureCompensation ?: 0f,
                rawAutoExposure = effectiveRawAutoExposure,
                customProperties = RawCaptureExposureCompensationMetadata.write(
                    rawProcessingMetadataProperties(
                        userPrefs, cameraController.getCurrentSensorPhysicalAreaMm2(), state.value.zoomRatio,
                    ) + mapOf("photoCaptureOutputScale" to superResScale.toString()),
                    captureExposureCompensationEv,
                ),
                rawHighlightsAdjustment = userPrefs?.rawHighlightsAdjustment ?: 0f,
                rawShadowsAdjustment = userPrefs?.rawShadowsAdjustment ?: 0f,
                rawBlackPointCorrection = userPrefs?.rawBlackPointCorrection ?: 0f,
                rawWhitePointCorrection = userPrefs?.rawWhitePointCorrection ?: 0f,
                rawLensShadingCorrectionEnabled = userPrefs?.rawLensShadingCorrectionEnabled,
                rawBlackLevelMode = userPrefs?.rawBlackLevelModes?.get(currentCameraId) ?: "Default",
                rawCustomBlackLevel = userPrefs?.rawCustomBlackLevels?.get(currentCameraId) ?: 0f,
                rawWhiteLevelMode = userPrefs?.rawWhiteLevelModes?.get(currentCameraId)
                    ?: RawWhiteLevelCorrection.MODE_DEFAULT,
                rawCustomWhiteLevel = userPrefs?.rawCustomWhiteLevels?.get(currentCameraId) ?: 0f,
                rawCfaCorrectionMode = userPrefs?.rawCfaCorrectionModes?.get(currentCameraId) ?: RawCfaCorrection.MODE_DEFAULT,
                cameraId = currentCameraId,
                rawBlackBorderCrop = settings.blackBorderCrop,
                rawRenderingEngine = resolveCaptureRawRenderingEngine(userPrefs),
                rawToneMappingParameters = rawToneMappingParameters,
                rawOutputUpscaleMode = userPrefs?.rawOutputUpscaleMode ?: RawOutputUpscaleMode.DEFAULT,
                spectralFilmStock = spectralFilmSettings.stock,
                spectralFilmPrint = spectralFilmSettings.print,
                spectralFilmCDensityGain = spectralFilmSettings.tuning.cDensityGain,
                spectralFilmMDensityGain = spectralFilmSettings.tuning.mDensityGain,
                spectralFilmYDensityGain = spectralFilmSettings.tuning.yDensityGain,
                fotufilmDocument = resolveRawFotufilmDocument(userPrefs)?.toJson(),
                width = MultiFrameConfig.scaledRawOutputDimension(images[0].width, superResScale),
                height = MultiFrameConfig.scaledRawOutputDimension(images[0].height, superResScale),
                ratio = aspectRatio,
                rotation = rotation,
                deviceModel = DeviceUtil.model,
                brand = captureInfo.make,
                dateTaken = captureInfo.captureTime,
                latitude = captureInfo.latitude,
                longitude = captureInfo.longitude,
                altitude = captureInfo.altitude,
                iso = captureInfo.iso,
                shutterSpeed = captureInfo.formatExposureTime(),
                focalLength = captureInfo.formatFocalLength(),
                focalLength35mm = captureInfo.formatFocalLength35mm(),
                aperture = captureInfo.formatAperture(),
                exposureBias = captureExposureBias,
                droMode = droModeString,
                isMirrored = shouldMirror,
                colorSpace = captureInfo.colorSpace,
                computationalAperture = aperture,
                focusPointX = settings.state.focusPoint?.first,
                focusPointY = settings.state.focusPoint?.second,
                manualHdrEffectEnabled = defaultHdrEffectEnabled,
                captureMode = if (isRawStack) null else "jpg_max",
            )

            val livePhotoVideoDeferred = settings.livePhotoVideo

            characteristics ?: return
            val photoId = GalleryManager.preparePhoto(
                context,
                metadata,
                captureResult,
                settings.displayThumbnail,
                settings.state.useLivePhoto,
                superResScale,
                includeCropRegionInOutputSize = images.firstOrNull()?.let {
                    shouldIncludeCropRegionInOutputSize(it.format)
                } ?: false,
                photoId = settings.photoId,
            )
            if (photoId == null) {
                PLog.e(TAG, "Failed to save burst image")
                return
            }

            withContext(Dispatchers.IO) {
                GalleryManager.saveVideo(context, photoId, livePhotoVideoDeferred)

                GalleryManager.saveStackedPhoto(
                    context,
                    photoId,
                    images,
                    rotation,
                    aspectRatio,
                    characteristics,
                    captureResult,
                    shouldAutoSave,
                    contentRepository.photoProcessor,
                    sharpeningValue,
                    noiseReductionValue,
                    chromaNoiseReductionValue,
                    photoQualityValue,
                    useSuperResolution = useSuperRes,
                    superResolutionScale = superResScale,
                    exposureBias = captureExposureBias,
                    captureExposureCompensationEv = captureExposureCompensationEv,
                    exportDngWithRawExport = settings.preferences.exportDngWithRawExport,
                    capturePreviewThumbnail = settings.originalThumbnail,
                    capturePortraitMask = capturePortraitMask,
                    rawStackFrames = frames,
                    rawMaxHdrFusionEnabled = rawMaxHdrFusionEnabled,
                    rawMaxSpatialOutputMode = rawSpatialOutputMode,
                    rawMaxMergeMethod = rawMaxMergeMethod,
                )
            }
            PLog.d(TAG, "Image saved: $photoId, LUT: $lutIdToSave, Frame: $frameIdToSave")
            markCapturedThumbnailSaved(settings, photoId)
            _imageSavedEvent.emit(photoId)
        } catch (e: Exception) {
            PLog.e(TAG, "Failed to save image", e)
        }
    }

    private suspend fun processHdrBracket(
        images: List<SafeImage>,
        captureResults: List<CaptureResult?>,
        zeroEvFrameCount: Int,
        expectedFrameCount: Int,
        captureInfo: CaptureInfo,
        characteristics: CameraCharacteristics?,
        captureResult: CaptureResult?,
        settings: CaptureSettingsSnapshot,
    ) {
        val orderedFrames = orderHdrBracketFramesByTimestamp(images, captureResults)
        val orderedImages = orderedFrames.images
        val orderedCaptureResults = orderedFrames.captureResults
        var imagesHandedToGallery = false
        try {
            if (orderedImages.size != expectedFrameCount) return
            val context = getApplication<Application>()
            val shouldAutoSave = settings.preferences.autoSaveAfterCapture
            val sharpeningValue = 0f
            val noiseReductionValue = 0f
            val chromaNoiseReductionValue = 0f
            val photoQualityValue = settings.preferences.photoQuality
            val baseImage = orderedImages[HDR_BRACKET_ZERO_INDEX]
            val useSuperRes = false
            val superResScale = 1.0f
            val captureMode = "jpg_max"
            val metadataCaptureInfo = captureInfo
            val metadata = buildPhotoMetadata(
                width = (baseImage.width.toFloat() * superResScale).roundToInt(),
                height = (baseImage.height.toFloat() * superResScale).roundToInt(),
                captureInfo = metadataCaptureInfo,
                sharpeningValue = sharpeningValue,
                noiseReductionValue = noiseReductionValue,
                chromaNoiseReductionValue = chromaNoiseReductionValue,
                captureMode = captureMode,
                multipleExposureFrameCount = expectedFrameCount,
                settings = settings,
            )

            val photoId = GalleryManager.preparePhoto(
                context,
                metadata,
                null,
                settings.displayThumbnail,
                false,
                superResScale,
                includeCropRegionInOutputSize = false,
                photoId = settings.photoId,
            ) ?: return

            val aspectRatio = metadata.ratio ?: settings.state.aspectRatio
            val colorSpace = android.graphics.ColorSpace.get(metadataCaptureInfo.colorSpace)
            imagesHandedToGallery = true
            withContext(Dispatchers.IO) {
                var fusedBitmap: Bitmap? = null
                try {
                    fusedBitmap = GalleryManager.composeHdrBracketPhoto(
                        images = orderedImages,
                        captureResults = orderedCaptureResults,
                        zeroEvFrameCount = zeroEvFrameCount,
                        rotation = metadata.rotation,
                        aspectRatio = aspectRatio,
                        shouldMirror = metadata.isMirrored,
                        useSuperResolution = useSuperRes,
                        colorSpace = colorSpace
                    )
                    val outputBitmap = fusedBitmap ?: return@withContext

                    GalleryManager.saveBitmapPhoto(
                        context,
                        photoId,
                        outputBitmap,
                        shouldAutoSave,
                        contentRepository.photoProcessor,
                        sharpeningValue,
                        noiseReductionValue,
                        chromaNoiseReductionValue,
                        photoQualityValue
                    )
                    PLog.d(TAG, "HDR bracket image saved: $photoId, characteristics=${characteristics != null}, result=${captureResult != null}")
                } catch (e: Exception) {
                    PLog.e(TAG, "Failed to process HDR bracket", e)
                } finally {
                    fusedBitmap?.takeIf { !it.isRecycled }?.recycle()
                }
            }
            PLog.d(TAG, "Image saved: $photoId, HDR mode: $captureMode")
            markCapturedThumbnailSaved(settings, photoId)
            _imageSavedEvent.emit(photoId)
        } catch (e: Exception) {
            PLog.e(TAG, "Failed to process HDR bracket", e)
        } finally {
            if (!imagesHandedToGallery) {
                orderedImages.forEach { it.close() }
            }
        }
    }

    private fun orderHdrBracketFramesByTimestamp(
        images: List<SafeImage>,
        captureResults: List<CaptureResult?>
    ): HdrBracketFrameOrder {
        val frames = images.mapIndexed { index, image ->
            val result = captureResults.getOrNull(index)
            HdrBracketFrame(
                image = image,
                captureResult = result,
                originalIndex = index,
                timestamp = result?.get(CaptureResult.SENSOR_TIMESTAMP) ?: image.timestamp
            )
        }
        val sortedFrames = frames.sortedWith(
            compareBy<HdrBracketFrame> { it.timestamp }
                .thenBy { it.originalIndex }
        )
        val sortedOrder = sortedFrames.map { it.originalIndex }
        val originalOrder = frames.map { it.originalIndex }
        if (sortedOrder != originalOrder) {
            PLog.d(
                TAG,
                "HDR bracket frames reordered by timestamp: " +
                        sortedFrames.joinToString { "${it.originalIndex}:${it.timestamp}" }
            )
        }
        return HdrBracketFrameOrder(
            images = sortedFrames.map { it.image },
            captureResults = sortedFrames.map { it.captureResult }
        )
    }

    private fun captureExposureProduct(result: CaptureResult): Double? {
        return RawExposureMath.productOrNull(
            exposureTimeNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME),
            sensitivityIso = result.get(CaptureResult.SENSOR_SENSITIVITY),
        )
    }

    private suspend fun prepareBurst(context: Context, photoId: String, image: SafeImage, captureInfo: CaptureInfo, settings: CaptureSettingsSnapshot) {
        // 保存当前配置信息
        val lutIdToSave = settings.lutId
        val aspectRatio = settings.state.aspectRatio
        val frameIdToSave = settings.frameId
        val userPrefs: UserPreferences? = settings.preferences
        val isRawCapture = isRawCaptureFormat(image.format)
        val sharpeningValue = resolveCaptureSharpening(
            isRawCapture = isRawCapture,
            userPrefs = userPrefs,
        )
        val denoiseStrengths = resolveCaptureDenoiseStrengths(
            isRawCapture = isRawCapture,
            userPrefs = userPrefs,
        )
        val noiseReductionValue = denoiseStrengths.editableLuma
        val chromaNoiseReductionValue = denoiseStrengths.editableChroma
        val currentCameraId = settings.cameraId

        // 计算旋转角度
        val sensorOrientation = settings.sensorOrientation
        val lensFacing = settings.lensFacing
        val deviceRotation = settings.deviceRotation

        // 基础旋转角度计算
        val baseRotation = if (lensFacing == CameraCharacteristics.LENS_FACING_FRONT) {
            (sensorOrientation - deviceRotation + 360) % 360
        } else {
            (sensorOrientation + deviceRotation) % 360
        }

        // 获取用户配置的摄像头方向偏移
        val orientationOffset = userPrefs?.cameraOrientationOffsets?.get(currentCameraId) ?: 0

        // 应用方向偏移
        val rotation = (baseRotation + orientationOffset) % 360

        val shouldMirror = lensFacing == CameraCharacteristics.LENS_FACING_FRONT &&
                settings.preferences.mirrorFrontCamera

        val aperture = if (settings.state.isVirtualApertureEnabled) settings.state.virtualAperture else null
        val baselineTarget = if (isRawCapture) {
            BaselineColorCorrectionTarget.RAW
        } else {
            null
        }
        val rawToneMappingParameters = resolveCaptureRawToneMappingParameters(userPrefs)
        val defaultHdrEffectEnabled = defaultHdrEffectEnabled(
            hasEmbeddedGainmap = false,
            userPrefs = userPrefs,
        )
        val baselineMetadata = resolveBaselineMetadata(baselineTarget, userPrefs)
        val effectiveRawAutoExposure = resolveEffectiveRawAutoExposure()
        val spectralFilmSettings = resolveRawSpectralFilmSettings(userPrefs)

        // 创建统一的 PhotoMetadata，包含编辑配置和拍摄信息
        val metadata = MediaMetadata(
            lutId = lutIdToSave,
            frameId = frameIdToSave,
            colorRecipeParams = settings.recipe,
            baselineTarget = baselineMetadata?.first,
            baselineLutId = baselineMetadata?.second,
            baselineColorRecipeParams = baselineMetadata?.third,
            sharpening = sharpeningValue,
            noiseReduction = noiseReductionValue,
            chromaNoiseReduction = chromaNoiseReductionValue,
            rawDenoiseValue = denoiseStrengths.bakedLuma,
            rawChromaDenoiseValue = denoiseStrengths.bakedChroma,
            captureNoiseReductionLevel = settings.state.nrLevel,
            rawDcpId = userPrefs?.rawDcpIdForLens(currentCameraId),
            rawHncsProfileId = userPrefs?.rawHncsProfileId,
            rawHncsRenderIntent = userPrefs?.rawHncsRenderIntent
                ?: HncsRenderIntent.Standard,
            rawHncsFilmCurveMode = userPrefs?.rawHncsFilmCurveMode
                ?: HncsFilmCurveMode.Standard,
            rawExposureCompensation = userPrefs?.rawExposureCompensation ?: 0f,
            rawAutoExposure = effectiveRawAutoExposure,
            customProperties = rawProcessingMetadataProperties(
                userPrefs, cameraController.getCurrentSensorPhysicalAreaMm2(), state.value.zoomRatio,
            ) + settings.sequenceProperties,
            rawHighlightsAdjustment = userPrefs?.rawHighlightsAdjustment ?: 0f,
            rawShadowsAdjustment = userPrefs?.rawShadowsAdjustment ?: 0f,
            rawBlackPointCorrection = userPrefs?.rawBlackPointCorrection ?: 0f,
            rawWhitePointCorrection = userPrefs?.rawWhitePointCorrection ?: 0f,
            rawLensShadingCorrectionEnabled = userPrefs?.rawLensShadingCorrectionEnabled,
            rawBlackLevelMode = userPrefs?.rawBlackLevelModes?.get(currentCameraId) ?: "Default",
            rawCustomBlackLevel = userPrefs?.rawCustomBlackLevels?.get(currentCameraId) ?: 0f,
            rawWhiteLevelMode = userPrefs?.rawWhiteLevelModes?.get(currentCameraId)
                ?: RawWhiteLevelCorrection.MODE_DEFAULT,
            rawCustomWhiteLevel = userPrefs?.rawCustomWhiteLevels?.get(currentCameraId) ?: 0f,
            rawCfaCorrectionMode = userPrefs?.rawCfaCorrectionModes?.get(currentCameraId) ?: RawCfaCorrection.MODE_DEFAULT,
            cameraId = currentCameraId,
            rawBlackBorderCrop = settings.blackBorderCrop,
            rawRenderingEngine = resolveCaptureRawRenderingEngine(userPrefs),
            rawToneMappingParameters = rawToneMappingParameters,
            rawOutputUpscaleMode = userPrefs?.rawOutputUpscaleMode ?: RawOutputUpscaleMode.DEFAULT,
            spectralFilmStock = spectralFilmSettings.stock,
            spectralFilmPrint = spectralFilmSettings.print,
            spectralFilmCDensityGain = spectralFilmSettings.tuning.cDensityGain,
            spectralFilmMDensityGain = spectralFilmSettings.tuning.mDensityGain,
            spectralFilmYDensityGain = spectralFilmSettings.tuning.yDensityGain,
            fotufilmDocument = resolveRawFotufilmDocument(userPrefs)?.toJson(),
            width = image.width,
            height = image.height,
            ratio = aspectRatio,
            rotation = rotation,
            deviceModel = DeviceUtil.model,
            brand = captureInfo.make,
            dateTaken = captureInfo.captureTime,
            latitude = captureInfo.latitude,
            longitude = captureInfo.longitude,
            altitude = captureInfo.altitude,
            iso = captureInfo.iso,
            shutterSpeed = captureInfo.formatExposureTime(),
            focalLength = captureInfo.formatFocalLength(),
            focalLength35mm = captureInfo.formatFocalLength35mm(),
            aperture = captureInfo.formatAperture(),
            isMirrored = shouldMirror,
            colorSpace = captureInfo.colorSpace,
            computationalAperture = aperture,
            focusPointX = settings.state.focusPoint?.first,
            focusPointY = settings.state.focusPoint?.second,
            manualHdrEffectEnabled = defaultHdrEffectEnabled
        )

        GalleryManager.preparePhoto(
            context,
            metadata,
            null,
            settings.displayThumbnail,
            false,
            1.0f,
            photoId = photoId
        )
    }

    fun getAvailableFocalLengths(): List<Float> {
        val currentState = state.value
        val availableCameras = currentState.availableCameras
        if (availableCameras.isEmpty()) return emptyList()

        val mainCamera = availableCameras.find {
            it.lensType == LensType.BACK_MAIN
        } ?: availableCameras.firstOrNull { it.lensFacing == CameraCharacteristics.LENS_FACING_BACK }
        ?: return emptyList()

        if (mainCamera.focalLength35mmEquivalent <= 0) return emptyList()

        val lensZoomStops = calculateLensZoomStops(availableCameras, mainCamera)
        val stops = lensZoomStops.toMutableList()
        addDefaultMinimumZoomStop(stops, lensZoomStops, mainCamera)

        return stops
            .map { it * mainCamera.focalLength35mmEquivalent }
            .distinctBy { it.roundToInt() }
            .sorted()
    }

    /**
     * 设置背景图
     */
    fun setBackgroundImage(image: String) {
        viewModelScope.launch {
            userPreferencesRepository.saveBackgroundImage(image)
        }
    }

    /**
     * 保存从外部选择的背景图
     */
    fun saveCustomBackgroundImage(uri: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val context = getApplication<Application>()
                    val inputStream = context.contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        val backgroundDir = File(context.filesDir, "backgrounds")
                        if (!backgroundDir.exists()) {
                            backgroundDir.mkdirs()
                        }
                        val fileName = "custom_bg_${System.currentTimeMillis()}.jpg"
                        val outputFile = File(backgroundDir, fileName)
                        inputStream.use { input ->
                            outputFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        setBackgroundImage(outputFile.absolutePath)
                    }
                } catch (e: Exception) {
                    PLog.e(TAG, "Failed to save custom background image", e)
                }
            }
        }
    }

    fun setAccentColor(color: Int) {
        viewModelScope.launch {
            userPreferencesRepository.saveAccentColor(color)
        }
    }

    fun setCaptureButtonStyle(style: CaptureButtonStyle) {
        viewModelScope.launch {
            userPreferencesRepository.saveCaptureButtonStyle(style)
        }
    }

    fun setCaptureButtonColor(color: Int) {
        viewModelScope.launch {
            userPreferencesRepository.saveCaptureButtonColor(color)
        }
    }

    fun saveCustomCaptureButtonImage(uri: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val context = getApplication<Application>()
                val imageDirectory = File(context.filesDir, "capture_buttons")
                val outputFile = File(
                    imageDirectory,
                    "capture_button_${UUID.randomUUID()}.image"
                )
                try {
                    if (!imageDirectory.exists() && !imageDirectory.mkdirs()) {
                        throw IllegalStateException(
                            "Unable to create capture button image directory"
                        )
                    }
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        outputFile.outputStream().use(input::copyTo)
                    } ?: throw IllegalArgumentException("Unable to open selected image")

                    val previousPath = userPreferencesRepository.userPreferences
                        .first()
                        .captureButtonImagePath
                    userPreferencesRepository.saveCaptureButtonImage(outputFile.absolutePath)

                    runCatching {
                        previousPath
                            ?.let(::File)
                            ?.takeIf {
                                it != outputFile &&
                                    it.parentFile?.canonicalFile == imageDirectory.canonicalFile
                            }
                            ?.delete()
                    }.onFailure {
                        PLog.w(TAG, "Failed to remove previous capture button image", it)
                    }
                } catch (e: Exception) {
                    outputFile.delete()
                    PLog.e(TAG, "Failed to save custom capture button image", e)
                }
            }
        }
    }


    fun onShutterAnimationTriggered() {
        _canStartShutterAnimation.value = true
    }

    override fun onCleared() {
        guidedGroupSources.keys.toList().forEach(::discardGuidedGroupSession)
        stopAdvancedCapture()
        super.onCleared()
        cameraController.onEnhancedStabilizationUnavailable = null
        cameraReopenJob?.cancel()
        cameraErrorRecoveryJob?.cancel()
        previewEyeFocusProcessor.close()
        locationManager.stopUpdates()
        cameraController.release()
        contentRepository.lutManager.clearCache()
        contentRepository.frameManager.clearCache()
        shutterSoundPlayer.release()
        videoAudioInputManager.release()

        // Accepted photo jobs retain their own images until the process-wide queue finishes.
        burstImageCount = 0
        multipleExposureState.previewBitmap?.recycle()
        multipleExposureState.sessionId?.let { GalleryManager.clearMultipleExposureSession(getApplication(), it) }
    }

    /**
     * 设置当前 RAW 动态范围
     */
    fun setDroMode(mode: String) {
        viewModelScope.launch {
            applyCameraFeatureUpdate(
                CameraFeatureUpdate(droMode = SettingValue(mode))
            )
        }
    }

    fun togglePhantomMode() {
        val newMode = !phantomMode.value
        viewModelScope.launch {
            userPreferencesRepository.savePhantomMode(newMode)
        }
    }

    fun setPhantomButtonHidden(hidden: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.savePhantomButtonHidden(hidden)
        }
    }

    fun setLaunchCameraOnPhantomMode(launch: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.saveLaunchCameraOnPhantomMode(launch)
        }
    }

    fun setPhantomPipPreview(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.savePhantomPipPreview(enabled)
        }
    }

    fun setPhantomPipCrop(crop: PhantomPipCrop) {
        viewModelScope.launch {
            userPreferencesRepository.savePhantomPipCrop(crop)
        }
    }

    fun setPhantomSaveAsNew(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.savePhantomSaveAsNew(enabled)
        }
    }

    fun setPhantomFrame(frameId: String?) {
        viewModelScope.launch {
            userPreferencesRepository.savePhantomFrameConfig(frameId)
        }
    }

    fun setDefaultVirtualAperture(aperture: Float) {
        applyDefaultVirtualAperture(aperture)
        viewModelScope.launch {
            userPreferencesRepository.saveDefaultVirtualAperture(aperture)
        }
    }

    private fun applyDefaultVirtualAperture(aperture: Float) {
        val enabled = aperture.isFinite() && aperture > 0f
        setVirtualApertureAuto(enabled)
        if (enabled) {
            setAperture(aperture)
        }
    }

    /**
     * 设置是否启用自拍镜像
     */
    fun setMirrorFrontCamera(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.saveMirrorFrontCamera(enabled)
        }
    }

    /**
     * 设置 Widget 主题
     */
    fun setWidgetTheme(theme: com.hinnka.mycamera.data.WidgetTheme) {
        viewModelScope.launch {
            userPreferencesRepository.saveWidgetTheme(theme)
            // 通知 Widget 更新
            val intent = Intent(
                getApplication<Application>(),
                PhantomWidgetProvider::class.java
            ).apply {
                action = android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
                val ids = android.appwidget.AppWidgetManager.getInstance(getApplication())
                    .getAppWidgetIds(
                        android.content.ComponentName(
                            getApplication(),
                            PhantomWidgetProvider::class.java
                        )
                    )
                putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            getApplication<Application>().sendBroadcast(intent)
        }
    }

    /**
     * 获取 LUT 的 .cube 字符串内容
     */
    suspend fun getLutCubeString(lutId: String): String? = withContext(Dispatchers.IO) {
        val lutInfo = contentRepository.lutManager.getLutInfo(lutId) ?: return@withContext null
        val lutConfig = contentRepository.lutManager.loadLut(lutId) ?: return@withContext null

        val floatBuffer = lutConfig.toFloatBuffer()
        val floatArray = FloatArray(floatBuffer.capacity())
        floatBuffer.position(0)
        floatBuffer.get(floatArray)

        LutGenerator.exportToCubeString(floatArray, lutConfig.size, lutInfo.getName())
    }

    /**
     * 将 LUT（含色彩配方）导出为 .plut v4 字节数组
     * 若该 LUT 没有色彩配方则导出为标准 v3 格式
     */
    suspend fun exportLutToPlut(lutId: String): ByteArray? = withContext(Dispatchers.IO) {
        val lutConfig = contentRepository.lutManager.loadLut(lutId) ?: return@withContext null
        val recipe = contentRepository.lutManager.loadColorRecipeParams(lutId)
        val recipeJson = if (!recipe.isDefault()) recipe.toJson() else null

        val outputStream = java.io.ByteArrayOutputStream()
        LutConverter.exportToPlut(lutConfig, outputStream, recipeJson)
        outputStream.toByteArray()
    }

    /**
     * 导出原始无损 .cube 字节数组
     */
    suspend fun exportLutToCube(lutId: String): ByteArray? = withContext(Dispatchers.IO) {
        getLutCubeString(lutId)?.toByteArray(Charsets.UTF_8)
    }

    /**
     * 将 LUT 和色彩配方永久烘焙并导出为标准 .cube 字节数组
     */
    suspend fun exportBakedLutToCube(lutId: String): ByteArray? = withContext(Dispatchers.IO) {
        val lutConfig = contentRepository.lutManager.loadLut(lutId) ?: return@withContext null
        val recipe = contentRepository.lutManager.loadColorRecipeParams(lutId)

        try {
            val lutInfo = contentRepository.lutManager.getLutInfo(lutId)
            val name = lutInfo?.getName() ?: "BakedLUT"
            BakedLutExporter.exportBakedCube(getApplication(), lutConfig, recipe, name)
        } catch (e: Exception) {
            PLog.e("CameraViewModel", "Failed to bake LUT to cube", e)
            null
        }
    }

    /**
     * 将 LUT 和色彩配方永久烘焙并导出为 HALD CLUT .png 字节数组
     */
    suspend fun exportBakedLutToHaldPng(lutId: String): ByteArray? = withContext(Dispatchers.IO) {
        val lutConfig = contentRepository.lutManager.loadLut(lutId) ?: return@withContext null
        val recipe = contentRepository.lutManager.loadColorRecipeParams(lutId)

        try {
            BakedLutExporter.exportBakedHaldPng(getApplication(), lutConfig, recipe)
        } catch (e: Exception) {
            PLog.e("CameraViewModel", "Failed to bake LUT to HALD PNG", e)
            null
        }
    }

    suspend fun exportFrameToJson(frame: FrameInfo): ByteArray? = withContext(Dispatchers.IO) {
        contentRepository.getCustomImportManager().exportFrameJson(frame)
    }

    /**
     * 从导入的 .plut URI 中提取嵌入的色彩配方并保存到指定 LUT（仅 v4 文件含有配方）
     */
    suspend fun extractAndSaveColorRecipeFromPlut(lutId: String, uri: Uri) = withContext(Dispatchers.IO) {
        try {
            val recipeJson = getApplication<Application>().contentResolver
                .openInputStream(uri)?.use { LutConverter.extractRecipeJsonFromPlut(it) }
                ?: return@withContext
            val params = ColorRecipeParams.fromJson(recipeJson)
            contentRepository.lutManager.saveColorRecipeParams(lutId, params)
        } catch (e: Exception) {
            PLog.e("CameraViewModel", "Failed to extract recipe from plut", e)
        }
    }

}

private fun shouldIncludeCropRegionInOutputSize(imageFormat: Int): Boolean {
    return when (imageFormat) {
        ImageFormat.RAW_SENSOR,
        ImageFormat.RAW10,
        ImageFormat.RAW12 -> true

        else -> false
    }
}
