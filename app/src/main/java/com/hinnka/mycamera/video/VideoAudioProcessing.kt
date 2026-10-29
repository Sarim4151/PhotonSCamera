package com.hinnka.mycamera.video

enum class VideoWindReduction(val cutoffHz: Int) {
    OFF(0), LOW(65), MEDIUM(100), HIGH(160);
    companion object {
        fun fromPersistedName(value: String?): VideoWindReduction =
            entries.firstOrNull { it.name == value } ?: OFF
    }
}

enum class VideoNoiseReductionStatus { OFF, ACTIVE, UNAVAILABLE, FAILED, NO_AUDIO }
