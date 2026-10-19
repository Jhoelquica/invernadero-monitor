package com.invernadero.monitor

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object AppPrefs {
    private const val FILE = "invernadero_prefs"
    private const val KEY_THRESHOLD = "threshold_celsius"
    private const val KEY_NIGHT_MODE = "night_mode"
    private const val KEY_SOIL_THRESHOLD = "soil_threshold_percent"
    private const val KEY_FROST_MINIMUM = "frost_minimum_celsius"

    fun getThreshold(context: Context): Int =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getInt(KEY_THRESHOLD, MainActivity.DEFAULT_THRESHOLD_CELSIUS.toInt())

    fun setThreshold(context: Context, value: Int) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putInt(KEY_THRESHOLD, value).apply()
    }

    fun getSoilThreshold(context: Context): Int =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getInt(KEY_SOIL_THRESHOLD, IrrigationLogic.DEFAULT_SOIL_THRESHOLD)

    fun setSoilThreshold(context: Context, value: Int) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putInt(KEY_SOIL_THRESHOLD, value).apply()
    }

    fun getFrostMinimum(context: Context): Int =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getInt(KEY_FROST_MINIMUM, AlertTracker.DEFAULT_FROST_CELSIUS)

    fun setFrostMinimum(context: Context, value: Int) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putInt(KEY_FROST_MINIMUM, value).apply()
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
