package com.hinnka.mycamera.lut

import android.opengl.GLES30
import com.hinnka.mycamera.raw.RawEnginePreviewPlan
import com.hinnka.mycamera.raw.RawFullscreenQuad

/** GL-context-owned ISP sRGB engine bridge; shares preview's restoration and all engine bakes. */
internal class IspBitmapEngineGl {
    private val engine = RawEnginePreviewGl()
    private val quad = RawFullscreenQuad()
    private var program = 0

    fun render(plan: RawEnginePreviewPlan, inputTexture: Int, targetFramebuffer: Int, width: Int, height: Int) {
        check(engine.prepare(plan)) { "Unable to prepare ISP bitmap engine ${plan.engine}" }
        if (program == 0) program = quad.createProgram(FRAGMENT_SHADER, "ispBitmapEngine")
        check(program != 0) { "Unable to compile ISP bitmap engine sampling pass" }
        // Baking uploads resources and changes GL state. Establish every consumer binding again.
        GLES30.glUseProgram(program)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, targetFramebuffer)
        check(GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) == GLES30.GL_FRAMEBUFFER_COMPLETE) {
            "ISP bitmap engine framebuffer is incomplete"
        }
        GLES30.glViewport(0, 0, width, height)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, inputTexture)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "uInputTexture"), 0)
        engine.bind(GLES30.glGetUniformLocation(program, "uEnginePreviewLut"))
        quad.bindIdentityTextureMatrix(program)
        quad.draw(program)
        val error = GLES30.glGetError()
        check(error == GLES30.GL_NO_ERROR) { "ISP bitmap engine draw failed: $error" }
    }

    fun release() {
        engine.release()
        if (program != 0) GLES30.glDeleteProgram(program)
        program = 0
    }

    companion object {
        internal val FRAGMENT_SHADER = """
            #version 300 es
            precision highp float;
            precision highp int;
            precision highp sampler2D;
            in vec2 vTexCoord;
            out vec4 fragColor;
            uniform highp sampler2D uInputTexture;
            ${PreviewColorShaderModules.COLOR_TRANSFER_CORE}
            ${RawEnginePreviewGl.SAMPLING_GLSL}
            void main() {
                vec4 sampled = texture(uInputTexture, vTexCoord);
                fragColor = vec4(sampleEnginePreview(sampled.rgb), sampled.a);
            }
        """.trimIndent().trimStart()
    }
}
