package com.hinnka.mycamera.camera

import android.content.Context
import org.json.JSONObject

/** Dedicated versioned storage; no session, image, job or automatic start state is restored. */
class AdvancedCapturePreferences(context: Context) {
    private val preferences = context.getSharedPreferences("advanced_capture_selection", Context.MODE_PRIVATE)
    fun selected(): AdvancedCaptureSelection? = if (preferences.getBoolean("armed", false)) configuration() else null
    fun configuration(): AdvancedCaptureSelection? = runCatching {
        val json = JSONObject(preferences.getString("configuration", null) ?: return@runCatching null)
        if (json.optInt("version") !in 1..2) return@runCatching null
        val mode = AdvancedCameraMode.valueOf(json.getString("mode"))
        val options = AdvancedCaptureOptions(
            bufferSeconds = json.optInt("seconds", 3).coerceIn(1, 15),
            bufferFps = json.optInt("fps", 4).coerceIn(1, 8),
            bufferMaxEdge = json.optInt("edge", 1280).coerceIn(480, 1536),
            bufferMemoryMb = json.optInt("memory", 32).coerceIn(8, 128),
            exposureSpacingEv = json.optDouble("ev", 1.5).toFloat().finite(1.5f, .3f, 3f),
            fuseExposureBracket = json.optBoolean("fuse", true),
            durationSeconds = json.optInt("duration", 60).coerceIn(5, 3600),
            intensity = json.optDouble("intensity", 1.0).toFloat().finite(1f, .1f, 8f),
            astroHorizon = if (json.isNull("astroHorizon")) null else
                json.optDouble("astroHorizon", Double.NaN).toFloat().takeIf { it.isFinite() && it in .15f.. .9f },
            detailInputMaxEdge = json.optInt("detailEdge", 0).let {
                // 2048 was the legacy implicit default; source resolution is now the default.
                if (it == 0 || (json.optInt("version") == 1 && it == 2048)) 0 else it.coerceIn(640, 8192)
            },
        )
        AdvancedCaptureSelection(mode, json.optInt("frames", 6).coerceIn(if (mode == AdvancedCameraMode.HIGH_DETAIL) 6 else 2, 15),
            if (mode == AdvancedCameraMode.HIGH_DETAIL) 0 else json.optInt("gap", 2).coerceIn(0, 60), options)
    }.getOrNull()
    fun save(selection: AdvancedCaptureSelection) {
        val o = selection.options
        val json = JSONObject().put("version", 2).put("mode", selection.mode.name)
            .put("frames", selection.frames).put("gap", if (selection.mode == AdvancedCameraMode.HIGH_DETAIL) 0 else selection.gapSeconds)
            .put("seconds", o.bufferSeconds).put("fps", o.bufferFps).put("edge", o.bufferMaxEdge)
            .put("memory", o.bufferMemoryMb).put("ev", o.exposureSpacingEv.finite(1.5f, .3f, 3f)).put("fuse", o.fuseExposureBracket)
            .put("duration", o.durationSeconds).put("intensity", o.intensity.finite(1f, .1f, 8f)).put("detailEdge", o.detailInputMaxEdge)
            .put("astroHorizon", o.astroHorizon?.takeIf { it.isFinite() && it in .15f.. .9f } ?: JSONObject.NULL)
        preferences.edit().putString("configuration", json.toString()).putBoolean("armed", true).apply()
    }
    fun clearSelection() { preferences.edit().putBoolean("armed", false).apply() }
    private fun Float.finite(fallback: Float, min: Float, max: Float) = if (isFinite()) coerceIn(min, max) else fallback
}
