package com.invernadero.monitor

import android.content.Intent
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

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
        val displayName = user?.displayName?.takeIf { it.isNotBlank() }
        val email = user?.email.orEmpty()

        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_account, null)
        view.findViewById<TextView>(R.id.tvAccountName).text = displayName ?: email
        val tvEmail = view.findViewById<TextView>(R.id.tvAccountEmail)
        if (displayName != null) {
            tvEmail.text = email
            tvEmail.visibility = View.VISIBLE
        } else {
            tvEmail.visibility = View.GONE
        }

        val avatar = view.findViewById<ShapeableImageView>(R.id.ivAccountAvatar)
        user?.photoUrl?.let { photoUrl ->
            activity.lifecycleScope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    runCatching {
                        URL(photoUrl.toString()).openStream().use { BitmapFactory.decodeStream(it) }
                    }.getOrNull()
                }
                if (bitmap != null) {
                    avatar.setImageBitmap(bitmap)
                    avatar.visibility = View.VISIBLE
                }
            }
        }

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
            .setPositiveButton(R.string.account_dialog_logout) { _, _ -> confirmLogout(activity) }
            .setNegativeButton(R.string.account_dialog_cancel, null)
            .show()
    }

    private fun confirmLogout(activity: AppCompatActivity) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.account_dialog_logout_confirm_title)
            .setMessage(R.string.account_dialog_logout_confirm_message)
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
