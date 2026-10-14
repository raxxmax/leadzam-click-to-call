package com.rahul.leadzam

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import com.rahul.leadzam.ui.theme.LeadzamTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// A simple tag so we can filter our log messages in Logcat.
private const val TAG = "LeadZam"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LeadzamTheme {
                // Scaffold gives us the full-screen surface with correct insets.
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    FcmTokenScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

/**
 * Main screen that fetches and displays the FCM registration token.
 *
 * State machine:
 *   "Fetching token..." → success: shows the token
 *                       → failure: shows the error message
 */
@Composable
fun FcmTokenScreen(modifier: Modifier = Modifier) {
    // ── State ──────────────────────────────────────────────────────────────
    // `status` is what the user sees in the status line (one sentence).
    var status by remember { mutableStateOf("Fetching token…") }

    // `token` holds the raw FCM token string; empty until we have one.
    var token by remember { mutableStateOf("") }

    val context = LocalContext.current

    // ── Side-effect: fetch token once when this composable first appears ───
    LaunchedEffect(Unit) {
        // We switch to the IO thread (Dispatchers.IO) for the network call.
        // Tasks.await() blocks the coroutine until Firebase returns the token.
        withContext(Dispatchers.IO) {
            try {
                // Call the Task-based API and block (on IO thread) until done.
                val registrationToken = Tasks.await(
                    FirebaseMessaging.getInstance().getToken()
                )
                Log.d(TAG, "FCM token: $registrationToken")
                // Switch back to Main thread to update Compose state.
                withContext(Dispatchers.Main) {
                    token = registrationToken
                    status = "Ready ✓"
                }
            } catch (e: Exception) {
                val errorMsg = e.localizedMessage ?: "Unknown error"
                Log.e(TAG, "FCM token fetch failed: $errorMsg", e)
                withContext(Dispatchers.Main) {
                    status = "Error: $errorMsg"
                    token = ""
                }
            }
        }
    }

    // ── UI ─────────────────────────────────────────────────────────────────
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())   // allows scrolling if token is long
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {

        // ① App title
        Text(
            text = "LeadZam Dialer",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        // ② Status line — changes from "Fetching…" → "Ready ✓" or error text
        Text(
            text = status,
            style = MaterialTheme.typography.bodyLarge,
            color = if (status.startsWith("Error"))
                MaterialTheme.colorScheme.error
            else
                MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        // ③ Show the token box only when we actually have a token
        if (token.isNotEmpty()) {

            // ③a  Label
            Text(
                text = "Your FCM Token",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.align(Alignment.Start)
            )

            // ③b  Selectable card — user can tap-and-hold to select text manually
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                // SelectionContainer enables long-press text selection inside it.
                SelectionContainer {
                    Text(
                        text = token,
                        modifier = Modifier.padding(12.dp),
                        fontFamily = FontFamily.Monospace,  // monospace = easy to read tokens
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            // ③c  "Copy token" button
            Button(
                onClick = {
                    // Get the system clipboard service.
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                            as ClipboardManager
                    // Wrap the token in a ClipData object (label + text).
                    val clip = ClipData.newPlainText("FCM Token", token)
                    // Push it onto the clipboard.
                    clipboard.setPrimaryClip(clip)
                    // Confirm to the user with a short Toast.
                    Toast.makeText(context, "Token copied!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Copy Token")
            }
        }
    }
}