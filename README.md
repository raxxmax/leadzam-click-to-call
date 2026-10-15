# LeadZam Click-to-Call

A seamless, privacy-conscious click-to-call bridge between a Web CRM dashboard and an Android mobile device. When a sales rep clicks **Call** on any lead in the CRM, a data-only push notification is dispatched via Firebase Cloud Messaging (FCM) to the rep's Android phone, prompting the native phone dialer with the customer's number pre-filled.

---

## Architecture Overview

```mermaid
flowchart LR
    CRM[Web CRM UI<br/>Vanilla JS / HTML] -- "POST /api/dial" --> Backend[Node.js Express<br/>Backend Server]
    Backend -- "FCM Send (Data Message)" --> FCM[Firebase Cloud<br/>Messaging (FCM)]
    FCM -- "High-priority push" --> App[Android App<br/>Jetpack Compose]
    App -- "Intent.ACTION_DIAL" --> Dialer[Native Phone Dialer]
```

### Key Design & Safety Highlights
- **Safe Dialing (`ACTION_DIAL`)**: Uses `Intent.ACTION_DIAL` instead of `ACTION_CALL`. The phone dialer opens with the number pre-populated, requiring explicit user tap to initiate the call. No invasive `CALL_PHONE` runtime permission is required.
- **Data-Only FCM Payload**: The server sends data-only messages (no notification payload). Android's `DialMessagingService` handles incoming messages uniformly in both foreground and background states without system interference.
- **Foreground vs. Background UX**:
  - **Foreground**: Live Compose UI observes `CallRequestStore` and immediately displays an actionable call card (Call / Dismiss).
  - **Background / Locked Screen**: Posts a high-priority heads-up notification with a direct "Call" action.
- **E.164 Validation**: Both backend and client validate phone numbers to match international standard E.164 format (e.g. `+919876543210`).

---

## Directory Structure

```text
leadzam/
├── app/                        # Android application (Kotlin + Jetpack Compose)
│   ├── src/main/java/com/rahul/leadzam/
│   │   ├── CallRequest.kt            # Data model & payload parsing
│   │   ├── CallRequestStore.kt       # In-memory StateFlow store for requests
│   │   ├── DialMessagingService.kt   # FirebaseMessagingService message listener
│   │   ├── LeadzamApplication.kt     # App entry & notification channel setup
│   │   ├── MainActivity.kt           # Compose UI, dialer intent & token viewer
│   │   ├── NotificationHelper.kt     # Heads-up notification builder
│   │   └── PhoneNumbers.kt           # E.164 normalization & validation logic
│   ├── src/main/AndroidManifest.xml
│   ├── build.gradle.kts
│   └── google-services.json          # (Firebase Android configuration file)
├── backend/                    # Node.js Express server & CRM UI
│   ├── public/
│   │   ├── index.html                # CRM web dashboard
│   │   └── app.js                    # Lead UI logic & API caller
│   ├── .env.example                  # Environment configuration template
│   ├── package.json
│   ├── server.js                     # Express server & Firebase Admin SDK sender
│   └── serviceAccountKey.json        # (Firebase Admin SDK private key)
├── build.gradle.kts
└── settings.gradle.kts
```

---

## Prerequisites

Ensure you have the following installed and available:
- **Node.js** (v18.0.0 or higher) & **npm**
- **Android Studio** (Koala / Ladybug or newer)
- **Android SDK** (minSdk 26, targetSdk 37, compileSdk 37)
- **Physical Android Device** or **Android Emulator with Google Play Services** (required for FCM token registration)
- **Google Firebase Account**

---

## 1. Firebase Configuration

You will need a Firebase project to connect both the backend (Firebase Admin SDK) and the Android application (Firebase Cloud Messaging).

