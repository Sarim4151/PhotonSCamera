package com.hinnka.mycamera.lut

import com.hinnka.mycamera.processor.BokehStyle
import java.io.File
import java.util.Properties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class BokehShadersTest {
    @Test
    fun offlineBokehDeclaresEveryUniformRequiredByItsRenderer() {
        val shader = Shaders.PSF_SPLAT_FRAGMENT_SHADER
        val expectedUniforms = listOf(
            "uDepthTexture",
            "uHighlightSourceTexture",
            "uLayerColorTexture",
            "uDepthMatrix",
            "uMaxBlurRadius",
            "uAperture",
            "uFocusDepth",
            "uTexelSize",
        )

        expectedUniforms.forEach { uniform ->
            assertTrue(
                "$uniform must be declared by the offline bokeh shader",
                Regex("""uniform\s+\w+\s+$uniform\s*;""").containsMatchIn(shader),
            )
        }
    }

    @Test
    fun compactHighlightShaderDeclaresEveryUniformRequiredByItsRenderer() {
        val shader = Shaders.COMPACT_BOKEH_HIGHLIGHT_FRAGMENT_SHADER
        val expectedUniforms = listOf(
            "uInputTexture",
            "uDepthTexture",
            "uDepthMatrix",
            "uMaxBlurRadius",
            "uAperture",
            "uFocusDepth",
            "uTexelSize",
            "uMinNeighborhoodLumaDifference",
            "uLinearInput",
        )

        expectedUniforms.forEach { uniform ->
            assertTrue(
                "$uniform must be declared by the compact-highlight shader",
                Regex("""uniform\s+\w+\s+$uniform\s*;""").containsMatchIn(shader),
            )
        }
    }

    @Test
    fun bubbleBokehUsesIntegratedCenterWeightedPsf() {
        val bokehShader = Shaders.PSF_SPLAT_FRAGMENT_SHADER

        assertTrue(bokehShader.contains("float softEdge = 1.0 - smoothstep(0.70, 1.05, lenP)"))
        assertTrue(bokehShader.contains("float radialEnergy = exp(-lenP * lenP * 1.55)"))
        assertTrue(bokehShader.contains("float centerWeightedTransmission = mix("))
        assertFalse(bokehShader.contains("centerHighlightLinear"))
        assertFalse(bokehShader.contains("integratedHighlight"))
        assertFalse(bokehShader.contains("bubbleHighlightWeight"))
        assertFalse(bokehShader.contains("highlightAccColor"))
        assertFalse(bokehShader.contains("highlightKernelWeight"))
    }

    @Test
    fun bokehOnlyReconstructsCompactHighlightsWithDefinedApertureEdges() {
        val compact = Shaders.COMPACT_BOKEH_HIGHLIGHT_FRAGMENT_SHADER
        val psf = Shaders.PSF_SPLAT_FRAGMENT_SHADER
        val analytic = Shaders.ANALYTIC_BOKEH_HIGHLIGHT_FRAGMENT_SHADER
        // Classification remains separate from the background PSF and optical disc.
        listOf("PROBE_DIRECTIONS[16]", "allRingSamplesAreDarker", "nearRingRadius",
            "middleRingRadius", "farRingRadius", "coreBrightnessThreshold",
            "brightCoreSampleCount", "neighborhoodContrastGate", "localMaximumGate").forEach {
            assertTrue("Missing compact-highlight guard: $it", compact.contains(it))
        }
        assertFalse(psf.contains("hdrBoost"))
        assertFalse(psf.contains("smoothstep(1.0, 0.88"))
        assertTrue(psf.contains("focusedSurfaceProtection"))
        assertTrue(psf.contains("sourceVisibility"))
        assertTrue(analytic.contains("if (normalizedDistance >= 1.0) discard"))
        assertTrue(analytic.contains("vSignal * (0.55 * transmission)"))
        assertTrue(analytic.contains("vec3(0.18)"))
        // Soap bubbles have a dedicated energy-normalized profile, not the analytic disc.
        assertTrue(Shaders.SOAP_BUBBLE_FRAGMENT_SHADER.contains("bubbleChannelProfile"))
        assertTrue(Shaders.SOAP_BUBBLE_FRAGMENT_SHADER.contains("if (rho >= BUBBLE_EXTENT) discard"))
    }

    @Test
    fun analyticHighlightsUseTheSameFieldDependentBiotarShapeAsBackgroundBokeh() {
        val vertex = Shaders.ANALYTIC_BOKEH_HIGHLIGHT_VERTEX_SHADER
        val fragment = Shaders.ANALYTIC_BOKEH_HIGHLIGHT_FRAGMENT_SHADER
        val psf = Shaders.PSF_SPLAT_FRAGMENT_SHADER
        listOf("float radialScale = mix(1.0, 0.54, fieldStrength)",
            "float tangentialScale = mix(1.0, 1.08, fieldStrength)",
            "vec2 tangential = vec2(-radial.y, radial.x)").forEach {
            assertTrue("analytic: $it", vertex.contains(it))
            assertTrue("background: $it", psf.contains(it))
        }
        assertTrue(vertex.contains("smoothstep(0.12, 1.15, fieldRadius)"))
        assertTrue(fragment.contains("smoothstep(0.38, 1.0, normalizedDistance)"))
        assertTrue(fragment.contains("float shoulderStrength = mix(0.025, 0.045, vFieldStrength)"))
        assertFalse(fragment.contains("uniform int uBokehStyle"))
        assertTrue(Shaders.SOAP_BUBBLE_VERTEX_SHADER.contains("flat out vec4 vShapePhase"))
        assertTrue(Shaders.SOAP_BUBBLE_FRAGMENT_SHADER.contains("flat in vec4 vShapePhase"))
    }

    @Test
    fun bokehRadiusUsesSceneDepthSpanAndApertureScale() {
        val shaders = listOf(
            Shaders.COMPACT_BOKEH_HIGHLIGHT_FRAGMENT_SHADER,
            Shaders.PSF_SPLAT_FRAGMENT_SHADER,
            Shaders.BOKEH_COMPOSITE_FRAGMENT_SHADER,
        )

        shaders.forEach { shader ->
            assertTrue(shader.contains("float availableFocusSpan = max("))
            assertTrue(shader.contains("float normalizedGap = clamp(gap / availableFocusSpan"))
            assertTrue(shader.contains("float defocus = pow(normalizedGap, 1.25)"))
            assertTrue(shader.contains("float apertureScale = min(1.4 / max(uAperture, 0.7), 1.25)"))
            assertTrue(shader.contains("defocus * uMaxBlurRadius * apertureScale"))
        }

        val renderer = File(
            "src/main/java/com/hinnka/mycamera/processor/OglBokehProcessor.kt"
        ).readText()
        assertTrue(renderer.contains("originalImage.width.toFloat() / 26.0f"))
        assertTrue(renderer.contains("val normalizedGap = (gap / availableFocusSpan)"))
        assertTrue(renderer.contains("sourceRadiusPixels * 1.05f"))
        assertTrue(renderer.contains("MIN_BUBBLE_COC_PIXELS = 10f"))
        assertTrue(renderer.contains("MIN_BUBBLE_DEPTH_GAP = 0.3f"))
        assertTrue(renderer.contains("HIGHLIGHT_MIN_CENTER_SPACING_SCALE = 0.9f"))
        assertTrue(renderer.contains("val sourceSignalScale = if (isPreExistingBokeh)"))
        assertTrue(renderer.contains("soapBubbleStyle = soapBubbleStyle"))
    }

    @Test
    fun bokehStylePersistenceKeepsExistingBubbleEditsNatural() {
        assertEquals(BokehStyle.DEFAULT, BokehStyle.fromPersistedName("DEFAULT"))
        assertEquals(BokehStyle.NATURAL, BokehStyle.fromPersistedName("BUBBLE"))
        assertEquals(BokehStyle.BUBBLE, BokehStyle.fromPersistedName("SOAP_BUBBLE"))
    }

    @Test
    fun bokehCompositeDeclaresEveryUniformRequiredByItsRenderer() {
        val shader = Shaders.BOKEH_COMPOSITE_FRAGMENT_SHADER
        val expectedUniforms = listOf(
            "uOriginalTexture",
            "uBokehTexture",
            "uHighlightTexture",
            "uDepthTexture",
            "uDepthMatrix",
            "uMaxBlurRadius",
            "uAperture",
            "uFocusDepth",
            "uDepthTexelSize",
            "uLinearInput",
        )

        expectedUniforms.forEach { uniform ->
            assertTrue(
                "$uniform must be declared by the bokeh composite shader",
                Regex("""uniform\s+\w+\s+$uniform\s*;""").containsMatchIn(shader),
            )
        }
    }

    @Test
    fun focusPlaneProtectionSupportsForegroundAndBackgroundDefocus() {
        val compactHighlightShader = Shaders.COMPACT_BOKEH_HIGHLIGHT_FRAGMENT_SHADER
        val bokehShader = Shaders.PSF_SPLAT_FRAGMENT_SHADER
        val compositeShader = Shaders.BOKEH_COMPOSITE_FRAGMENT_SHADER

        listOf(compactHighlightShader, bokehShader, compositeShader).forEach { shader ->
            assertTrue(shader.contains("abs(uFocusDepth - depth) - 0.015"))
            assertFalse(shader.contains("max(uFocusDepth - depth - 0.015"))
        }
        assertTrue(compactHighlightShader.contains("if (centerLuma <= 0.50)"))
        assertTrue(compactHighlightShader.contains("depthCoherenceGate"))
        assertTrue(compactHighlightShader.contains("maximumCoreDepthDelta"))
        assertFalse(compositeShader.contains("localDefocusCoverage"))
        assertTrue(compositeShader.contains("for (int y = -2; y <= 2; y++)"))
        assertTrue(compositeShader.contains("for (int x = -2; x <= 2; x++)"))
        assertTrue(compositeShader.contains("foregroundDefocusCoverage"))
        assertTrue(compositeShader.contains("sampleDepth - centerDepth"))
        assertTrue(compositeShader.contains("float foregroundSpill = smoothstep("))
        assertTrue(compositeShader.contains("0.65,"))
        assertTrue(compositeShader.contains(") * 0.76;"))
        assertTrue(compositeShader.contains("(1.0 - defocusMix) * (1.0 - foregroundSpill)"))
        assertFalse(compositeShader.contains("bokehMix = max(bokehMix, foregroundSpill)"))
        assertTrue(compositeShader.contains("backgroundWithHighlights"))
        assertFalse(compositeShader.contains("protectedFocusCoc"))
        assertTrue(
            compositeShader.contains(
                "mix(originalColor.rgb, backgroundWithHighlights, bokehMix)"
            )
        )
    }

    @Test
    fun layeredDepthPassesDeclareTheirGuideAndFocusInputs() {
        val upsample = Shaders.JBU_UPSAMPLE_FRAGMENT_SHADER
        val refine = Shaders.DEPTH_REFINE_FRAGMENT_SHADER

        assertTrue(upsample.contains("uniform sampler2D uSubjectMask;"))
        assertTrue(upsample.contains("uniform vec2 uMaskTexelSize;"))
        assertTrue(upsample.contains("uniform vec4 uMaskBounds;"))
        assertTrue(refine.contains("uniform int uProtectSubject;"))
        listOf(upsample, refine).forEach { shader ->
            listOf("uHighResGuide", "uFocusDepth", "uLinearInput").forEach { uniform ->
                assertTrue(Regex("""uniform\s+\w+\s+$uniform\s*;""").containsMatchIn(shader))
            }
            assertTrue(shader.startsWith("#version 300 es"))
        }
    }

    @Test
    fun offlineBokehPassesAvailableNdkShaderValidator() {
        val localPropertiesSdk = listOf(File("local.properties"), File("../local.properties"))
            .firstOrNull(File::isFile)
            ?.inputStream()
            ?.use { input ->
                Properties().apply { load(input) }.getProperty("sdk.dir")
            }
        val sdkRoot = System.getenv("ANDROID_SDK_ROOT")
            ?: System.getenv("ANDROID_HOME")
            ?: localPropertiesSdk
        val validator = sdkRoot?.let(::File)
            ?.resolve("ndk")
            ?.listFiles()
            ?.sortedByDescending { it.name }
            ?.asSequence()
            ?.mapNotNull { ndk ->
                ndk.resolve("shader-tools")
                    .walkTopDown()
                    .firstOrNull { it.isFile && it.nameWithoutExtension == "glslc" }
            }
            ?.firstOrNull()
        assumeTrue("Android NDK glslc is unavailable", validator != null)

        val shaders = listOf(
            "frag" to Shaders.JBU_UPSAMPLE_FRAGMENT_SHADER,
            "frag" to Shaders.DEPTH_REFINE_FRAGMENT_SHADER,
            "frag" to Shaders.DEPTH_READBACK_FRAGMENT_SHADER,
            "frag" to Shaders.BOKEH_LAYER_COLOR_FRAGMENT_SHADER,
            "frag" to Shaders.COMPACT_BOKEH_HIGHLIGHT_FRAGMENT_SHADER,
            "frag" to Shaders.BUBBLE_SOURCE_FRAGMENT_SHADER,
            "frag" to Shaders.PSF_SPLAT_FRAGMENT_SHADER,
            "frag" to Shaders.psfSplatFragmentShader(true),
            "frag" to Shaders.psfSplatFragmentShader(
                naturalStyle = true,
                soapBubbleStyle = true,
            ),
            "vert" to Shaders.ANALYTIC_BOKEH_HIGHLIGHT_VERTEX_SHADER,
            "frag" to Shaders.ANALYTIC_BOKEH_HIGHLIGHT_FRAGMENT_SHADER,
            "vert" to Shaders.SOAP_BUBBLE_VERTEX_SHADER,
            "frag" to Shaders.SOAP_BUBBLE_FRAGMENT_SHADER,
            "frag" to Shaders.BOKEH_COMPOSITE_FRAGMENT_SHADER,
        )
        shaders.forEachIndexed { index, (stage, shader) ->
            assertTrue("shader $index must start with #version for Mali drivers", shader.startsWith("#version 300 es"))
            val sourceFile = File.createTempFile("offline-bokeh-$index-", ".$stage")
            val outputFile = File.createTempFile("offline-bokeh-$index-", ".spv")
            try {
                // glslc emits SPIR-V and therefore requires GLSL ES 3.10 or newer.
                // Only the validator copy needs the higher version; the production
                // shader remains GLSL ES 3.00 for the app's OpenGL ES 3.0 renderer.
                sourceFile.writeText(shader.replaceFirst("#version 300 es", "#version 310 es"))
                val process = ProcessBuilder(
                    checkNotNull(validator).absolutePath,
                    "--target-env=opengl",
                    "-fauto-map-locations",
                    "-fauto-bind-uniforms",
                    "-fshader-stage=$stage",
                    sourceFile.absolutePath,
                    "-o",
                    outputFile.absolutePath,
                ).redirectErrorStream(true).start()
                val output = process.inputStream.bufferedReader().use { it.readText() }

                assertEquals("shader $index: $output", 0, process.waitFor())
            } finally {
                sourceFile.delete()
                outputFile.delete()
            }
        }
    }
}
