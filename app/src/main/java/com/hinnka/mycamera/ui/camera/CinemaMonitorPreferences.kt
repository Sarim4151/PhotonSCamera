package com.hinnka.mycamera.ui.camera

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import com.hinnka.mycamera.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CinemaFrameGuide(val ratio: Float?, @StringRes val label: Int) {
    OFF(null, R.string.cinema_guide_off), WIDE(16f / 9f, R.string.cinema_guide_wide),
    DCI(1.85f, R.string.cinema_guide_dci), SCOPE(2.39f, R.string.cinema_guide_scope),
    SQUARE(1f, R.string.cinema_guide_square),
    PORTRAIT(9f / 16f, R.string.cinema_guide_portrait),
    FOUR_FIVE(4f / 5f, R.string.cinema_guide_four_five),
    FOUR_THREE(4f / 3f, R.string.cinema_guide_four_three),
    FOURTEEN_NINE(14f / 9f, R.string.cinema_guide_fourteen_nine),
    TWO(2f, R.string.cinema_guide_two),
    SCOPE_235(2.35f, R.string.cinema_guide_235),
    SCOPE_240(2.4f, R.string.cinema_guide_240),
    ULTRA(2.76f, R.string.cinema_guide_ultra)
}
enum class CinemaClockMode(@StringRes val label: Int) {
    OFF(R.string.cinema_clock_off), RECORD_RUN(R.string.cinema_clock_record),
    TIME_OF_DAY(R.string.cinema_clock_day)
}
data class CinemaMonitorSettings(
    val guide: CinemaFrameGuide = CinemaFrameGuide.OFF,
    val safeAreaPercent: Int = 90,
    val safeAreaEnabled: Boolean = false,
    val anamorphicFactor: Float = 1f,
    val clock: CinemaClockMode = CinemaClockMode.OFF
) {
    /** Fit a horizontally desqueezed image in the existing preview bounds without clipping. */
    val previewScaleY: Float get() = 1f / anamorphicFactor
}

/** Shared display-only preferences. No setting here changes the recorded image or metadata. */
class CinemaMonitorPreferences private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("cinema_monitor", Context.MODE_PRIVATE)
    private fun read() = CinemaMonitorSettings(
        guide = CinemaFrameGuide.entries.find { it.name == prefs.getString("guide", null) } ?: CinemaFrameGuide.OFF,
        safeAreaPercent = prefs.getInt("safe_percent", 90).coerceIn(5, 100),
        safeAreaEnabled = prefs.getBoolean("safe_enabled", false),
        anamorphicFactor = prefs.getFloat("anamorphic", 1f).let { if (it in anamorphicFactors) it else 1f },
        clock = CinemaClockMode.entries.find { it.name == prefs.getString("clock", null) } ?: CinemaClockMode.OFF
    )
    private val mutableSettings = MutableStateFlow(read())
    val settings: StateFlow<CinemaMonitorSettings> = mutableSettings.asStateFlow()
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> mutableSettings.value = read() }
    init { prefs.registerOnSharedPreferenceChangeListener(listener) }
    fun update(settings: CinemaMonitorSettings) {
        prefs.edit().putString("guide", settings.guide.name)
            .putInt("safe_percent", settings.safeAreaPercent.coerceIn(5, 100))
            .putBoolean("safe_enabled", settings.safeAreaEnabled)
            .putFloat("anamorphic", settings.anamorphicFactor.takeIf { it in anamorphicFactors } ?: 1f)
            .putString("clock", settings.clock.name).apply()
        mutableSettings.value = read()
    }
    companion object {
        val anamorphicFactors = listOf(1f, 1.33f, 1.5f, 1.55f, 1.8f, 2f)
        @Volatile private var instance: CinemaMonitorPreferences? = null
        fun get(context: Context): CinemaMonitorPreferences = instance ?: synchronized(this) {
            instance ?: CinemaMonitorPreferences(context).also { instance = it }
        }
    }
}
