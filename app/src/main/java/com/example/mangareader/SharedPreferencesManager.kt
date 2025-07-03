package com.example.mangareader

import android.content.Context
import android.content.SharedPreferences

object SharedPreferencesManager {

    private const val PREFS_NAME = "MinimaPrefs"
    private const val KEY_LANDSCAPE_MODE_ENABLED = "landscape_mode_enabled"
    private const val KEY_LEFT_TO_RIGHT_ENABLED = "left_to_right_enabled"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isLandscapeModeEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_LANDSCAPE_MODE_ENABLED, false)
    }

    fun setLandscapeModeEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_LANDSCAPE_MODE_ENABLED, enabled).apply()
    }

    fun isLeftToRightEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_LEFT_TO_RIGHT_ENABLED, true)
    }

    fun setLeftToRightEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_LEFT_TO_RIGHT_ENABLED, enabled).apply()
    }

}