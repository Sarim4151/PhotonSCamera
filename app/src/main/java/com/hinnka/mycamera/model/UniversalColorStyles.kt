package com.hinnka.mycamera.model

import androidx.annotation.StringRes
import com.hinnka.mycamera.R

/** Display-referred creative starting points; preserve camera calibration and capture settings. */
enum class UniversalColorStyles(@StringRes val titleRes: Int) {
    NEUTRAL(R.string.universal_style_neutral),
    PORTRAIT(R.string.universal_style_portrait),
    LANDSCAPE(R.string.universal_style_landscape);

    fun applyTo(source: ColorRecipeParams): ColorRecipeParams {
        val base = source.copy(
            styleTone = 0f, styleWarmth = 0f, styleColour = 0f,
            contrast = 1f, tonality = 0f, saturation = 1f,
            color = 0f, fade = 0f, bleachBypass = 0f,
            highlights = 0f, shadows = 0f,
            skinHue = 0f, skinChroma = 0f, skinLightness = 0f,
            redHue = 0f, redChroma = 0f, redLightness = 0f,
            orangeHue = 0f, orangeChroma = 0f, orangeLightness = 0f,
            yellowHue = 0f, yellowChroma = 0f, yellowLightness = 0f,
            greenHue = 0f, greenChroma = 0f, greenLightness = 0f,
            cyanHue = 0f, cyanChroma = 0f, cyanLightness = 0f,
            blueHue = 0f, blueChroma = 0f, blueLightness = 0f,
            purpleHue = 0f, purpleChroma = 0f, purpleLightness = 0f,
            magentaHue = 0f, magentaChroma = 0f, magentaLightness = 0f,
        )
        return when (this) {
            NEUTRAL -> base
            PORTRAIT -> base.copy(contrast = 0.98f, saturation = 0.98f,
                highlights = -0.10f, shadows = 0.03f, skinChroma = -0.03f, skinLightness = 0.02f)
            LANDSCAPE -> base.copy(contrast = 1.04f, saturation = 1.04f,
                highlights = -0.10f, shadows = 0.02f, greenChroma = 0.02f, blueChroma = 0.02f)
        }
    }
}
