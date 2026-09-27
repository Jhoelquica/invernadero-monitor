package com.invernadero.monitor

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class InvernaderoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppPrefs.getNightMode(this))
    }
}
