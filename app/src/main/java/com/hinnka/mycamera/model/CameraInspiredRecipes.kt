package com.hinnka.mycamera.model

import androidx.annotation.StringRes
import com.hinnka.mycamera.R

/**
 * Original, restrained looks inspired by familiar photographic styles.
 * These recipes do not reproduce a manufacturer's proprietary image processing.
 * Stable IDs allow the existing preset persistence, overrides and deletion flow to apply.
 */
object CameraInspiredRecipes {
    private data class Recipe(
        val id: String,
        @StringRes val nameRes: Int,
        val params: ColorRecipeParams,
    )

    private val recipes = listOf(
        Recipe("builtin_inspired_leica_vivid", R.string.camera_recipe_leica_vivid,
            ColorRecipeParams(contrast = 1.10f, saturation = 1.12f, highlights = -0.12f, shadows = -0.04f, styleColour = 0.15f)),
        Recipe("builtin_inspired_cinematic", R.string.camera_recipe_cinematic,
            ColorRecipeParams(contrast = 1.06f, saturation = 0.90f, highlights = -0.18f, shadows = 0.04f,
                gradingShadowHue = 0.52f, gradingShadowAmount = 0.09f,
                gradingHighlightHue = 0.10f, gradingHighlightAmount = 0.06f)),
        Recipe(
            "builtin_inspired_natural",
            R.string.camera_recipe_natural,
            ColorRecipeParams.DEFAULT,
        ),
        Recipe(
            "builtin_inspired_iphone",
            R.string.camera_recipe_iphone,
            ColorRecipeParams(
                contrast = 1.03f,
                saturation = 1.03f,
                highlights = -0.12f,
                shadows = 0.08f,
                temperature = 0.01f,
                skinChroma = -0.02f,
                skinLightness = 0.02f,
            ),
        ),
        Recipe(
            "builtin_inspired_galaxy",
            R.string.camera_recipe_galaxy,
            ColorRecipeParams(
                contrast = 1.06f,
                saturation = 1.12f,
                highlights = -0.10f,
                shadows = 0.05f,
                blueChroma = 0.04f,
                greenChroma = 0.03f,
                skinChroma = -0.06f,
                skinLightness = 0.02f,
            ),
        ),
        Recipe(
            "builtin_inspired_leica_natural",
            R.string.camera_recipe_leica_natural,
            ColorRecipeParams(
                contrast = 1.08f,
                saturation = 0.96f,
                temperature = 0.02f,
                highlights = -0.08f,
                shadows = -0.03f,
                skinChroma = -0.02f,
                skinLightness = 0.01f,
            ),
        ),
        Recipe(
            "builtin_inspired_leica_monochrome",
            R.string.camera_recipe_leica_monochrome,
            ColorRecipeParams(
                saturation = 0f,
                contrast = 1.12f,
                highlights = -0.10f,
                shadows = -0.04f,
                skinLightness = 0.04f,
            ),
        ),
        Recipe(
            "builtin_inspired_canon_portrait",
            R.string.camera_recipe_canon_portrait,
            ColorRecipeParams(
                contrast = 0.98f,
                saturation = 1.02f,
                temperature = 0.03f,
                tint = 0.01f,
                highlights = -0.10f,
                shadows = 0.04f,
                skinHue = 0.01f,
                skinChroma = -0.04f,
                skinLightness = 0.05f,
            ),
        ),
    )

    /** Names are stable keys; resolve them through [displayNameResource] when displayed. */
    val presets: List<CameraPreset> = recipes.map { recipe ->
        CameraPreset(
            id = recipe.id,
            name = recipe.id,
            lutId = null,
            colorRecipe = recipe.params,
            effects = EffectParams.DEFAULT,
            isBuiltIn = true,
        )
    }

    @StringRes
    fun displayNameResource(presetId: String): Int? =
        recipes.firstOrNull { it.id == presetId }?.nameRes
}
