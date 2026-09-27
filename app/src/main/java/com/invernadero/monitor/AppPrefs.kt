package com.invernadero.monitor

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object AppPrefs {
    private const val FILE = "invernadero_prefs"
    private const val KEY_THRESHOLD = "threshold_celsius"
    private const val KEY_NIGHT_MODE = "night_mode"

    fun getThreshold(context: Context): Int =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getInt(KEY_THRESHOLD, MainActivity.DEFAULT_THRESHOLD_CELSIUS.toInt())

    fun setThreshold(context: Context, value: Int) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putInt(KEY_THRESHOLD, value).apply()
    }

    /** Uno de AppCompatDelegate.MODE_NIGHT_*. Por defecto sigue el tema del sistema. */
    fun getNightMode(context: Context): Int =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getInt(KEY_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

    fun setNightMode(context: Context, mode: Int) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putInt(KEY_NIGHT_MODE, mode).apply()
    }
}
