package com.hinnka.mycamera.raw

/** Original scene-linear sRGB operators; no camera-brand emulation or extra texture pass. */
internal object OriginalToneShaders {
    // Map luminance, then compress chroma towards that luminance to fit the RGB cube.
    // This preserves neutral axes, channel order and hue direction without channel clipping.
    private val common = """
        const vec3 ORIGINAL_LUMA = vec3(0.2126, 0.7152, 0.0722);
        vec3 originalFitGamut(vec3 c, float y) {
            vec3 delta = c - vec3(y);
            float scale = 1.0;
            for (int i = 0; i < 3; ++i) {
                if (delta[i] > 0.000001) scale = min(scale, (1.0 - y) / delta[i]);
                if (delta[i] < -0.000001) scale = min(scale, -y / delta[i]);
            }
            return clamp(vec3(y) + clamp(scale, 0.0, 1.0) * delta, 0.0, 1.0);
        }
        vec3 originalTone(vec3 c, float contrast, float saturation) {
            float y = max(dot(c, ORIGINAL_LUMA), 0.0);
            if (y <= 0.000001) return vec3(0.0);
            // The positive scalar curve is monotonic; 18% scene grey stays 18%.
            float p = pow(y / 0.18, contrast);
            float t = p / (p + 4.55555556);
            vec3 chroma = (c / y - vec3(1.0)) * (t * saturation);
            return originalFitGamut(vec3(t) + chroma, t);
        }
    """.trimIndent()

    private fun definition(body: String) = RawEngineToneShaderDefinition(
        engineUniforms = RawEngineTonePass.OUTPUT_TRANSFORM_COMBINED_UNIFORMS,
        engineFunctions = common + "\nvec3 applyEngineTone(vec3 color) {\n" + body + "\n}",
        includeAdobeProfilePipeline = false,
    )

    // An original interpretation of a restrained photographic finish. Sony does not
    // publish its proprietary Creative Look transform; no Sony sensor matrix is substituted.
    val SONY_NATURAL = definition("""
        float y = max(dot(color, ORIGINAL_LUMA), 0.0);
        if (y <= 0.000001) return vec3(0.0);
        // Matched value and slope at middle grey, bounded exponential shoulder.
        float t;
        if (y <= 0.18) {
            t = 0.18 * pow(y / 0.18, 1.08);
        } else {
            t = 0.18 + 0.82 * (1.0 - exp(-1.08 * (y - 0.18) / 0.82));
        }
        float saturation = mix(0.97, 0.85, smoothstep(1.0, 8.0, y));
        vec3 c = vec3(t) + (color / y - vec3(1.0)) * (t * saturation);
        return uOutputTransform * originalFitGamut(c, t);
    """.trimIndent())

    val NEUTRAL = definition("return uOutputTransform * originalTone(color, 1.0, 1.0);")
    val PORTRAIT = definition("return uOutputTransform * originalTone(color, 0.88, 0.92);")
    val SILVER = definition("""
        float y = max(dot(color, ORIGINAL_LUMA), 0.0);
        return uOutputTransform * originalTone(vec3(y), 1.18, 0.0);
    """.trimIndent())
}

internal class SonyNaturalToneAlgorithm(quad: RawFullscreenQuad) :
    RawRenderingEngineToneAlgorithm(quad, OriginalToneShaders.SONY_NATURAL)

internal class NeutralToneAlgorithm(quad: RawFullscreenQuad) :
    RawRenderingEngineToneAlgorithm(quad, OriginalToneShaders.NEUTRAL)
internal class SoftPortraitToneAlgorithm(quad: RawFullscreenQuad) :
    RawRenderingEngineToneAlgorithm(quad, OriginalToneShaders.PORTRAIT)
internal class SilverMonochromeToneAlgorithm(quad: RawFullscreenQuad) :
    RawRenderingEngineToneAlgorithm(quad, OriginalToneShaders.SILVER)
