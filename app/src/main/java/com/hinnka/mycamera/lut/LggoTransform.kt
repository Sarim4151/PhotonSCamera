package com.hinnka.mycamera.lut

import kotlin.math.pow

/** Display-referred RGB grading; all controls are deltas and zero is identity.
 * Kept in one shader function so preview, still export and video cannot drift. */
internal object LggoTransform {
    const val GLSL = """
        vec3 applyLggo(vec3 color, vec3 lift, vec3 gamma, vec3 gain, vec3 offset) {
            vec3 lifted = max(color + lift * (vec3(1.0) - color), vec3(0.0));
            return pow(lifted * (vec3(1.0) + gain), vec3(1.0) / (vec3(1.0) + gamma)) + offset;
        }
    """

    /** CPU reference for numerical verification, before the colour pass's final clipping. */
    fun channel(color: Float, lift: Float, gamma: Float, gain: Float, offset: Float): Float {
        val lifted = (color + lift * (1f - color)).coerceAtLeast(0f)
        return (lifted * (1f + gain)).pow(1f / (1f + gamma)) + offset
    }
}
