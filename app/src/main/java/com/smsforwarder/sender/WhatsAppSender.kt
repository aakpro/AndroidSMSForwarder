package com.smsforwarder.sender

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.smsforwarder.data.preferences.SecurePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class WhatsAppSender(
    private val context: Context,
    private val securePreferences: SecurePreferences,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    private val callMeBotMutex = Mutex()
    private var lastCallMeBotTimestamp = 0L

    /**
     * Send via CallMeBot API (Automated background delivery).
     * Enforces rate-limiting delay of 1.5 seconds between dispatches.
     */
    suspend fun sendCallMeBot(plainText: String): Result<String> = withContext(Dispatchers.IO) {
        val phone = securePreferences.callMeBotPhoneNumber
        val apiKey = securePreferences.callMeBotApiKey

        if (phone.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("CallMeBot phone number is not configured"))
        }
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("CallMeBot API key is not configured"))
        }

        executeCallMeBot(phone, apiKey, plainText)
    }

    suspend fun testCallMeBot(phone: String, apiKey: String): Result<String> = withContext(Dispatchers.IO) {
        if (phone.isBlank() || apiKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Please enter both phone number and API key"))
        }
        val testMessage = "SMS Forwarder Test: WhatsApp connected successfully!"
        executeCallMeBot(phone, apiKey, testMessage)
    }

    private suspend fun executeCallMeBot(phone: String, apiKey: String, text: String): Result<String> {
        return callMeBotMutex.withLock {
            // Enforce at least 1500ms between calls to avoid CallMeBot rate-limiting drops
            val now = System.currentTimeMillis()
            val timeSinceLast = now - lastCallMeBotTimestamp
            if (timeSinceLast < 1500) {
                delay(1500 - timeSinceLast)
            }
            lastCallMeBotTimestamp = System.currentTimeMillis()

            try {
                val cleanPhone = phone.replace("+", "").replace(" ", "").trim()
                val encodedText = URLEncoder.encode(text, "UTF-8")
                val url = "https://api.callmebot.com/whatsapp.php?phone=$cleanPhone&text=$encodedText&apikey=$apiKey"

                val request = Request.Builder().url(url).get().build()
                val response = client.newCall(request).execute()
                val body = response.body?.string().orEmpty()

                if (response.isSuccessful && !body.contains("error", ignoreCase = true)) {
                    Log.d(TAG, "CallMeBot message sent successfully")
                    Result.success("Sent via CallMeBot")
                } else {
                    Log.e(TAG, "CallMeBot failed: $body")
                    Result.failure(Exception("CallMeBot Error: ${body.take(100)}"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "CallMeBot request exception", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Send via Custom Webhook (e.g. self-hosted Evolution API, Baileys, n8n, Zapier).
     */
    suspend fun sendWebhook(
        sender: String,
        body: String,
        simSlot: Int,
        carrier: String,
        timestamp: Long
    ): Result<String> = withContext(Dispatchers.IO) {
        val url = securePreferences.customWebhookUrl
        val authHeader = securePreferences.customWebhookAuthHeader

        if (url.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Custom Webhook URL is not configured"))
        }

        executeWebhook(url, authHeader, sender, body, simSlot, carrier, timestamp)
    }

    suspend fun testWebhook(url: String, authHeader: String): Result<String> = withContext(Dispatchers.IO) {
        if (url.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Please enter a Webhook URL"))
        }
        executeWebhook(url, authHeader, "+1234567890", "Test SMS Webhook Message", 0, "TestCarrier", System.currentTimeMillis())
    }

    private fun executeWebhook(
        url: String,
        authHeader: String,
        sender: String,
        body: String,
        simSlot: Int,
        carrier: String,
        timestamp: Long
    ): Result<String> {
        return try {
            val json = JSONObject().apply {
                put("sender", sender)
                put("message", body)
                put("simSlot", simSlot)
                put("carrier", carrier)
                put("timestamp", timestamp)
            }.toString()

            val requestBuilder = Request.Builder()
                .url(url)
                .post(json.toRequestBody("application/json; charset=utf-8".toMediaType()))

            if (authHeader.isNotBlank()) {
                requestBuilder.header("Authorization", authHeader)
            }

            val response = client.newCall(requestBuilder.build()).execute()
            if (response.isSuccessful) {
                Result.success("Webhook Delivered (${response.code})")
            } else {
                Result.failure(Exception("Webhook returned HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Prepares an interactive draft notification for the user to tap and send in WhatsApp.
     */
    fun createDraftNotification(sender: String, text: String): Result<String> {
        return try {
            createNotificationChannel()
            val targetPhone = securePreferences.whatsappDraftPhoneNumber
            val encodedText = URLEncoder.encode(text, "UTF-8")
            val cleanPhone = targetPhone.replace("+", "").replace(" ", "").trim()

            val uri = if (cleanPhone.isNotBlank()) {
                Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=$encodedText")
            } else {
                Uri.parse("https://api.whatsapp.com/send?text=$encodedText")
            }

            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                (System.currentTimeMillis() % 10000).toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle("SMS from $sender (Draft Ready)")
                .setContentText("Tap to send message on WhatsApp")
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .addAction(android.R.drawable.ic_menu_send, "Open in WhatsApp", pendingIntent)
                .build()

            notificationManager.notify(NOTIFICATION_ID_OFFSET + (System.currentTimeMillis() % 1000).toInt(), notification)
            Result.success("Draft notification posted")
        } catch (e: Exception) {
            Log.e(TAG, "Error posting draft notification", e)
            Result.failure(e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "WhatsApp Drafts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications to manually dispatch SMS to WhatsApp"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "WhatsAppSender"
        private const val CHANNEL_ID = "whatsapp_drafts_channel"
        private const val NOTIFICATION_ID_OFFSET = 20000
    }
}
