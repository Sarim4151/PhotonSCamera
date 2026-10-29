package com.hinnka.mycamera.lut

import android.opengl.GLES30

/** CUBE input domain is a coordinate transform, never a resampling of the table. */
internal object LutDomainShader {
    val GLSL = """
        uniform vec3 uLutDomainMin;
        uniform vec3 uLutDomainMax;
        vec3 normalizeLutDomain(vec3 inputValue) {
            return clamp((inputValue - uLutDomainMin) / (uLutDomainMax - uLutDomainMin), 0.0, 1.0);
        }
    """.trimIndent()
    fun bind(program: Int, config: LutConfig?) {
        val low = config?.domainMin ?: floatArrayOf(0f, 0f, 0f)
        val high = config?.domainMax ?: floatArrayOf(1f, 1f, 1f)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(program, "uLutDomainMin"), low[0], low[1], low[2])
        GLES30.glUniform3f(GLES30.glGetUniformLocation(program, "uLutDomainMax"), high[0], high[1], high[2])
    }
}
