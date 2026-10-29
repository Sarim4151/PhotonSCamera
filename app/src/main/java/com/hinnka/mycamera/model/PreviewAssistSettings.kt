package com.hinnka.mycamera.model

/** Stable persisted IDs; colours are display RGB, not photo recipe transforms. */
enum class PeakingColor(val red: Float, val green: Float, val blue: Float) {
    RED(1f, 0.1f, 0.1f), YELLOW(1f, 1f, 0f), BLUE(0f, 0.5f, 1f), WHITE(1f, 1f, 1f);

    companion object {
        fun fromPersistedValue(value: String?): PeakingColor =
            entries.firstOrNull { it.name == value } ?: RED
    }
}

enum class PeakingSensitivity(val threshold: Float) {
    // Sobel magnitude divided by four: a straight luminance step measures its contrast.
    LOW(0.14f), MEDIUM(0.07f), HIGH(0.025f);

    companion object {
        fun fromPersistedValue(value: String?): PeakingSensitivity =
            entries.firstOrNull { it.name == value } ?: MEDIUM
    }
}

/** Immutable UI-to-GL snapshot. Integer thresholds cannot contain non-finite values. */
data class PreviewAssistSettings(
    val peakingColor: PeakingColor = PeakingColor.RED,
    val peakingSensitivity: PeakingSensitivity = PeakingSensitivity.MEDIUM,
    val zebraEnabled: Boolean = false,
    val zebraThresholdPercent: Int = 95,
    /** Display-referred preview luminance monitor; never part of capture colour processing. */
    val falseColorEnabled: Boolean = false,
) {
    fun normalized(): PreviewAssistSettings = copy(
        zebraThresholdPercent = zebraThresholdPercent.coerceIn(70, 100)
    )
}
