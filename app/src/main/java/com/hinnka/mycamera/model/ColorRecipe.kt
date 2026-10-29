package com.hinnka.mycamera.model

import androidx.annotation.Keep
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.hinnka.mycamera.R

/**
 * 色彩配方参数数据类
 *
 * 定义所有色彩调整参数的值
 */
@Keep
data class ColorRecipeParams(
    val styleTone: Float = 0f,
    val styleWarmth: Float = 0f,
    val styleColour: Float = 0f,
    val sharpnessRadius: Float = 1f,
    val sharpnessThreshold: Float = 0.005f,
    val exposure: Float = 0f,       // -2.0 ~ +2.0 (EV值，曝光调整)
    val contrast: Float = 1f,       // 0.5 ~ 1.5 (对比度，1为无调整)
    val tonality: Float = 0f,       // -1.0 ~ +1.0 (BasicTone 影调强度，0为无调整)
    val saturation: Float = 1f,     // 0.0 ~ 2.0 (饱和度，1为无调整)
    val temperature: Float = 0f,    // -1.0 ~ +1.0 (色温，负值偏冷，正值偏暖)
    val tint: Float = 0f,           // -1.0 ~ +1.0 (色调，负值偏绿，正值偏品红)
    val fade: Float = 0f,           // 0.0 ~ 1.0 (褪色效果，0为无褪色)
    val color: Float = 0f,       // -1.0 ~ 1.0 (色彩密度，0为无调整)
    val highlights: Float = 0f,     // -1.0 ~ +1.0 (高光调整，0为无调整)
    val shadows: Float = 0f,        // -1.0 ~ +1.0 (阴影调整，0为无调整)
    val filmGrain: Float = 0f,      // 0.0 ~ 1.0 (颗粒强度，0为无颗粒)
    val vignette: Float = 0f,       // -1.0 ~ +1.0 (晕影，负值暗角，正值亮角)
    val flash: Float = 0f,          // 0.0 ~ 1.0 (镜头轴向直闪模拟强度，0为无效果)
    val bleachBypass: Float = 0f,   // 0.0 ~ 1.0 (留银冲洗强度，0为无效果)
    val clarity: Float = 0f,        // -1.0 ~ 1.0 (局部对比度，0为无效果)
    val sharpness: Float = 0f,      // -1.0 ~ 1.0 (sRGB 锐度，负值柔化，正值锐化)
    val bloom: Float = 0f,          // 0.0 ~ 1.0 (高光颜色扩散泛光强度，0为无效果)
    val softLight: Float = 0f,      // 0.0 ~ 1.0 (柔光扩散强度，0为无效果)
    val halation: Float = 0f,       // 历史 HDF 字段，配方读写时归零；当前光晕使用 redHalation
    val redHalation: Float = 0f,    // 0.0 ~ 1.0 (高光遮罩扩散红色光晕强度，0为无效果)
    val chromaticAberration: Float = 0f, // 0.0 ~ 1.0 (色散/边缘溢色强度，0为无效果)
    val noise: Float = 0f,          // 0.0 ~ 1.0 (噪点强度，包含亮度和色彩噪点，0为无效果)
    val lowRes: Float = 0f,         // 0.0 ~ 1.0 (低像素强度，0为无效果)
    val skinHue: Float = 0f,
    val skinChroma: Float = 0f,
    val skinLightness: Float = 0f,
    val redHue: Float = 0f,
    val redChroma: Float = 0f,
    val redLightness: Float = 0f,
    val orangeHue: Float = 0f,
    val orangeChroma: Float = 0f,
    val orangeLightness: Float = 0f,
    val yellowHue: Float = 0f,
    val yellowChroma: Float = 0f,
    val yellowLightness: Float = 0f,
    val greenHue: Float = 0f,
    val greenChroma: Float = 0f,
    val greenLightness: Float = 0f,
    val cyanHue: Float = 0f,
    val cyanChroma: Float = 0f,
    val cyanLightness: Float = 0f,
    val blueHue: Float = 0f,
    val blueChroma: Float = 0f,
    val blueLightness: Float = 0f,
    val purpleHue: Float = 0f,
    val purpleChroma: Float = 0f,
    val purpleLightness: Float = 0f,
    val magentaHue: Float = 0f,
    val magentaChroma: Float = 0f,
    val magentaLightness: Float = 0f,
    val primaryRedHue: Float = 0f,
    val primaryRedSaturation: Float = 0f,
    val primaryRedLightness: Float = 0f,
    val primaryGreenHue: Float = 0f,
    val primaryGreenSaturation: Float = 0f,
    val primaryGreenLightness: Float = 0f,
    val primaryBlueHue: Float = 0f,
    val primaryBlueSaturation: Float = 0f,
    val primaryBlueLightness: Float = 0f,
    val gradeLiftRed: Float = 0f, // display-referred LGGO delta; zero is identity
    val gradeLiftGreen: Float = 0f, // display-referred LGGO delta; zero is identity
    val gradeLiftBlue: Float = 0f, // display-referred LGGO delta; zero is identity
    val gradeGammaRed: Float = 0f, // display-referred LGGO delta; zero is identity
    val gradeGammaGreen: Float = 0f, // display-referred LGGO delta; zero is identity
    val gradeGammaBlue: Float = 0f, // display-referred LGGO delta; zero is identity
    val gradeGainRed: Float = 0f, // display-referred LGGO delta; zero is identity
    val gradeGainGreen: Float = 0f, // display-referred LGGO delta; zero is identity
    val gradeGainBlue: Float = 0f, // display-referred LGGO delta; zero is identity
    val gradeOffsetRed: Float = 0f, // display-referred LGGO delta; zero is identity
    val gradeOffsetGreen: Float = 0f, // display-referred LGGO delta; zero is identity
    val gradeOffsetBlue: Float = 0f, // display-referred LGGO delta; zero is identity
    val gradingShadowHue: Float = 0f,          // 0.0 ~ 1.0
    val gradingShadowAmount: Float = 0f,       // 0.0 ~ 1.0
    val gradingShadowLuminance: Float = 0f,    // -1.0 ~ 1.0
    val gradingMidtoneHue: Float = 0f,         // 0.0 ~ 1.0
    val gradingMidtoneAmount: Float = 0f,      // 0.0 ~ 1.0
    val gradingMidtoneLuminance: Float = 0f,   // -1.0 ~ 1.0
    val gradingHighlightHue: Float = 0f,       // 0.0 ~ 1.0
    val gradingHighlightAmount: Float = 0f,    // 0.0 ~ 1.0
    val gradingHighlightLuminance: Float = 0f, // -1.0 ~ 1.0
    val gradingBalance: Float = 0f,            // -1.0 ~ 1.0
    val gradingBlending: Float = 0.5f,         // 0.0 ~ 1.0
    val lutIntensity: Float = 1f,   // 0.0 ~ 2.0 (LUT强度，1为完全应用，超过1沿LUT色差外推)
    val remarks: String? = "",       // 用户备注
    // 曲线控制点 [x0,y0, x1,y1, ...], null = 恒等曲线（无效果）
    val masterCurvePoints: FloatArray? = null,
    val redCurvePoints: FloatArray? = null,
    val greenCurvePoints: FloatArray? = null,
    val blueCurvePoints: FloatArray? = null,
) {
    /**
     * 检查参数是否为默认值（无任何调整）
     */
    fun isDefault(): Boolean {
        return gradeLiftRed == 0f && gradeLiftGreen == 0f && gradeLiftBlue == 0f && gradeGammaRed == 0f && gradeGammaGreen == 0f && gradeGammaBlue == 0f && gradeGainRed == 0f && gradeGainGreen == 0f && gradeGainBlue == 0f && gradeOffsetRed == 0f && gradeOffsetGreen == 0f && gradeOffsetBlue == 0f &&
                styleTone == 0f && styleWarmth == 0f && styleColour == 0f &&
                exposure == 0f &&
                contrast == 1f &&
                tonality == 0f &&
                saturation == 1f &&
                temperature == 0f &&
                tint == 0f &&
                fade == 0f &&
                color == 0f &&
                highlights == 0f &&
                shadows == 0f &&
                filmGrain == 0f &&
                vignette == 0f &&
                flash == 0f &&
                bleachBypass == 0f &&
                clarity == 0f &&
                sharpness == 0f &&
                bloom == 0f &&
                softLight == 0f &&
                redHalation == 0f &&
                chromaticAberration == 0f &&
                noise == 0f &&
                lowRes == 0f &&
                skinHue == 0f &&
                skinChroma == 0f &&
                skinLightness == 0f &&
                redHue == 0f &&
                redChroma == 0f &&
                redLightness == 0f &&
                orangeHue == 0f &&
                orangeChroma == 0f &&
                orangeLightness == 0f &&
                yellowHue == 0f &&
                yellowChroma == 0f &&
                yellowLightness == 0f &&
                greenHue == 0f &&
                greenChroma == 0f &&
                greenLightness == 0f &&
                cyanHue == 0f &&
                cyanChroma == 0f &&
                cyanLightness == 0f &&
                blueHue == 0f &&
                blueChroma == 0f &&
                blueLightness == 0f &&
                purpleHue == 0f &&
                purpleChroma == 0f &&
                purpleLightness == 0f &&
                magentaHue == 0f &&
                magentaChroma == 0f &&
                magentaLightness == 0f &&
                primaryRedHue == 0f &&
                primaryRedSaturation == 0f &&
                primaryRedLightness == 0f &&
                primaryGreenHue == 0f &&
                primaryGreenSaturation == 0f &&
                primaryGreenLightness == 0f &&
                primaryBlueHue == 0f &&
                primaryBlueSaturation == 0f &&
                primaryBlueLightness == 0f &&
                gradingShadowHue == 0f &&
                gradingShadowAmount == 0f &&
                gradingShadowLuminance == 0f &&
                gradingMidtoneHue == 0f &&
                gradingMidtoneAmount == 0f &&
                gradingMidtoneLuminance == 0f &&
                gradingHighlightHue == 0f &&
                gradingHighlightAmount == 0f &&
                gradingHighlightLuminance == 0f &&
                gradingBalance == 0f &&
                gradingBlending == 0.5f &&
                remarks.isNullOrEmpty() &&
                masterCurvePoints == null &&
                redCurvePoints == null &&
                greenCurvePoints == null &&
                blueCurvePoints == null
    }

    /**
     * 检查参数是否与另一个参数集相同
     */
    fun isSameAs(other: ColorRecipeParams): Boolean {
        return styleTone == other.styleTone && styleWarmth == other.styleWarmth &&
                styleColour == other.styleColour && sharpnessRadius == other.sharpnessRadius &&
                sharpnessThreshold == other.sharpnessThreshold && exposure == other.exposure &&
                contrast == other.contrast &&
                tonality == other.tonality &&
                saturation == other.saturation &&
                temperature == other.temperature &&
                tint == other.tint &&
                fade == other.fade &&
                color == other.color &&
                highlights == other.highlights &&
                shadows == other.shadows &&
                filmGrain == other.filmGrain &&
                vignette == other.vignette &&
                flash == other.flash &&
                bleachBypass == other.bleachBypass &&
                clarity == other.clarity &&
                sharpness == other.sharpness &&
                bloom == other.bloom &&
                softLight == other.softLight &&
                redHalation == other.redHalation &&
                chromaticAberration == other.chromaticAberration &&
                noise == other.noise &&
                lowRes == other.lowRes &&
                skinHue == other.skinHue &&
                skinChroma == other.skinChroma &&
                skinLightness == other.skinLightness &&
                redHue == other.redHue &&
                redChroma == other.redChroma &&
                redLightness == other.redLightness &&
                orangeHue == other.orangeHue &&
                orangeChroma == other.orangeChroma &&
                orangeLightness == other.orangeLightness &&
                yellowHue == other.yellowHue &&
                yellowChroma == other.yellowChroma &&
                yellowLightness == other.yellowLightness &&
                greenHue == other.greenHue &&
                greenChroma == other.greenChroma &&
                greenLightness == other.greenLightness &&
                cyanHue == other.cyanHue &&
                cyanChroma == other.cyanChroma &&
                cyanLightness == other.cyanLightness &&
                blueHue == other.blueHue &&
                blueChroma == other.blueChroma &&
                blueLightness == other.blueLightness &&
                purpleHue == other.purpleHue &&
                purpleChroma == other.purpleChroma &&
                purpleLightness == other.purpleLightness &&
                magentaHue == other.magentaHue &&
                magentaChroma == other.magentaChroma &&
                magentaLightness == other.magentaLightness &&
                primaryRedHue == other.primaryRedHue &&
                primaryRedSaturation == other.primaryRedSaturation &&
                primaryRedLightness == other.primaryRedLightness &&
                primaryGreenHue == other.primaryGreenHue &&
                primaryGreenSaturation == other.primaryGreenSaturation &&
                primaryGreenLightness == other.primaryGreenLightness &&
                primaryBlueHue == other.primaryBlueHue &&
                primaryBlueSaturation == other.primaryBlueSaturation &&
                primaryBlueLightness == other.primaryBlueLightness &&
                gradeLiftRed == other.gradeLiftRed &&
                gradeLiftGreen == other.gradeLiftGreen &&
                gradeLiftBlue == other.gradeLiftBlue &&
                gradeGammaRed == other.gradeGammaRed &&
                gradeGammaGreen == other.gradeGammaGreen &&
                gradeGammaBlue == other.gradeGammaBlue &&
                gradeGainRed == other.gradeGainRed &&
                gradeGainGreen == other.gradeGainGreen &&
                gradeGainBlue == other.gradeGainBlue &&
                gradeOffsetRed == other.gradeOffsetRed &&
                gradeOffsetGreen == other.gradeOffsetGreen &&
                gradeOffsetBlue == other.gradeOffsetBlue &&
                gradingShadowHue == other.gradingShadowHue &&
                gradingShadowAmount == other.gradingShadowAmount &&
                gradingShadowLuminance == other.gradingShadowLuminance &&
                gradingMidtoneHue == other.gradingMidtoneHue &&
                gradingMidtoneAmount == other.gradingMidtoneAmount &&
                gradingMidtoneLuminance == other.gradingMidtoneLuminance &&
                gradingHighlightHue == other.gradingHighlightHue &&
                gradingHighlightAmount == other.gradingHighlightAmount &&
                gradingHighlightLuminance == other.gradingHighlightLuminance &&
                gradingBalance == other.gradingBalance &&
                gradingBlending == other.gradingBlending &&
                lutIntensity == other.lutIntensity &&
                remarks == other.remarks &&
                (masterCurvePoints === other.masterCurvePoints || masterCurvePoints?.contentEquals(other.masterCurvePoints) == true) &&
                (redCurvePoints === other.redCurvePoints || redCurvePoints?.contentEquals(other.redCurvePoints) == true) &&
                (greenCurvePoints === other.greenCurvePoints || greenCurvePoints?.contentEquals(other.greenCurvePoints) == true) &&
                (blueCurvePoints === other.blueCurvePoints || blueCurvePoints?.contentEquals(other.blueCurvePoints) == true)
    }

    /**
     * 序列化为 JSON 字符串
     */
    fun toJson(): String = gson.toJson(
        copy(halation = 0f)
    )

    companion object {
        private val gson = Gson()

        /**
         * 从 JSON 字符串反序列化
         */
        fun fromJson(json: String): ColorRecipeParams {
            return try {
                val root = JsonParser.parseString(json)
                val parsed = gson.fromJson(root, ColorRecipeParams::class.java) ?: return DEFAULT
                parsed.copy(
                    gradeLiftRed = if (parsed.gradeLiftRed.isFinite()) parsed.gradeLiftRed.coerceIn(-0.5f, 0.5f) else 0f,
                    gradeLiftGreen = if (parsed.gradeLiftGreen.isFinite()) parsed.gradeLiftGreen.coerceIn(-0.5f, 0.5f) else 0f,
                    gradeLiftBlue = if (parsed.gradeLiftBlue.isFinite()) parsed.gradeLiftBlue.coerceIn(-0.5f, 0.5f) else 0f,
                    gradeGammaRed = if (parsed.gradeGammaRed.isFinite()) parsed.gradeGammaRed.coerceIn(-0.5f, 0.5f) else 0f,
                    gradeGammaGreen = if (parsed.gradeGammaGreen.isFinite()) parsed.gradeGammaGreen.coerceIn(-0.5f, 0.5f) else 0f,
                    gradeGammaBlue = if (parsed.gradeGammaBlue.isFinite()) parsed.gradeGammaBlue.coerceIn(-0.5f, 0.5f) else 0f,
                    gradeGainRed = if (parsed.gradeGainRed.isFinite()) parsed.gradeGainRed.coerceIn(-0.5f, 0.5f) else 0f,
                    gradeGainGreen = if (parsed.gradeGainGreen.isFinite()) parsed.gradeGainGreen.coerceIn(-0.5f, 0.5f) else 0f,
                    gradeGainBlue = if (parsed.gradeGainBlue.isFinite()) parsed.gradeGainBlue.coerceIn(-0.5f, 0.5f) else 0f,
                    gradeOffsetRed = if (parsed.gradeOffsetRed.isFinite()) parsed.gradeOffsetRed.coerceIn(-0.5f, 0.5f) else 0f,
                    gradeOffsetGreen = if (parsed.gradeOffsetGreen.isFinite()) parsed.gradeOffsetGreen.coerceIn(-0.5f, 0.5f) else 0f,
                    gradeOffsetBlue = if (parsed.gradeOffsetBlue.isFinite()) parsed.gradeOffsetBlue.coerceIn(-0.5f, 0.5f) else 0f,
                    halation = 0f,
                    styleTone = if (parsed.styleTone.isFinite()) parsed.styleTone.coerceIn(-1f, 1f) else 0f,
                    styleWarmth = if (parsed.styleWarmth.isFinite()) parsed.styleWarmth.coerceIn(-1f, 1f) else 0f,
                    styleColour = if (parsed.styleColour.isFinite()) parsed.styleColour.coerceIn(-1f, 1f) else 0f,
                    sharpnessRadius = if (root.isJsonObject && root.asJsonObject.has("sharpnessRadius") && parsed.sharpnessRadius.isFinite()) parsed.sharpnessRadius.coerceIn(0.5f, 3f) else 1f,
                    sharpnessThreshold = if (root.isJsonObject && root.asJsonObject.has("sharpnessThreshold") && parsed.sharpnessThreshold.isFinite()) parsed.sharpnessThreshold.coerceIn(0f, 0.05f) else 0.005f,
                    gradingBlending = if (
                        root.isJsonObject && root.asJsonObject.has("gradingBlending")
                    ) {
                        parsed.gradingBlending
                    } else {
                        DEFAULT.gradingBlending
                    }
                ).let { LegacyColorRecipeMigration.migrate(it, root.asJsonObject) }
            } catch (_: Exception) {
                DEFAULT
            }
        }

        /**
         * 默认参数（无调整）
         */
        val DEFAULT = ColorRecipeParams()
    }
}

