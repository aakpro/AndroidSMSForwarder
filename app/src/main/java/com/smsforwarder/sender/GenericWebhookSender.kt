package com.smsforwarder.sender

import android.util.Log
import com.smsforwarder.data.model.SmsMessageItem
import com.smsforwarder.data.preferences.SecurePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class GenericWebhookSender(
    private val securePreferences: SecurePreferences,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {

    suspend fun sendSms(sms: SmsMessageItem): Result<String> = withContext(Dispatchers.IO) {
        val url = securePreferences.genericWebhookUrl
        val authHeader = securePreferences.genericWebhookAuthHeader

        if (url.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Generic Webhook URL is not configured"))
        }

        val dateIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date(sms.timestamp))
        val jsonPayload = JSONObject().apply {
            put("event", "sms_received")
            put("sender", sms.sender)
            put("message", sms.body)
            put("sim_slot", sms.simSlotIndex)
            put("sim_name", "SIM ${sms.simSlotIndex + 1}")
            put("carrier", sms.carrierName)
            put("timestamp", sms.timestamp)
            put("date_iso", dateIso)
        }.toString()

        executePost(url, authHeader, jsonPayload)
    }

    suspend fun sendEvent(eventType: String, data: JSONObject): Result<String> = withContext(Dispatchers.IO) {
        val url = securePreferences.genericWebhookUrl
        val authHeader = securePreferences.genericWebhookAuthHeader

        if (url.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Generic Webhook URL is not configured"))
        }

        val payload = JSONObject().apply {
            put("event", eventType)
            put("timestamp", System.currentTimeMillis())
            put("data", data)
        }.toString()

        executePost(url, authHeader, payload)
    }

    suspend fun testConnection(url: String, authHeader: String): Result<String> = withContext(Dispatchers.IO) {
        if (url.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Please provide a valid Webhook URL"))
        }

        val jsonPayload = JSONObject().apply {
            put("event", "test_ping")
            put("message", "SMS Forwarder Webhook test connection successful")
            put("timestamp", System.currentTimeMillis())
        }.toString()

        executePost(url, authHeader, jsonPayload)
    }

    private fun executePost(url: String, authHeader: String, jsonPayload: String): Result<String> {
        return try {
            val requestBuilder = Request.Builder()
                .url(url)
                .post(jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType()))

            if (authHeader.isNotBlank()) {
                if (authHeader.contains(":")) {
                    val parts = authHeader.split(":", limit = 2)
                    requestBuilder.addHeader(parts[0].trim(), parts[1].trim())
                } else {
                    requestBuilder.addHeader("Authorization", authHeader.trim())
                }
            }

            val response = client.newCall(requestBuilder.build()).execute()
            val code = response.code

            if (code in 200..299) {
                Log.d(TAG, "Generic webhook delivered successfully (HTTP $code)")
                Result.success("Delivered (HTTP $code)")
            } else {
                val body = response.body?.string().orEmpty()
                Log.e(TAG, "Generic webhook returned HTTP $code: $body")
                Result.failure(Exception("Webhook Error: HTTP $code ($body)"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Generic webhook network call failed", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "GenericWebhookSender"
    }
}
