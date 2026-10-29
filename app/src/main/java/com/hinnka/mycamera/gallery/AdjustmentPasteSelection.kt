package com.hinnka.mycamera.gallery

import com.hinnka.mycamera.frame.TextType
import com.hinnka.mycamera.model.ColorRecipeParams

/** Immutable per-operation choices, shared by editor, detail and batch paste. */
data class AdjustmentPasteSelection(
    val color: Boolean = true,
    val detail: Boolean = true,
    val frame: Boolean = false,
    val geometry: Boolean = false,
    val portrait: Boolean = false
) {
    val isEmpty: Boolean get() = !color && !detail && !frame && !geometry && !portrait

    /** Keep target-only metadata (including all video flags) and unselected groups intact. */
    fun merge(target: MediaMetadata, source: MediaMetadata, allowDetail: Boolean,
        targetRecipe: ColorRecipeParams = target.colorRecipeParams ?: ColorRecipeParams.DEFAULT
    ): MediaMetadata {
        var result = target
        val copyDetail = detail && allowDetail
        if (color || copyDetail) {
            val sourceRecipe = source.colorRecipeParams ?: ColorRecipeParams.DEFAULT
            val look = if (color) sourceRecipe else targetRecipe
            val texture = if (copyDetail) sourceRecipe else targetRecipe
            result = result.copy(
                lutId = if (color) source.lutId else target.lutId,
                colorRecipeParams = look.copy(
                    sharpness = texture.sharpness,
                    sharpnessRadius = texture.sharpnessRadius,
                    sharpnessThreshold = texture.sharpnessThreshold,
                    clarity = texture.clarity,
                    noise = texture.noise,
                    lowRes = texture.lowRes,
                    masterCurvePoints = look.masterCurvePoints?.copyOf(),
                    redCurvePoints = look.redCurvePoints?.copyOf(),
                    greenCurvePoints = look.greenCurvePoints?.copyOf(),
                    blueCurvePoints = look.blueCurvePoints?.copyOf()))
        }
        if (detail && allowDetail) result = result.copy(
            sharpening = source.sharpening ?: target.sharpening,
            noiseReduction = source.noiseReduction ?: target.noiseReduction,
            chromaNoiseReduction = source.chromaNoiseReduction ?: target.chromaNoiseReduction)
        if (frame) result = result.copy(frameId = source.frameId,
            customProperties = mergeFrameProperties(target.customProperties, source.customProperties))
        if (geometry) result = result.copy(postCropRegion = source.postCropRegion,
            postRotationDegrees = source.postRotationDegrees,
            postStraightenDegrees = source.postStraightenDegrees,
            postMirrorHorizontal = source.postMirrorHorizontal)
        if (portrait) result = result.copy(computationalAperture = source.computationalAperture,
            computationalBokehStyle = source.computationalBokehStyle,
            focusPointX = source.focusPointX, focusPointY = source.focusPointY)
        return result
    }

    companion object {
        private val FRAME_PROPERTY_KEYS = TextType.entries.mapTo(mutableSetOf()) { it.name } +
            setOf("LOGO", "DEVICE_MODEL_FONT")

        /** Frame overrides share a map with capture provenance: transfer only renderer-owned keys. */
        fun mergeFrameProperties(target: Map<String, String>, source: Map<String, String>): Map<String, String> =
            target.filterKeys { it !in FRAME_PROPERTY_KEYS } +
                source.filterKeys { it in FRAME_PROPERTY_KEYS }
    }
}
