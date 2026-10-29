package com.hinnka.mycamera.ui.camera

import android.content.Context
import androidx.annotation.StringRes
import com.hinnka.mycamera.R
import org.json.JSONArray
import org.json.JSONObject

enum class ProQuickControl(@StringRes val label: Int) {
    FLASH(R.string.flash), TIMER(R.string.timer), LIVE_PHOTO(R.string.settings_use_live_photo),
    HISTOGRAM(R.string.quick_histogram_domain), GRID(R.string.settings_grid_lines),
    LEVEL(R.string.settings_level_indicator), ZEBRA(R.string.preview_assist_zebras),
    PEAKING(R.string.settings_focus_peaking), AE_LOCK(R.string.quick_ae_lock),
    AF_LOCK(R.string.quick_af_lock), INTERVAL(R.string.interval_capture_short);
    companion object {
        val defaults = listOf(FLASH, TIMER, LIVE_PHOTO, HISTOGRAM, INTERVAL)
        const val MAX_SLOTS = 6
    }
}

/** Built-in UI workspaces change only ordered quick controls, never capture or colour settings. */
enum class CameraWorkspace(@StringRes val label: Int, val slots: List<ProQuickControl>) {
    SIMPLE(R.string.workspace_simple, listOf(ProQuickControl.FLASH, ProQuickControl.TIMER, ProQuickControl.LIVE_PHOTO)),
    PRO(R.string.workspace_pro, listOf(ProQuickControl.HISTOGRAM, ProQuickControl.ZEBRA, ProQuickControl.PEAKING, ProQuickControl.AE_LOCK, ProQuickControl.AF_LOCK, ProQuickControl.LEVEL)),
    STREET(R.string.workspace_street, listOf(ProQuickControl.FLASH, ProQuickControl.GRID, ProQuickControl.LEVEL, ProQuickControl.AE_LOCK, ProQuickControl.AF_LOCK)),
    PORTRAIT(R.string.workspace_portrait, listOf(ProQuickControl.FLASH, ProQuickControl.TIMER, ProQuickControl.GRID, ProQuickControl.PEAKING, ProQuickControl.AF_LOCK)),
    ASTRO(R.string.workspace_astro, listOf(ProQuickControl.TIMER, ProQuickControl.INTERVAL, ProQuickControl.HISTOGRAM, ProQuickControl.PEAKING, ProQuickControl.LEVEL));
}

/** Ordered slots and named layouts are stored separately from camera capture settings. */
class ProControlsPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("pro_quick_controls", Context.MODE_PRIVATE)
    private fun decode(array: JSONArray): List<ProQuickControl> = (0 until array.length())
        .mapNotNull { index -> ProQuickControl.entries.find { it.name == array.optString(index) } }
        .distinct().take(ProQuickControl.MAX_SLOTS)
    fun loadSlots(): List<ProQuickControl> = runCatching {
        preferences.getString("slots", null)?.let { decode(JSONArray(it)) } ?: ProQuickControl.defaults
    }.getOrDefault(ProQuickControl.defaults)
    fun saveSlots(slots: List<ProQuickControl>) {
        preferences.edit().putString("slots", JSONArray(slots.distinct().take(ProQuickControl.MAX_SLOTS).map { it.name }).toString()).apply()
    }
    fun showInViewfinder(): Boolean = preferences.getBoolean("show_in_viewfinder", false)
    fun setShowInViewfinder(enabled: Boolean) {
        preferences.edit().putBoolean("show_in_viewfinder", enabled).apply()
    }
    fun presets(): Map<String, List<ProQuickControl>> = runCatching {
        val json = JSONObject(preferences.getString("presets", "{}") ?: "{}")
        json.keys().asSequence().sorted().associateWith { decode(json.getJSONArray(it)) }
    }.getOrDefault(emptyMap())
    fun savePreset(name: String, slots: List<ProQuickControl>) {
        val cleaned = name.trim().take(40)
        if (cleaned.isEmpty()) return
        val values = presets().toMutableMap().apply { put(cleaned, slots.distinct().take(ProQuickControl.MAX_SLOTS)) }
        writePresets(values)
    }
    fun deletePreset(name: String) = writePresets(presets() - name)
    private fun writePresets(values: Map<String, List<ProQuickControl>>) {
        val json = JSONObject()
        values.forEach { (name, slots) -> json.put(name, JSONArray(slots.map { it.name })) }
        preferences.edit().putString("presets", json.toString()).apply()
    }
}
