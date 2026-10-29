package com.hinnka.mycamera.lut

import android.opengl.GLES30
import com.hinnka.mycamera.model.ColorRecipeParams
import com.hinnka.mycamera.model.PhotographicStyle

internal object PhotographicStyleShader {
    val GLSL = """
        uniform vec3 uPhotographicStyle;
        uniform float uSharpnessRadius;
        uniform float uSharpnessThreshold;
        vec3 applyPhotographicStyle(vec3 c) {
            if (dot(abs(uPhotographicStyle), vec3(1.0)) < 0.000001) return c;
            float y = dot(c, vec3(0.2126, 0.7152, 0.0722));
            float chroma = max(c.r, max(c.g, c.b)) - min(c.r, min(c.g, c.b));
            // Broad warm skin locus; smoothly protect it instead of hard hue boundaries.
            float skin = smoothstep(0.0, 0.10, c.r-c.b) *
                (1.0-smoothstep(0.10, 0.30, abs((c.r-c.g)-(c.g-c.b)))) *
                smoothstep(0.02, 0.16, chroma) * (1.0-smoothstep(0.65, 0.95, chroma));
            float protect = 1.0 - 0.65 * skin;
            float tone = uPhotographicStyle.x * y * (1.0-y) * (2.0*y-1.0) * 0.9;
            c += vec3(tone * (1.0-0.3*skin));
            c += vec3(0.045, 0.006, -0.045) * uPhotographicStyle.y * protect *
                (0.3 + 0.7 * (1.0-abs(2.0*y-1.0)));
            c = mix(vec3(dot(c, vec3(0.2126,0.7152,0.0722))), c,
                1.0 + uPhotographicStyle.z * 0.35 * protect);
            return clamp(c, 0.0, 1.0);
        }
    """.trimIndent()
    fun bind(program: Int, recipe: ColorRecipeParams?) {
        val s = PhotographicStyle.fromRecipe(recipe ?: ColorRecipeParams.DEFAULT)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(program, "uPhotographicStyle"), s.tone, s.warmth, s.colour)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uSharpnessRadius"), s.fineness)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uSharpnessThreshold"), s.threshold)
    }
}
