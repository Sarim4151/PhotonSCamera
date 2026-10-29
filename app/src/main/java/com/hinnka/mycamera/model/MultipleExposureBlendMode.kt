package com.hinnka.mycamera.model

/** Conventional display-referred blends for the existing JPEG multiple-exposure workflow. */
enum class MultipleExposureBlendMode {
    AVERAGE, LIGHTEN, DARKEN, SCREEN, ADD;

    fun blend(base: Int, next: Int, frameIndex: Int): Int {
        fun channel(shift: Int): Int {
            val a = (base ushr shift) and 255
            val b = (next ushr shift) and 255
            return when (this) {
                AVERAGE -> a + (b - a) / frameIndex.coerceAtLeast(1)
                LIGHTEN -> maxOf(a, b)
                DARKEN -> minOf(a, b)
                SCREEN -> 255 - ((255 - a) * (255 - b) + 127) / 255
                ADD -> (a + b).coerceAtMost(255)
            }
        }
        return (255 shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    companion object {
        fun fromPersistedValue(value: String?): MultipleExposureBlendMode =
            entries.firstOrNull { it.name == value } ?: AVERAGE
    }
}
