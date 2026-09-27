package com.invernadero.monitor

import androidx.appcompat.app.AppCompatActivity

/** Aplica un cross-fade entre la actividad actual y la siguiente (o la anterior, al volver). */
@Suppress("DEPRECATION")
fun AppCompatActivity.applyFadeTransition() {
    overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
}
