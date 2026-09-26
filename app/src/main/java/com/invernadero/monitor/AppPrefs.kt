package com.invernadero.monitor

import android.content.Context

object AppPrefs {
    private const val FILE = "invernadero_prefs"
    private const val KEY_THRESHOLD = "threshold_celsius"

    fun getThreshold(context: Context): Int =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getInt(KEY_THRESHOLD, MainActivity.DEFAULT_THRESHOLD_CELSIUS.toInt())

    fun setThreshold(context: Context, value: Int) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putInt(KEY_THRESHOLD, value).apply()
    }
}
