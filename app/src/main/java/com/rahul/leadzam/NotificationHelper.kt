package com.rahul.leadzam

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

private const val TAG = "NotificationHelper"

/**
 * Builds and posts a heads-up notification for an incoming call request.
 *
 * Design notes:
 *  - We use CATEGORY_CALL so the system treats this as a call-like alert.
 *  - Visibility PUBLIC lets the content appear on the lock screen; since we
 *    only show name and number (no secret data), this is acceptable and helps
 *    the user act quickly. Change to VISIBILITY_PRIVATE for stronger privacy.
 *  - Both the content tap and the "Call" action launch MainActivity (singleTop)
 *    with extras, NOT a BroadcastReceiver. Android 12+ blocks notification
 *    trampolines (BR → Activity), so we go Activity-direct.
 *  - FLAG_IMMUTABLE is required on API 31+; FLAG_UPDATE_CURRENT lets the OS
 *    reuse the PendingIntent slot and overwrite its extras if the same request
 *    code is reused (which we avoid by using unique codes per requestId).
 */
object NotificationHelper {

    const val CHANNEL_ID = "call_requests"

    // Extras keys shared between the notification and MainActivity.
    const val EXTRA_ACTION    = "action"
    const val EXTRA_REQUEST_ID = "requestId"
    const val EXTRA_PHONE     = "phoneNumber"
    const val EXTRA_NAME      = "contactName"
    const val ACTION_DIAL     = "dial"

    /**
     * Posts a notification for [request] if POST_NOTIFICATIONS is granted.
     * Safe to call from a background thread (FirebaseMessagingService runs on
     * a worker thread).
     */
    fun postCallNotification(context: Context, request: CallRequest) {
        // Respect the runtime permission check before posting.
        val notifManager = NotificationManagerCompat.from(context)
        if (!notifManager.areNotificationsEnabled()) {
            Log.w(TAG, "Notifications disabled — skipping notification for ${request.id}")
            return
        }

        val displayName = request.contactName?.takeIf { it.isNotBlank() }
        val title = if (displayName != null) "Call $displayName?" else "Call ${request.phoneNumber}?"
        val text  = "${request.phoneNumber} • from LeadZam CRM"

        // A unique integer code for PendingIntents — derived from the requestId hash.
        // Using the same code for different requests would overwrite the previous intent's extras.
        val requestCode = request.id.hashCode()

        // Shared intent factory: opens MainActivity with action="dial" and request details.
        fun buildDialIntent(): Intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ACTION, ACTION_DIAL)
            putExtra(EXTRA_REQUEST_ID, request.id)
            putExtra(EXTRA_PHONE, request.phoneNumber)
            putExtra(EXTRA_NAME, request.contactName ?: "")
        }

        val pendingFlags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

        // Content tap PendingIntent (unique request code to avoid collisions).
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            requestCode,          // unique per request
            buildDialIntent(),
            pendingFlags
        )

        // "Call" action button PendingIntent (different request code slot).
        val callActionPendingIntent = PendingIntent.getActivity(
            context,
            requestCode + 1,      // +1 so it's a separate PendingIntent slot
            buildDialIntent(),
            pendingFlags
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)  // existing vector drawable
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            // PUBLIC: name+number are not secret; visible on lock screen for fast action.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)         // dismiss notification when tapped
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_call,  // system icon — no new library needed
                "Call",
                callActionPendingIntent
            )
            .build()

        // Notification ID derived from requestId hash — each request gets its own notif.
        val notificationId = request.id.hashCode()
        try {
            notifManager.notify(notificationId, notification)
            Log.d(TAG, "Notification posted for ${request.id}")
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS was revoked between the check and the post.
            Log.w(TAG, "POST_NOTIFICATIONS denied — notification not posted", e)
        }
    }

    /** Cancel the notification for a specific request (called on Dismiss). */
    fun cancelNotification(context: Context, requestId: String) {
        NotificationManagerCompat.from(context).cancel(requestId.hashCode())
    }
}
