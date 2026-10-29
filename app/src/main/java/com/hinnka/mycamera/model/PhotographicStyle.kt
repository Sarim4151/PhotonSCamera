package com.hinnka.mycamera.model

/** Display-referred look controls; exposure remains a separate scene-light control. */
data class PhotographicStyle(
    val tone: Float = 0f,
    val warmth: Float = 0f,
    val colour: Float = 0f,
    val detail: Float = 0f,
    val fineness: Float = 1f,
    val threshold: Float = 0.005f,
) {
    fun normalized() = copy(tone = bound(tone, -1f, 1f), warmth = bound(warmth, -1f, 1f),
        colour = bound(colour, -1f, 1f), detail = bound(detail, -1f, 1f),
        fineness = bound(fineness, 0.5f, 3f, 1f), threshold = bound(threshold, 0f, 0.05f, 0.005f))
    fun applyTo(recipe: ColorRecipeParams): ColorRecipeParams = normalized().let {
        recipe.copy(styleTone = it.tone, styleWarmth = it.warmth, styleColour = it.colour,
            sharpness = it.detail, sharpnessRadius = it.fineness, sharpnessThreshold = it.threshold)
    }
    companion object {
        private fun bound(v: Float, min: Float, max: Float, fallback: Float = 0f) =
            if (v.isFinite()) v.coerceIn(min, max) else fallback
        fun fromRecipe(recipe: ColorRecipeParams) = PhotographicStyle(recipe.styleTone,
            recipe.styleWarmth, recipe.styleColour, recipe.sharpness,
            recipe.sharpnessRadius, recipe.sharpnessThreshold).normalized()
    }
}

enum class WhiteBalanceIntent { AUTO, KEEP_ATMOSPHERE, NEUTRAL_WHITES }
