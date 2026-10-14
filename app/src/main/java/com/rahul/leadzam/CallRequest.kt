package com.rahul.leadzam

import android.util.Log

private const val TAG = "CallRequest"

/**
 * A single "call this lead" request received from the CRM backend via FCM.
 *
 * @param id             Unique UUID from the backend's requestId field.
 * @param contactName    CRM contact name — null or blank means "Unknown contact".
 * @param phoneNumber    Validated E.164 phone number.
 * @param receivedAtMillis  System clock when we received the FCM message.
 */
data class CallRequest(
    val id: String,
    val contactName: String?,
    val phoneNumber: String,
    val receivedAtMillis: Long
) {
    companion object {
        // How long a request is considered "active" before it is shown as expired.
        private const val EXPIRY_MS = 10L * 60 * 1000  // 10 minutes

        /**
         * Builds a [CallRequest] from the FCM data payload map.
         *
         * Returns null (and logs a warning) if any required field is missing or invalid.
         * This keeps [DialMessagingService] clean: it just calls fromData() and checks null.
         *
         * Expected keys: type, requestId, phoneNumber, contactName, sentAt.
         */
        fun fromData(data: Map<String, String>): CallRequest? {
            // Guard 1: only process our own message type.
            if (data["type"] != "dial_request") {
                Log.w(TAG, "fromData: unexpected type '${data["type"]}' — ignoring")
                return null
            }

            // Guard 2: requestId must be present.
            val id = data["requestId"]
            if (id.isNullOrBlank()) {
                Log.w(TAG, "fromData: missing requestId — ignoring")
                return null
            }

            // Guard 3: phoneNumber must pass E.164 validation.
            val rawPhone = data["phoneNumber"]
            val phone = normalizeAndValidatePhone(rawPhone)
            if (phone == null) {
                Log.w(TAG, "fromData: invalid phoneNumber '$rawPhone' — ignoring")
                return null
            }

            // contactName is optional; an empty string becomes null so the UI shows
            // "Unknown contact" instead of a blank line.
            val name = data["contactName"]?.takeIf { it.isNotBlank() }

            return CallRequest(
                id = id,
                contactName = name,
                phoneNumber = phone,
                receivedAtMillis = System.currentTimeMillis()
            )
        }

        /** True when the request is older than EXPIRY_MS milliseconds. */
        fun isExpired(request: CallRequest): Boolean =
            System.currentTimeMillis() - request.receivedAtMillis > EXPIRY_MS
    }
}
