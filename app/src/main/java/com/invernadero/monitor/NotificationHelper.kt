package com.invernadero.monitor

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object NotificationHelper {

    const val CHANNEL_ID = "temperature_alerts"
    const val IRRIGATION_CHANNEL_ID = "irrigation_alerts"
    private const val NOTIFICATION_ID = 1001
    private const val FROST_NOTIFICATION_ID = 1002
    private const val IRRIGATION_NOTIFICATION_ID = 1003

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.notification_channel_description)
                }
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    IRRIGATION_CHANNEL_ID,
                    context.getString(R.string.notification_irrigation_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.notification_irrigation_channel_description)
                }
            )
        }
    }

    fun showTemperatureAlert(context: Context, temperature: Double, threshold: Double) {
        notify(
            context,
            CHANNEL_ID,
            NOTIFICATION_ID,
            context.getString(R.string.notification_title),
            context.getString(R.string.notification_text, temperature, threshold)
        )
    }

    fun showFrostAlert(context: Context, temperature: Double, minimum: Int) {
        notify(
            context,
            CHANNEL_ID,
            FROST_NOTIFICATION_ID,
            context.getString(R.string.notification_frost_title),
            context.getString(R.string.notification_frost_text, temperature, minimum)
        )
    }

    /** Avisa que un riego terminó sin que subiera la humedad del suelo. */
    fun showIrrigationAlert(context: Context, record: IrrigationRecord) {
        notify(
            context,
            IRRIGATION_CHANNEL_ID,
            IRRIGATION_NOTIFICATION_ID,
            context.getString(R.string.notification_irrigation_title),
            context.getString(
                R.string.notification_irrigation_text,
                record.durationSeconds,
                record.soilBefore,
                record.soilAfter
            )
        )
    }

    private fun notify(context: Context, channelId: String, id: Int, title: String, text: String) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
