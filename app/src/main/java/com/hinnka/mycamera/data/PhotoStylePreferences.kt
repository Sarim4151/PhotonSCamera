package com.hinnka.mycamera.data

import android.content.Context
import com.hinnka.mycamera.model.PhotographicStyle
import com.hinnka.mycamera.model.WhiteBalanceIntent

/** Remembers the last edited controls, never overlays them onto a selected recipe. */
class PhotoStylePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("photographic_style", Context.MODE_PRIVATE)
    fun load() = PhotographicStyle(prefs.getFloat("tone", 0f), prefs.getFloat("warmth", 0f),
        prefs.getFloat("colour", 0f), prefs.getFloat("detail", 0f), prefs.getFloat("radius", 1f),
        prefs.getFloat("threshold", 0.005f)).normalized()
    fun save(value: PhotographicStyle) { value.normalized().let {
        prefs.edit().putFloat("tone", it.tone).putFloat("warmth", it.warmth)
            .putFloat("colour", it.colour).putFloat("detail", it.detail)
            .putFloat("radius", it.fineness).putFloat("threshold", it.threshold).apply()
    } }
    fun loadWhiteBalanceIntent() = runCatching {
        WhiteBalanceIntent.valueOf(prefs.getString("wb_intent", "AUTO") ?: "AUTO")
    }.getOrDefault(WhiteBalanceIntent.AUTO)
    fun saveWhiteBalanceIntent(value: WhiteBalanceIntent) {
        prefs.edit().putString("wb_intent", value.name).apply()
    }
}
