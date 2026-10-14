package com.rahul.leadzam

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

/**
 * Application subclass — runs before any Activity or Service.
 *
 * Its only job here is to create the notification channel.
 * Android 8+ (API 26) requires channels before you can post any notification;
 * creating a channel that already exists is a no-op, so it is safe to call
 * every time the process starts.
 */
class LeadzamApplication : android.app.Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        // Notification channels were introduced in Android 8.0 (API 26).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NotificationHelper.CHANNEL_ID,       // unique ID used when posting
                "Call requests",                     // name shown in system settings
                NotificationManager.IMPORTANCE_HIGH  // shows as heads-up notification
            ).apply {
                description = "Alerts for incoming call requests from the LeadZam CRM"
            }
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
