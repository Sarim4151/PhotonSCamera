package com.hinnka.mycamera.lut

import android.opengl.GLES30
import com.hinnka.mycamera.model.ColorRecipeParams
import java.nio.ByteBuffer

internal const val LCH_COLOR_BAND_COUNT = 9

internal data class LchAdjustmentArrays(
    val hue: FloatArray,
    val chroma: FloatArray,
    val lightness: FloatArray,
)

internal object ColorRecipeGl {
    fun bindLggo(program: Int, params: ColorRecipeParams?) {
        GLES30.glUniform3f(GLES30.glGetUniformLocation(program, "uGradeLift"), (params?.gradeLiftRed ?: 0f).let { if (it.isFinite()) it.coerceIn(-0.5f, 0.5f) else 0f }, (params?.gradeLiftGreen ?: 0f).let { if (it.isFinite()) it.coerceIn(-0.5f, 0.5f) else 0f }, (params?.gradeLiftBlue ?: 0f).let { if (it.isFinite()) it.coerceIn(-0.5f, 0.5f) else 0f })
        GLES30.glUniform3f(GLES30.glGetUniformLocation(program, "uGradeGamma"), (params?.gradeGammaRed ?: 0f).let { if (it.isFinite()) it.coerceIn(-0.5f, 0.5f) else 0f }, (params?.gradeGammaGreen ?: 0f).let { if (it.isFinite()) it.coerceIn(-0.5f, 0.5f) else 0f }, (params?.gradeGammaBlue ?: 0f).let { if (it.isFinite()) it.coerceIn(-0.5f, 0.5f) else 0f })
        GLES30.glUniform3f(GLES30.glGetUniformLocation(program, "uGradeGain"), (params?.gradeGainRed ?: 0f).let { if (it.isFinite()) it.coerceIn(-0.5f, 0.5f) else 0f }, (params?.gradeGainGreen ?: 0f).let { if (it.isFinite()) it.coerceIn(-0.5f, 0.5f) else 0f }, (params?.gradeGainBlue ?: 0f).let { if (it.isFinite()) it.coerceIn(-0.5f, 0.5f) else 0f })
        GLES30.glUniform3f(GLES30.glGetUniformLocation(program, "uGradeOffset"), (params?.gradeOffsetRed ?: 0f).let { if (it.isFinite()) it.coerceIn(-0.5f, 0.5f) else 0f }, (params?.gradeOffsetGreen ?: 0f).let { if (it.isFinite()) it.coerceIn(-0.5f, 0.5f) else 0f }, (params?.gradeOffsetBlue ?: 0f).let { if (it.isFinite()) it.coerceIn(-0.5f, 0.5f) else 0f })
    }

    fun lchAdjustments(params: ColorRecipeParams?): LchAdjustmentArrays {
        if (params == null) {
            return LchAdjustmentArrays(
                hue = FloatArray(LCH_COLOR_BAND_COUNT),
                chroma = FloatArray(LCH_COLOR_BAND_COUNT),
                lightness = FloatArray(LCH_COLOR_BAND_COUNT),
            )
        }
        return LchAdjustmentArrays(
            hue = floatArrayOf(
                params.skinHue,
                params.redHue,
                params.orangeHue,
                params.yellowHue,
                params.greenHue,
                params.cyanHue,
                params.blueHue,
                params.purpleHue,
                params.magentaHue,
            ),
            chroma = floatArrayOf(
                params.skinChroma,
                params.redChroma,
                params.orangeChroma,
                params.yellowChroma,
                params.greenChroma,
                params.cyanChroma,
                params.blueChroma,
                params.purpleChroma,
                params.magentaChroma,
            ),
            lightness = floatArrayOf(
                params.skinLightness,
                params.redLightness,
                params.orangeLightness,
                params.yellowLightness,
                params.greenLightness,
                params.cyanLightness,
                params.blueLightness,
                params.purpleLightness,
                params.magentaLightness,
            )
        )
    }

    fun bindLchAdjustments(
        hueLocation: Int,
        chromaLocation: Int,
        lightnessLocation: Int,
        adjustments: LchAdjustmentArrays,
    ) {
        GLES30.glUniform1fv(hueLocation, LCH_COLOR_BAND_COUNT, adjustments.hue, 0)
        GLES30.glUniform1fv(chromaLocation, LCH_COLOR_BAND_COUNT, adjustments.chroma, 0)
        GLES30.glUniform1fv(lightnessLocation, LCH_COLOR_BAND_COUNT, adjustments.lightness, 0)
    }

    fun ensureCurveTextureUploaded(textureId: Int, pendingBuffer: ByteBuffer?): Int {
        var currentTextureId = textureId
        pendingBuffer?.let { buffer ->
            if (currentTextureId == 0) {
                val ids = IntArray(1)
                GLES30.glGenTextures(1, ids, 0)
                currentTextureId = ids[0]
            }
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, currentTextureId)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D,
                0,
                GLES30.GL_RGBA8,
                256,
                1,
                0,
                GLES30.GL_RGBA,
                GLES30.GL_UNSIGNED_BYTE,
                buffer
            )
        }
        return currentTextureId
    }
}
