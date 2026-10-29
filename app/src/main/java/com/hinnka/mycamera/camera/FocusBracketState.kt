package com.hinnka.mycamera.camera

enum class FocusBracketOutcome { IDLE, RUNNING, MERGING, COMPLETE, STOPPED, FAILED }
data class FocusBracketState(
    val outcome: FocusBracketOutcome = FocusBracketOutcome.IDLE,
    val total: Int = 0,
    val accepted: Int = 0,
    val mergedPhotoId: String? = null,
    val mergeFailed: Boolean = false,
) {
    val isRunning: Boolean get() = outcome == FocusBracketOutcome.RUNNING || outcome == FocusBracketOutcome.MERGING
}
