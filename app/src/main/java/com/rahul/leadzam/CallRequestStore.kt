package com.rahul.leadzam

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory singleton store for pending [CallRequest]s.
 *
 * Uses Kotlin's StateFlow so that any Compose UI observing it automatically
 * recomposes when the list changes.
 *
 * NOTE for reviewer / production:  This is intentionally in-memory.
 * Killing the process clears all requests.  A production app would persist
 * them in a Room database (one table, auto-delete after EXPIRY_MS) and
 * inject the DAO via Hilt.  That was out of scope for this assignment.
 */
object CallRequestStore {

    // _requests is mutable internally; exposed as read-only StateFlow to the rest of the app.
    private val _requests = MutableStateFlow<List<CallRequest>>(emptyList())

    /** Observe this from Compose with .collectAsState(). */
    val requests: StateFlow<List<CallRequest>> = _requests.asStateFlow()

    /**
     * Adds [request] to the front of the list (newest first).
     * Silently ignores requests whose [CallRequest.id] is already present,
     * preventing duplicate cards if the server retries the same FCM message.
     */
    fun add(request: CallRequest) {
        val current = _requests.value
        // Duplicate guard — same id should never show twice.
        if (current.any { it.id == request.id }) {
            return
        }
        // Prepend so the newest request appears at the top.
        _requests.value = listOf(request) + current
    }

    /**
     * Removes the request with the given [id].
     * Called when the user taps "Call" or "Dismiss" in the UI.
     */
    fun remove(id: String) {
        _requests.value = _requests.value.filter { it.id != id }
    }
}
