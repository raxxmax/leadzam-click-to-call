package com.rahul.leadzam

import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

private const val TAG = "DialMessagingService"

/**
 * Receives FCM messages while the app is running (foreground or background)
 * because the backend sends DATA-ONLY messages (no `notification` field).
 * Data-only messages always deliver to onMessageReceived, unlike notification
 * messages which the system handles when the app is in the background.
 */
class DialMessagingService : FirebaseMessagingService() {

    /**
     * Called by Firebase when the device gets a new registration token.
     * This happens on first install, after a token refresh, or when the app
     * is restored to a new device.
     *
     * In this assignment the token is displayed in the UI; we just log here.
     * Production would upload the new token to the server.
     *
     * @Suppress: the Firebase Android SDK marks this as @Deprecated on the Java side
     * due to a Kotlin/Java interop issue, but it is still the correct override to use.
     */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Do NOT log the token value in release builds — treat it like a password.
        Log.d(TAG, "FCM token refreshed (value hidden)")
    }

    /**
     * Called every time an FCM message arrives (foreground or background).
     */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "onMessageReceived: data keys = ${message.data.keys}")

        // Step 1: Parse the FCM data map → CallRequest (null = invalid message).
        val request = CallRequest.fromData(message.data)
        if (request == null) {
            // fromData() already logged the specific reason via Log.w.
            return
        }

        // Step 2: Add to the in-memory store (duplicate IDs are ignored inside add()).
        CallRequestStore.add(request)

        // Step 3: Decide foreground vs background.
        //
        // ProcessLifecycleOwner tracks the lifecycle of the entire app process.
        // When the state is at least STARTED it means at least one Activity is
        // visible to the user (i.e., the app is in the foreground).
        val isForegrounded = ProcessLifecycleOwner.get()
            .lifecycle
            .currentState
            .isAtLeast(Lifecycle.State.STARTED)

        if (isForegrounded) {
            // The in-app UI (FcmTokenScreen / call cards) will automatically update
            // because it observes CallRequestStore.requests via collectAsState().
            // No notification needed — the user can already see the card.
            Log.d(TAG, "App is foreground — card will appear in UI")
        } else {
            // App is in the background or has been swiped away.
            // Post a heads-up notification so the user is alerted.
            NotificationHelper.postCallNotification(applicationContext, request)
        }
    }
}
