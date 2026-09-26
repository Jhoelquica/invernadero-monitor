package com.invernadero.monitor

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth

object NavHelper {

    fun setup(activity: AppCompatActivity, nav: BottomNavigationView, currentItemId: Int) {
        nav.selectedItemId = currentItemId
        nav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                currentItemId -> true
                R.id.nav_home -> {
                    if (activity is MainActivity) true else {
                        activity.finish()
                        true
                    }
                }
                R.id.nav_history -> {
                    activity.startActivity(Intent(activity, HistoryActivity::class.java))
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
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.account_dialog_title)
            .setMessage(activity.getString(R.string.account_dialog_signed_in, who))
            .setPositiveButton(R.string.account_dialog_logout) { _, _ ->
                FirebaseAuth.getInstance().signOut()
                activity.startActivity(
                    Intent(activity, LoginActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
                activity.finish()
            }
            .setNegativeButton(R.string.account_dialog_cancel, null)
            .show()
    }
}
