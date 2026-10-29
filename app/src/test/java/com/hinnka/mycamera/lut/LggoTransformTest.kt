package com.hinnka.mycamera.lut

import com.hinnka.mycamera.model.ColorRecipeParams
import org.junit.Assert.*
import org.junit.Test

class LggoTransformTest {
    @Test fun neutralIsIdentityAcrossGrayRamp() {
        for (i in 0..1024) {
            val x = i / 1024f
            assertEquals(x, LggoTransform.channel(x, 0f, 0f, 0f, 0f), 0.000001f)
        }
    }
    @Test fun anchorsAndChannelOperationsAreNumericallyDefined() {
        assertEquals(1f, LggoTransform.channel(1f, .25f, 0f, 0f, 0f), .000001f)
        assertEquals(.25f, LggoTransform.channel(0f, .25f, 0f, 0f, 0f), .000001f)
        assertEquals(0f, LggoTransform.channel(0f, 0f, 0f, .5f, 0f), .000001f)
        assertEquals(.75f, LggoTransform.channel(.5f, 0f, 0f, .5f, 0f), .000001f)
        assertEquals(.5f, LggoTransform.channel(.25f, 0f, 1f, 0f, 0f), .000001f)
        assertEquals(.7f, LggoTransform.channel(.5f, 0f, 0f, 0f, .2f), .000001f)
        assertTrue(LggoTransform.channel(0f, -.5f, -.5f, -.5f, 0f).isFinite())
    }
    @Test fun recipeRoundTripAndLegacyDefaultsPreserveIdentity() {
        val legacy = ColorRecipeParams.fromJson("{}")
        assertEquals(0f, legacy.gradeGammaRed, 0f)
        assertTrue(legacy.isDefault())
        val recipe = legacy.copy(gradeLiftRed = .2f, gradeGammaGreen = -.1f, gradeGainBlue = .3f, gradeOffsetRed = -.2f)
        assertFalse(recipe.isDefault())
        assertTrue(recipe.isSameAs(ColorRecipeParams.fromJson(recipe.toJson())))
        assertFalse(recipe.isSameAs(legacy))
    }
}