### Step 1.1: Create a Firebase Project
1. Go to the [Firebase Console](https://console.firebase.google.com/).
2. Click **Add project** and name it (e.g., `leadzam-click-to-call`).
3. Follow the setup wizard (Google Analytics can be disabled for testing).

### Step 1.2: Register the Android App
1. In project overview, click the **Android** icon to add an Android app.
2. Enter the exact Package Name:
   ```text
   com.rahul.leadzam
   ```
3. (Optional) Provide an App Nickname (e.g., `LeadZam Dialer`).
4. Click **Register app**.
5. Download `google-services.json`.
6. Place the downloaded file into the `app/` folder of this repository:
   ```text
   leadzam/app/google-services.json
   ```

### Step 1.3: Generate Firebase Admin Service Account Key
1. In the Firebase Console, navigate to **Project settings** (gear icon) → **Service accounts**.
2. Select **Firebase Admin SDK** (Node.js).
3. Click **Generate new private key** and confirm.
4. Rename the downloaded JSON file to `serviceAccountKey.json`.
5. Place this file inside the `backend/` folder:
   ```text
   leadzam/backend/serviceAccountKey.json
   ```
   > ⚠️ **Important Security Note**: Never commit `serviceAccountKey.json` or `.env` to Git. Both are included in `.gitignore`.

---

## 2. Android App Setup & Run

### Step 2.1: Open and Build Project
1. Open **Android Studio**.
2. Select **Open** and choose the root directory of this repository (`leadzam`).
3. Allow Gradle to sync dependencies.
4. Verify that `app/google-services.json` is present.

### Step 2.2: Run the App
1. Connect a physical Android device via USB (with USB Debugging enabled) or start an Emulator **with Google APIs / Google Play Store**.
2. Click **Run 'app'** (`Shift + F10`) in Android Studio.
3. Upon launch:
   - On **Android 13+ (API 33+)**, grant the **Notification Permission** (`POST_NOTIFICATIONS`) when prompted.
   - If denied, you will see a banner with a direct link to App Settings.

### Step 2.3: Retrieve FCM Device Registration Token
1. Scroll to the bottom of the Android app screen.
2. Tap **▼ Show device token**.
3. The app fetches the current FCM token. Once `Ready ✓` is displayed, tap **Copy Token**.
4. Keep this token handy for the backend configuration in the next section.

---

## 3. Backend Setup & Run

### Step 3.1: Install Dependencies
Open a terminal and navigate to the `backend/` directory:
```bash
cd backend
npm install
```

### Step 3.2: Configure Environment Variables
1. Copy the example configuration:
   ```bash
   cp .env.example .env
   ```
2. Open `.env` in your text editor:
   ```env
   PORT=3000
   GOOGLE_APPLICATION_CREDENTIALS=./serviceAccountKey.json
   DEVICE_FCM_TOKEN=paste_your_copied_android_device_token_here
   ```
3. Replace `paste_your_copied_android_device_token_here` with the token copied from the Android app in **Step 2.3**.

### Step 3.3: Start the Backend Server
Run:
```bash
npm start
```
The server will initialize the Firebase Admin SDK and start listening:
```text
Server running at http://localhost:3000
```

Verify backend health at:
[http://localhost:3000/api/health](http://localhost:3000/api/health) — returns `{"ok":true}`.

---

## 4. Running the CRM UI

The CRM Web UI is hosted directly from the Node.js Express backend (`backend/public/`).

1. Open your browser and navigate to:
   ```text
   http://localhost:3000
   ```
2. You will see sample mock leads with names, numbers, and stages:
   - **Rahul Sharma** (`+919876543210`)
   - **Priya Verma** (`+918765432109`)
   - **Amit Singh** (`+917654321098`)
3. Click **Call** next to any lead.
4. The UI transitions through statuses:
   - `Sending...`: Sending POST request to backend.
   - `Request sent to FCM`: Server successfully handed the message to Firebase Cloud Messaging.
   - Status automatically resets to `Ready` after 5 seconds.

---

## 5. End-to-End Testing Scenarios

### Scenario A: Foreground In-App Card
1. Keep the **LeadZam Dialer** Android app open on your screen.
2. In the CRM browser dashboard, click **Call** on any lead.
3. Within 1–2 seconds, a new card appears dynamically at the top of the Android app with:
   - Lead name and phone number
   - "From LeadZam CRM" tag & timestamp
   - **Call** and **Dismiss** buttons
4. Tap **Call** → Android native phone dialer immediately opens with the number filled in.
5. Tap **Dismiss** → Dismisses the card from the list.

### Scenario B: Background / Heads-Up Notification
1. Press the **Home** button on your Android device (or lock the phone).
2. On the CRM dashboard, click **Call** on a lead.
3. Your device rings/vibrates with a high-priority heads-up notification:
   - Title: `Call <Name>?`
   - Content: `<Phone Number> • from LeadZam CRM`
   - Action Button: **Call**
4. Tap either the notification banner or the **Call** action:
   - The app launches directly to the system dialer pre-populated with the lead's number.

### Scenario C: Request Expiry & Duplicate Handling
- **Request Expiry**: Call requests older than 10 minutes display an **Expired** badge in the app.
- **De-duplication**: Each request contains a unique `requestId` UUID; duplicates are safely ignored by `CallRequestStore`.

---

## API Reference

### `POST /api/dial`
Dispatches an FCM high-priority data message to the registered mobile device.

- **URL**: `/api/dial`
- **Method**: `POST`
- **Headers**: `Content-Type: application/json`
- **Request Body**:
  ```json
  {
    "phoneNumber": "+919876543210",
    "contactName": "Rahul Sharma"
  }
  ```
- **Responses**:
  - `200 OK`:
    ```json
    {
      "status": "accepted",
      "requestId": "9c1b72b8-2081-4be3-b9dc-048754162e21",
      "fcmMessageId": "projects/leadzam-xxx/messages/17123456789"
    }
    ```
  - `400 Bad Request`: Invalid phone number format or invalid token.
  - `410 Gone`: Token has expired or registration is invalid (fresh token required from app).
  - `500 Internal Server Error`: `DEVICE_FCM_TOKEN` not configured in `.env`.
  - `502 Bad Gateway`: Firebase FCM gateway error.

---

## Troubleshooting

| Issue | Cause | Solution |
| :--- | :--- | :--- |
| `serviceAccountKey.json not found` on backend start | Missing or misconfigured key path | Ensure `serviceAccountKey.json` is in `backend/` and `GOOGLE_APPLICATION_CREDENTIALS=./serviceAccountKey.json` in `.env`. |
| `Device token is not configured on the server` (HTTP 500) | Placeholder token still in `.env` | Copy the token from Android app UI and set `DEVICE_FCM_TOKEN` in `backend/.env`. Restart the server. |
| Android token says `Error: ...` | Emulator has no Google Play Services | Use an Android Virtual Device (AVD) image labeled **Google Play** or test on a physical device. |
| Notification not popping up in background | Notification permission disabled or battery saver blocking FCM | Ensure Notification permission is granted in Android Settings → Apps → LeadZam → Notifications. Check battery optimization. |
| CRM status says `Cannot reach server. Is it running?` | Express server is stopped or port blocked | Run `npm start` in `backend/` and verify access to `http://localhost:3000/api/health`. |

---

## License

This project is licensed under the [MIT License](file:///Users/rahulsharma/AndroidStudioProjects/leadzam/LICENSE).
