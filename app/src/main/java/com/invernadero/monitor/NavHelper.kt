package com.invernadero.monitor

import android.content.Intent
import android.content.res.Configuration
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.firebase.auth.FirebaseAuth

object NavHelper {

    fun setup(activity: AppCompatActivity, nav: BottomNavigationView, currentItemId: Int) {
        nav.selectedItemId = currentItemId
        nav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                currentItemId -> true
                R.id.nav_home -> {
                    if (activity is MainActivity) {
                        true
                    } else {
                        activity.finish()
                        activity.applyFadeTransition()
                        true
                    }
                }
                R.id.nav_history -> {
                    activity.startActivity(Intent(activity, HistoryActivity::class.java))
                    activity.applyFadeTransition()
                    false
                }
                R.id.nav_account -> {
                    showAccountDialog(activity)
                    false
                }
                else -> false
            }
        }
    }

    private fun showAccountDialog(activity: AppCompatActivity) {
        val user = FirebaseAuth.getInstance().currentUser
        val who = user?.email ?: user?.displayName.orEmpty()

        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_account, null)
        view.findViewById<android.widget.TextView>(R.id.tvAccountEmail).text =
            activity.getString(R.string.account_dialog_signed_in, who)

        val switchDarkMode = view.findViewById<MaterialSwitch>(R.id.switchDarkMode)
        switchDarkMode.isChecked = isCurrentlyDark(activity)
        switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            val mode = if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            AppPrefs.setNightMode(activity, mode)
            AppCompatDelegate.setDefaultNightMode(mode)
        }

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.account_dialog_title)
            .setView(view)
            .setPositiveButton(R.string.account_dialog_logout) { _, _ ->
                FirebaseAuth.getInstance().signOut()
                activity.startActivity(
                    Intent(activity, LoginActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
                activity.applyFadeTransition()
                activity.finish()
            }
            .setNegativeButton(R.string.account_dialog_cancel, null)
            .show()
    }

    private fun isCurrentlyDark(activity: AppCompatActivity): Boolean {
        val nightModeFlags = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES
    }
}