/**
 * 色彩配方参数枚举
 *
 * 用于标识不同的可调整参数
 */
enum class RecipeParam(
    val displayNameRes: Int,             // 显示名称资源ID
    val minValue: Float,                 // 最小值
    val maxValue: Float,                 // 最大值
    val defaultValue: Float              // 默认值
) {
    EXPOSURE(R.string.recipe_param_exposure, -2.0f, 2.0f, 0f),
    CONTRAST(R.string.recipe_param_contrast, 0.5f, 1.5f, 1f),
    TONALITY(R.string.recipe_palette_tone, -1.0f, 1.0f, 0f),
    SATURATION(R.string.recipe_param_saturation, 0.0f, 2.0f, 1f),
    TEMPERATURE(R.string.recipe_param_temperature, -1.0f, 1.0f, 0f),
    TINT(R.string.recipe_param_tint, -1.0f, 1.0f, 0f),
    FADE(R.string.recipe_param_fade, 0.0f, 1.0f, 0f),
    COLOR(R.string.recipe_param_color, -1.0f, 1.0f, 0f),
    HIGHLIGHTS(R.string.recipe_param_highlights, -1.0f, 1.0f, 0f),
    SHADOWS(R.string.recipe_param_shadows, -1.0f, 1.0f, 0f),
    FILM_GRAIN(R.string.recipe_param_film_grain, 0.0f, 1.0f, 0f),
    VIGNETTE(R.string.recipe_param_vignette, -1.0f, 1.0f, 0f),
    FLASH(R.string.recipe_param_flash, 0.0f, 1.0f, 0f),
    BLEACH_BYPASS(R.string.recipe_param_bleach_bypass, 0.0f, 1.0f, 0f),
    CLARITY(R.string.recipe_param_clarity, -1.0f, 1.0f, 0f),
    SHARPNESS(R.string.recipe_param_sharpness, -1.0f, 1.0f, 0f),
    BLOOM(R.string.recipe_param_bloom, 0.0f, 1.0f, 0f),
    SOFT_LIGHT(R.string.recipe_param_soft_light, 0.0f, 1.0f, 0f),
    HDF(R.string.recipe_param_hdf, 0.0f, 1.0f, 0f),
    HALATION(R.string.recipe_param_halation, 0.0f, 1.0f, 0f),
    CHROMATIC_ABERRATION(R.string.recipe_param_chromatic_aberration, 0.0f, 1.0f, 0f),
    NOISE(R.string.recipe_param_noise, 0.0f, 1.0f, 0f),
    LOW_RES(R.string.recipe_param_low_res, 0.0f, 1.0f, 0f),
    SKIN_HUE(R.string.recipe_param_skin_hue, -1.0f, 1.0f, 0f),
    SKIN_CHROMA(R.string.recipe_param_skin_chroma, -1.0f, 1.0f, 0f),
    SKIN_LIGHTNESS(R.string.recipe_param_skin_lightness, -1.0f, 1.0f, 0f),
    RED_HUE(R.string.recipe_param_red_hue, -1.0f, 1.0f, 0f),
    RED_CHROMA(R.string.recipe_param_red_chroma, -1.0f, 1.0f, 0f),
    RED_LIGHTNESS(R.string.recipe_param_red_lightness, -1.0f, 1.0f, 0f),
    ORANGE_HUE(R.string.recipe_param_orange_hue, -1.0f, 1.0f, 0f),
    ORANGE_CHROMA(R.string.recipe_param_orange_chroma, -1.0f, 1.0f, 0f),
    ORANGE_LIGHTNESS(R.string.recipe_param_orange_lightness, -1.0f, 1.0f, 0f),
    YELLOW_HUE(R.string.recipe_param_yellow_hue, -1.0f, 1.0f, 0f),
    YELLOW_CHROMA(R.string.recipe_param_yellow_chroma, -1.0f, 1.0f, 0f),
    YELLOW_LIGHTNESS(R.string.recipe_param_yellow_lightness, -1.0f, 1.0f, 0f),
    GREEN_HUE(R.string.recipe_param_green_hue, -1.0f, 1.0f, 0f),
    GREEN_CHROMA(R.string.recipe_param_green_chroma, -1.0f, 1.0f, 0f),
    GREEN_LIGHTNESS(R.string.recipe_param_green_lightness, -1.0f, 1.0f, 0f),
    CYAN_HUE(R.string.recipe_param_cyan_hue, -1.0f, 1.0f, 0f),
    CYAN_CHROMA(R.string.recipe_param_cyan_chroma, -1.0f, 1.0f, 0f),
    CYAN_LIGHTNESS(R.string.recipe_param_cyan_lightness, -1.0f, 1.0f, 0f),
    BLUE_HUE(R.string.recipe_param_blue_hue, -1.0f, 1.0f, 0f),
    BLUE_CHROMA(R.string.recipe_param_blue_chroma, -1.0f, 1.0f, 0f),
    BLUE_LIGHTNESS(R.string.recipe_param_blue_lightness, -1.0f, 1.0f, 0f),
    PURPLE_HUE(R.string.recipe_param_purple_hue, -1.0f, 1.0f, 0f),
    PURPLE_CHROMA(R.string.recipe_param_purple_chroma, -1.0f, 1.0f, 0f),
    PURPLE_LIGHTNESS(R.string.recipe_param_purple_lightness, -1.0f, 1.0f, 0f),
    MAGENTA_HUE(R.string.recipe_param_magenta_hue, -1.0f, 1.0f, 0f),
    MAGENTA_CHROMA(R.string.recipe_param_magenta_chroma, -1.0f, 1.0f, 0f),
    MAGENTA_LIGHTNESS(R.string.recipe_param_magenta_lightness, -1.0f, 1.0f, 0f),
    PRIMARY_RED_HUE(R.string.recipe_param_primary_red_hue, -1.0f, 1.0f, 0f),
    PRIMARY_RED_SATURATION(R.string.recipe_param_primary_red_saturation, -1.0f, 1.0f, 0f),
    PRIMARY_RED_LIGHTNESS(R.string.recipe_param_primary_red_lightness, -1.0f, 1.0f, 0f),
    PRIMARY_GREEN_HUE(R.string.recipe_param_primary_green_hue, -1.0f, 1.0f, 0f),
    PRIMARY_GREEN_SATURATION(R.string.recipe_param_primary_green_saturation, -1.0f, 1.0f, 0f),
    PRIMARY_GREEN_LIGHTNESS(R.string.recipe_param_primary_green_lightness, -1.0f, 1.0f, 0f),
    PRIMARY_BLUE_HUE(R.string.recipe_param_primary_blue_hue, -1.0f, 1.0f, 0f),
    PRIMARY_BLUE_SATURATION(R.string.recipe_param_primary_blue_saturation, -1.0f, 1.0f, 0f),
    PRIMARY_BLUE_LIGHTNESS(R.string.recipe_param_primary_blue_lightness, -1.0f, 1.0f, 0f),
    LUT_INTENSITY(R.string.recipe_param_lut_intensity, 0.0f, 2.0f, 1f);

    /**
     * 将参数值限制在合法范围内
     */
    fun clamp(value: Float): Float {
        return value.coerceIn(minValue, maxValue)
    }

    /**
     * 获取参数在当前参数集中的值
     */
    fun getValue(params: ColorRecipeParams): Float {
        return when (this) {
            EXPOSURE -> params.exposure
            CONTRAST -> params.contrast
            TONALITY -> params.tonality
            SATURATION -> params.saturation
            TEMPERATURE -> params.temperature
            TINT -> params.tint
            FADE -> params.fade
            COLOR -> params.color
            HIGHLIGHTS -> params.highlights
            SHADOWS -> params.shadows
            FILM_GRAIN -> params.filmGrain
            VIGNETTE -> params.vignette
            FLASH -> params.flash
            BLEACH_BYPASS -> params.bleachBypass
            CLARITY -> params.clarity
            SHARPNESS -> params.sharpness
            BLOOM -> params.bloom
            SOFT_LIGHT -> params.softLight
            HDF -> params.halation
            HALATION -> params.redHalation
            CHROMATIC_ABERRATION -> params.chromaticAberration
            NOISE -> params.noise
            LOW_RES -> params.lowRes
            SKIN_HUE -> params.skinHue
            SKIN_CHROMA -> params.skinChroma
            SKIN_LIGHTNESS -> params.skinLightness
            RED_HUE -> params.redHue
            RED_CHROMA -> params.redChroma
            RED_LIGHTNESS -> params.redLightness
            ORANGE_HUE -> params.orangeHue
            ORANGE_CHROMA -> params.orangeChroma
            ORANGE_LIGHTNESS -> params.orangeLightness
            YELLOW_HUE -> params.yellowHue
            YELLOW_CHROMA -> params.yellowChroma
            YELLOW_LIGHTNESS -> params.yellowLightness
            GREEN_HUE -> params.greenHue
            GREEN_CHROMA -> params.greenChroma
            GREEN_LIGHTNESS -> params.greenLightness
            CYAN_HUE -> params.cyanHue
            CYAN_CHROMA -> params.cyanChroma
            CYAN_LIGHTNESS -> params.cyanLightness
            BLUE_HUE -> params.blueHue
            BLUE_CHROMA -> params.blueChroma
            BLUE_LIGHTNESS -> params.blueLightness
            PURPLE_HUE -> params.purpleHue
            PURPLE_CHROMA -> params.purpleChroma
            PURPLE_LIGHTNESS -> params.purpleLightness
            MAGENTA_HUE -> params.magentaHue
            MAGENTA_CHROMA -> params.magentaChroma
            MAGENTA_LIGHTNESS -> params.magentaLightness
            PRIMARY_RED_HUE -> params.primaryRedHue
            PRIMARY_RED_SATURATION -> params.primaryRedSaturation
            PRIMARY_RED_LIGHTNESS -> params.primaryRedLightness
            PRIMARY_GREEN_HUE -> params.primaryGreenHue
            PRIMARY_GREEN_SATURATION -> params.primaryGreenSaturation
            PRIMARY_GREEN_LIGHTNESS -> params.primaryGreenLightness
            PRIMARY_BLUE_HUE -> params.primaryBlueHue
            PRIMARY_BLUE_SATURATION -> params.primaryBlueSaturation
            PRIMARY_BLUE_LIGHTNESS -> params.primaryBlueLightness
            LUT_INTENSITY -> params.lutIntensity
        }
    }

    /**
     * 在参数集中设置此参数的值
     */
    fun setValue(params: ColorRecipeParams, value: Float): ColorRecipeParams {
        val clampedValue = clamp(value)
        return when (this) {
            EXPOSURE -> params.copy(exposure = clampedValue)
            CONTRAST -> params.copy(contrast = clampedValue)
            TONALITY -> params.copy(tonality = clampedValue)
            SATURATION -> params.copy(saturation = clampedValue)
            TEMPERATURE -> params.copy(temperature = clampedValue)
            TINT -> params.copy(tint = clampedValue)
            FADE -> params.copy(fade = clampedValue)
            COLOR -> params.copy(color = clampedValue)
            HIGHLIGHTS -> params.copy(highlights = clampedValue)
            SHADOWS -> params.copy(shadows = clampedValue)
            FILM_GRAIN -> params.copy(filmGrain = clampedValue)
            VIGNETTE -> params.copy(vignette = clampedValue)
            FLASH -> params.copy(flash = clampedValue)
            BLEACH_BYPASS -> params.copy(bleachBypass = clampedValue)
            CLARITY -> params.copy(clarity = clampedValue)
            SHARPNESS -> params.copy(sharpness = clampedValue)
            BLOOM -> params.copy(bloom = clampedValue)
            SOFT_LIGHT -> params.copy(softLight = clampedValue)
            HDF -> params.copy(halation = clampedValue)
            HALATION -> params.copy(redHalation = clampedValue)
            CHROMATIC_ABERRATION -> params.copy(chromaticAberration = clampedValue)
            NOISE -> params.copy(noise = clampedValue)
            LOW_RES -> params.copy(lowRes = clampedValue)
            SKIN_HUE -> params.copy(skinHue = clampedValue)
            SKIN_CHROMA -> params.copy(skinChroma = clampedValue)
            SKIN_LIGHTNESS -> params.copy(skinLightness = clampedValue)
            RED_HUE -> params.copy(redHue = clampedValue)
            RED_CHROMA -> params.copy(redChroma = clampedValue)
            RED_LIGHTNESS -> params.copy(redLightness = clampedValue)
            ORANGE_HUE -> params.copy(orangeHue = clampedValue)
            ORANGE_CHROMA -> params.copy(orangeChroma = clampedValue)
            ORANGE_LIGHTNESS -> params.copy(orangeLightness = clampedValue)
            YELLOW_HUE -> params.copy(yellowHue = clampedValue)
            YELLOW_CHROMA -> params.copy(yellowChroma = clampedValue)
            YELLOW_LIGHTNESS -> params.copy(yellowLightness = clampedValue)
            GREEN_HUE -> params.copy(greenHue = clampedValue)
            GREEN_CHROMA -> params.copy(greenChroma = clampedValue)
            GREEN_LIGHTNESS -> params.copy(greenLightness = clampedValue)
            CYAN_HUE -> params.copy(cyanHue = clampedValue)
            CYAN_CHROMA -> params.copy(cyanChroma = clampedValue)
            CYAN_LIGHTNESS -> params.copy(cyanLightness = clampedValue)
            BLUE_HUE -> params.copy(blueHue = clampedValue)
            BLUE_CHROMA -> params.copy(blueChroma = clampedValue)
            BLUE_LIGHTNESS -> params.copy(blueLightness = clampedValue)
            PURPLE_HUE -> params.copy(purpleHue = clampedValue)
            PURPLE_CHROMA -> params.copy(purpleChroma = clampedValue)
            PURPLE_LIGHTNESS -> params.copy(purpleLightness = clampedValue)
            MAGENTA_HUE -> params.copy(magentaHue = clampedValue)
            MAGENTA_CHROMA -> params.copy(magentaChroma = clampedValue)
            MAGENTA_LIGHTNESS -> params.copy(magentaLightness = clampedValue)
            PRIMARY_RED_HUE -> params.copy(primaryRedHue = clampedValue)
            PRIMARY_RED_SATURATION -> params.copy(primaryRedSaturation = clampedValue)
            PRIMARY_RED_LIGHTNESS -> params.copy(primaryRedLightness = clampedValue)
            PRIMARY_GREEN_HUE -> params.copy(primaryGreenHue = clampedValue)
            PRIMARY_GREEN_SATURATION -> params.copy(primaryGreenSaturation = clampedValue)
            PRIMARY_GREEN_LIGHTNESS -> params.copy(primaryGreenLightness = clampedValue)
            PRIMARY_BLUE_HUE -> params.copy(primaryBlueHue = clampedValue)
            PRIMARY_BLUE_SATURATION -> params.copy(primaryBlueSaturation = clampedValue)
            PRIMARY_BLUE_LIGHTNESS -> params.copy(primaryBlueLightness = clampedValue)
            LUT_INTENSITY -> params.copy(lutIntensity = clampedValue)
        }
    }
}
