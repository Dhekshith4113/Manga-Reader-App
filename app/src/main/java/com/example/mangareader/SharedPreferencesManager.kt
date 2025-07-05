package com.example.mangareader

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri

object SharedPreferencesManager {

    private const val PREFS_NAME = "MangaReaderPrefs"
    private const val KEY_LANDSCAPE_MODE_ENABLED = "landscape_mode_enabled"
    private const val KEY_RIGHT_TO_LEFT_ENABLED = "left_to_right_enabled"
    private const val KEY_RECREATE_ENABLED = "recreate_enabled"
    private const val KEY_LOAD_FILE_ENABLED = "load_file_enabled"
    private const val KEY_URI_STRING = "uri_string"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isDoublePageEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_LANDSCAPE_MODE_ENABLED, false)
    }

    fun setDoublePageEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_LANDSCAPE_MODE_ENABLED, enabled).apply()
    }

    fun isRTL(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_RIGHT_TO_LEFT_ENABLED, true)
    }

    fun setRTL(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_RIGHT_TO_LEFT_ENABLED, enabled).apply()
    }

    fun isRecreateEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_RECREATE_ENABLED, false)
    }

    fun setRecreateEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_RECREATE_ENABLED, enabled).apply()
    }

    fun isLoadFileEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_LOAD_FILE_ENABLED, false)
    }

    fun setLoadFileEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_LOAD_FILE_ENABLED, enabled).apply()
    }

    fun loadUriString(context: Context): String? {
        return getPrefs(context).getString(KEY_URI_STRING, null)
    }

    fun saveUriString(context: Context, uri: Uri?) {
        getPrefs(context).edit().putString(KEY_URI_STRING, uri?.toString()).apply()
    }

    fun setCurrentPage(context: Context, page: Int) {
        getPrefs(context).edit().putInt("current_page", page).apply()
    }

    fun getCurrentPage(context: Context): Int {
        return getPrefs(context).getInt("current_page", 0)
    }
}