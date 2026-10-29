package com.hinnka.mycamera.lut

import android.opengl.GLES30
import com.hinnka.mycamera.utils.PLog
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Invert the reported ISP transfer before applying the selected RAW color engine. */
internal class PreviewIspInputGl {
    private var texture = 0
    private var tableSize = SIZE
    private var lastCurve: List<List<Float>>? = null

    fun bind(location: Int, unit: Int, reported: List<List<Float>>?) {
        if (location < 0) return
        val curves = PreviewIspToneCurve.validated(reported)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
        val allocate = texture == 0
        if (allocate) {
            val limit = IntArray(1)
            GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, limit, 0)
            tableSize = minOf(SIZE, limit[0])
            check(tableSize >= 2) { "Invalid ISP inverse curve texture limit: ${limit[0]}" }
            texture = IntArray(1).also { GLES30.glGenTextures(1, it, 0) }[0]
        }
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        if (allocate || curves != lastCurve) {
            PLog.d("PreviewIspInput", if (curves != null)
                "Restoring scene light from reported ISP curve: points=${curves.map { it.size / 2 }}"
            else "Using sRGB fallback: reported ISP curve ${if (reported == null) "absent" else "invalid"}; scene-light restoration is approximate")
            val samples = ByteBuffer.allocateDirect(tableSize * 4 * Float.SIZE_BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer()
            val inverseTables = curves?.map { PreviewIspToneCurve.inverseTable(it, tableSize) }
            for (index in 0 until tableSize) {
                val encoded = index.toFloat() / (tableSize - 1)
                // FAST/HIGH_QUALITY ISP paths often omit TONEMAP_CURVE. The
                // SurfaceTexture is display-encoded; an arbitrary ACR3 inverse
                // invents exposure and washes out the engine preview.
                val linear = if (encoded <= 0.04045f) encoded / 12.92f
                    else Math.pow(((encoded + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
                for (channel in 0..2) samples.put(inverseTables?.get(channel)?.get(index) ?: linear)
                samples.put(1f)
            }
            samples.flip()
            if (allocate) {
                GLES30.glTexStorage2D(GLES30.GL_TEXTURE_2D, 1, GLES30.GL_RGBA32F, tableSize, 1)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            }
            // This upload consumes client memory, independent of prior pixel-buffer users.
            GLES30.glBindBuffer(GLES30.GL_PIXEL_UNPACK_BUFFER, 0)
            GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 4)
            GLES30.glPixelStorei(GLES30.GL_UNPACK_ROW_LENGTH, 0)
            GLES30.glPixelStorei(GLES30.GL_UNPACK_SKIP_PIXELS, 0)
            GLES30.glPixelStorei(GLES30.GL_UNPACK_SKIP_ROWS, 0)
            GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, tableSize, 1,
                GLES30.GL_RGBA, GLES30.GL_FLOAT, samples)
            lastCurve = curves
        }
        GLES30.glUniform1i(location, unit)
    }

    fun reset() { texture = 0; lastCurve = null }
    fun release() {
        if (texture != 0) GLES30.glDeleteTextures(1, intArrayOf(texture), 0)
        reset()
    }

    companion object {
        private const val SIZE = 4096
        val GLSL = """
            uniform highp sampler2D uInverseAcr3Texture;
            vec3 restorePreviewScene(vec3 encoded) {
                highp int last = textureSize(uInverseAcr3Texture, 0).x - 1;
                vec3 position = clamp(encoded, 0.0, 1.0) * float(last);
                ivec3 lower = ivec3(floor(position));
                ivec3 upper = min(lower + ivec3(1), ivec3(last));
                vec3 lo = vec3(texelFetch(uInverseAcr3Texture, ivec2(lower.r, 0), 0).r,
                    texelFetch(uInverseAcr3Texture, ivec2(lower.g, 0), 0).g,
                    texelFetch(uInverseAcr3Texture, ivec2(lower.b, 0), 0).b);
                vec3 hi = vec3(texelFetch(uInverseAcr3Texture, ivec2(upper.r, 0), 0).r,
                    texelFetch(uInverseAcr3Texture, ivec2(upper.g, 0), 0).g,
                    texelFetch(uInverseAcr3Texture, ivec2(upper.b, 0), 0).b);
                return mix(lo, hi, position - vec3(lower));
            }
        """.trimIndent()
    }
}
