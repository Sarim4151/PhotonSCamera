package com.hinnka.mycamera.preview

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Independent opt-in storage; no capture database migration is needed. */
class SubjectFocusPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("subject_focus", Context.MODE_PRIVATE)
    private val mutableEnabled = MutableStateFlow(preferences.getBoolean("enabled", false))
    private val mutableCategory = MutableStateFlow(preferences.getString("category", "auto")
        ?.takeIf { it in categories } ?: "auto")
    val enabled = mutableEnabled.asStateFlow()
    val category = mutableCategory.asStateFlow()
    fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean("enabled", enabled).apply()
        mutableEnabled.value = enabled
    }
    fun setCategory(category: String) {
        require(category in categories)
        preferences.edit().putString("category", category).apply()
        mutableCategory.value = category
    }
    companion object { val categories = listOf("auto", "person", "animals", "vehicles") }
}
