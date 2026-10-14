require("dotenv").config();
const fs = require("fs");
const path = require("path");
const crypto = require("crypto");
const express = require("express");
const { initializeApp, applicationDefault } = require("firebase-admin/app");
const { getMessaging } = require("firebase-admin/messaging");

// Initialise Firebase using the service account key
const keyPath = process.env.GOOGLE_APPLICATION_CREDENTIALS;
if (!keyPath || !fs.existsSync(path.resolve(keyPath))) {
  console.error("ERROR: serviceAccountKey.json not found. Check the path in .env.");
  process.exit(1);
}
initializeApp({ credential: applicationDefault() });

const app = express();
app.use(express.json());
app.use(express.static("public"));

// Clean the phone number: strip spaces, dashes and brackets, require +<country code><number>
function cleanPhone(raw) {
  if (typeof raw !== "string") return null;
  const cleaned = raw.replace(/[\s\-()]/g, "");
  return /^\+[1-9]\d{6,14}$/.test(cleaned) ? cleaned : null;
}

app.get("/api/health", (req, res) => res.json({ ok: true }));

app.post("/api/dial", async (req, res) => {
  const { phoneNumber, contactName } = req.body || {};

  const phone = cleanPhone(phoneNumber);
  if (!phone) {
    return res.status(400).json({ error: "Invalid phone number. Use this format: +919876543210" });
  }
  const name = typeof contactName === "string" ? contactName.trim().slice(0, 80) : "";

  const token = process.env.DEVICE_FCM_TOKEN;
  if (!token || token === "paste_token_here") {
    return res.status(500).json({ error: "Device token is not configured on the server. Check .env." });
  }

  const requestId = crypto.randomUUID();

  // Data-only message (no notification block). All values must be strings.
  const message = {
    token,
    data: {
      type: "dial_request",
      phoneNumber: phone,
      contactName: name,
      requestId,
      sentAt: new Date().toISOString(),
    },
    android: { priority: "high", ttl: 60000 },
  };

  try {
    const fcmMessageId = await getMessaging().send(message);
    // "accepted" means FCM took the message, not that the phone received it.
    return res.json({ status: "accepted", requestId, fcmMessageId });
  } catch (err) {
    console.error("FCM error:", err.code || err.message);
    if (err.code === "messaging/registration-token-not-registered") {
      return res.status(410).json({ error: "Device token is expired or invalid. Copy a fresh token from the app." });
    }
    if (err.code === "messaging/invalid-argument" || err.code === "messaging/invalid-registration-token") {
      return res.status(400).json({ error: "Device token format is invalid." });
    }
    return res.status(502).json({ error: "Could not send the message through FCM. Please try again." });
  }
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => console.log(`Server running at http://localhost:${PORT}`));
