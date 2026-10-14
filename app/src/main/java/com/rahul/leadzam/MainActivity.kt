package com.rahul.leadzam

import android.Manifest
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import com.rahul.leadzam.ui.theme.LeadzamTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle a notification tap that launched (or relaunched) this Activity.
        handleDialIntent(intent)

        setContent {
            LeadzamTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    /**
     * Called when the Activity is already running (singleTop) and a new intent arrives —
     * e.g. the user taps a second notification while the app is open.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDialIntent(intent)
    }

    /**
     * Reads the "action=dial" extras posted by [NotificationHelper] and opens the dialer.
     *
     * The notification stores phoneNumber and contactName in the extras so the dial
     * still works even if the process was killed (and the in-memory store is empty).
     */
    private fun handleDialIntent(intent: Intent?) {
        if (intent?.getStringExtra(NotificationHelper.EXTRA_ACTION) != NotificationHelper.ACTION_DIAL) return

        val requestId = intent.getStringExtra(NotificationHelper.EXTRA_REQUEST_ID) ?: return

        // Prefer the phone from extras (survives process death) over the store.
        val phoneFromExtras = intent.getStringExtra(NotificationHelper.EXTRA_PHONE)
        val nameFromExtras  = intent.getStringExtra(NotificationHelper.EXTRA_NAME)

        // If the store still has the request, remove it (dismisses the in-app card too).
        CallRequestStore.remove(requestId)
        // Cancel the notification that brought us here.
        NotificationHelper.cancelNotification(this, requestId)

        // Determine which number to dial.
        val phoneToDial = phoneFromExtras?.takeIf { it.isNotBlank() }
            ?: CallRequestStore.requests.value.find { it.id == requestId }?.phoneNumber

        if (phoneToDial == null) {
            Log.w(TAG, "handleDialIntent: no phone number found for $requestId")
            Toast.makeText(this, "Request no longer available", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "handleDialIntent: dialling $requestId")
        launchDialer(this, phoneToDial, nameFromExtras)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helper: opens the system dialer with a pre-filled number (never auto-dials)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Launches Intent.ACTION_DIAL so the user sees the number and must press
 * the call button themselves.  Never uses ACTION_CALL (would require CALL_PHONE
 * permission and would auto-dial without consent).
 */
fun launchDialer(context: Context, phoneNumber: String, label: String? = null) {
    val dialIntent = Intent(Intent.ACTION_DIAL).apply {
        // Uri.fromParts("tel", number, null) is the correct way to build a tel: URI.
        data = Uri.fromParts("tel", phoneNumber, null)
    }
    try {
        context.startActivity(dialIntent)
    } catch (e: android.content.ActivityNotFoundException) {
        Log.e(TAG, "No dialer app found for $phoneNumber", e)
        Toast.makeText(context, "No dialer app found", Toast.LENGTH_SHORT).show()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Root composable — decides what to show
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // ── Notification permission (API 33+) ─────────────────────────────────
    // notifGranted: true if POST_NOTIFICATIONS is already granted (or API < 33).
    var notifGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            else true  // API < 33 doesn't require runtime permission
        )
    }

    // Launcher for the POST_NOTIFICATIONS system dialog.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notifGranted = granted
        if (!granted) {
            Log.w(TAG, "POST_NOTIFICATIONS denied by user")
        }
    }

    // Request the permission once when the screen first composes (API 33+ only).
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notifGranted) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // ── Observe call requests ──────────────────────────────────────────────
    // collectAsState() subscribes to the StateFlow; Compose recomposes automatically
    // whenever CallRequestStore.add() or .remove() is called.
    val requests by CallRequestStore.requests.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // ① App title
        Text(
            text = "LeadZam Dialer",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        )

        // ② Notification-off banner (shown only when permission is denied)
        if (!notifGranted) {
            NotificationDeniedBanner(
                onOpenSettings = {
                    // Open system notification settings for this app.
                    val settingsIntent = Intent(
                        android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
                    ).apply {
                        putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                    context.startActivity(settingsIntent)
                }
            )
            Spacer(Modifier.height(8.dp))
        }

        // ③ Call request cards (or empty-state text)
        if (requests.isEmpty()) {
            // Empty state — center it vertically in the remaining space.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No call requests yet.\nClick Call in the CRM dashboard.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics {
                        contentDescription = "No pending call requests"
                    }
                )
            }
        } else {
            // LazyColumn renders only the visible cards — efficient for any list size.
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(requests, key = { it.id }) { request ->
                    CallRequestCard(
                        request = request,
                        onCall = {
                            launchDialer(context, request.phoneNumber, request.contactName)
                            CallRequestStore.remove(request.id)
                            NotificationHelper.cancelNotification(context, request.id)
                        },
                        onDismiss = {
                            CallRequestStore.remove(request.id)
                            NotificationHelper.cancelNotification(context, request.id)
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))

        // ④ Device token section (collapsed, always at the bottom)
        DeviceTokenSection()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Call request card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun CallRequestCard(
    request: CallRequest,
    onCall: () -> Unit,
    onDismiss: () -> Unit
) {
    val expired = CallRequest.isExpired(request)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Call request from ${request.contactName ?: "Unknown contact"}"
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Source label
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "From LeadZam CRM",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                // "Expired" badge if older than 10 minutes
                if (expired) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "Expired",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // Contact name
            Text(
                text = request.contactName ?: "Unknown contact",
                style = MaterialTheme.typography.titleMedium
            )

            // Phone number
            Text(
                text = request.phoneNumber,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Received time
            Text(
                text = "Received: ${formatTime(request.receivedAtMillis)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(Modifier.height(12.dp))

            // Action buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onCall,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = "Call ${request.phoneNumber}" }
                ) {
                    Text("Call")
                }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = "Dismiss call request" }
                ) {
                    Text("Dismiss")
                }
            }
        }
    }
}

// Formats a millis timestamp as a short time string, e.g. "14:32:05".
private fun formatTime(millis: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(millis))

// ─────────────────────────────────────────────────────────────────────────────
// Notification-denied banner
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun NotificationDeniedBanner(onOpenSettings: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Notifications are off. You will not be alerted about call requests while the app is in the background.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onOpenSettings) {
                Text(
                    "Settings",
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Device token section (collapsed at the bottom)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun DeviceTokenSection() {
    var expanded by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Tap to load token") }
    var token by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column {
        TextButton(
            onClick = {
                expanded = !expanded
                if (expanded && token.isEmpty()) status = "Fetching…"
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (expanded) "▲ Hide device token" else "▼ Show device token",
                style = MaterialTheme.typography.labelMedium
            )
        }

        if (expanded) {
            // Fetch token when section is first expanded.
            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    try {
                        @Suppress("DEPRECATION")
                        val t = Tasks.await(FirebaseMessaging.getInstance().getToken())
                        withContext(Dispatchers.Main) {
                            token = t
                            status = "Ready ✓"
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            status = "Error: ${e.localizedMessage}"
                        }
                    }
                }
            }

            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                color = if (status.startsWith("Error"))
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.primary
            )

            if (token.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    SelectionContainer {
                        Text(
                            text = token,
                            modifier = Modifier.padding(10.dp),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("FCM Token", token))
                        Toast.makeText(context, "Token copied!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Copy Token")
                }
            }
        }
    }
}