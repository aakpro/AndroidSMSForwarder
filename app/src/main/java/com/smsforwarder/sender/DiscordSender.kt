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
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class DiscordSender(
    private val securePreferences: SecurePreferences,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {

    suspend fun sendSms(sms: SmsMessageItem, formattedText: String): Result<String> = withContext(Dispatchers.IO) {
        val webhookUrl = securePreferences.discordWebhookUrl
        if (webhookUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Discord Webhook URL is not configured"))
        }

        executeDiscordEmbed(
            webhookUrl = webhookUrl,
            sms = sms,
            content = formattedText
        )
    }

    suspend fun sendMessage(content: String): Result<String> = withContext(Dispatchers.IO) {
        val webhookUrl = securePreferences.discordWebhookUrl
        if (webhookUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Discord Webhook URL is not configured"))
        }
        executeDiscordSimple(webhookUrl, content)
    }

    suspend fun testConnection(webhookUrl: String): Result<String> = withContext(Dispatchers.IO) {
        if (webhookUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Please provide a valid Discord Webhook URL"))
        }
        executeDiscordSimple(webhookUrl, "✅ **SMS Forwarder Test**: Discord Webhook connection successful!")
    }

    private fun executeDiscordEmbed(
        webhookUrl: String,
        sms: SmsMessageItem,
        content: String
    ): Result<String> {
        return try {
            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(sms.timestamp))
            val simColor = if (sms.simSlotIndex == 0) 0x3498DB else 0x9B59B6 // Blue for SIM 1, Purple for SIM 2

            val embed = JSONObject().apply {
                put("title", "📬 SMS Received: SIM ${sms.simSlotIndex + 1} (${sms.carrierName})")
                put("description", sms.body)
                put("color", simColor)
                val fields = JSONArray().apply {
                    put(JSONObject().apply {
                        put("name", "From")
                        put("value", "`" + sms.sender + "`")
                        put("inline", true)
                    })
                    put(JSONObject().apply {
                        put("name", "Slot")
                        put("value", "SIM ${sms.simSlotIndex + 1}")
                        put("inline", true)
                    })
                    put(JSONObject().apply {
                        put("name", "Time")
                        put("value", dateStr)
                        put("inline", false)
                    })
                }
                put("fields", fields)
                put("footer", JSONObject().apply {
                    put("text", "Android SMS Forwarder • Dual-SIM")
                })
            }

            val payload = JSONObject().apply {
                put("username", "SMS Forwarder")
                put("embeds", JSONArray().apply { put(embed) })
            }.toString()

            val request = Request.Builder()
                .url(webhookUrl)
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val code = response.code

            if (code in 200..299) {
                Log.d(TAG, "Discord webhook delivered successfully (HTTP $code)")
                Result.success("Delivered (HTTP $code)")
            } else {
                val body = response.body?.string().orEmpty()
                Log.e(TAG, "Discord webhook failed: HTTP $code - $body")
                Result.failure(Exception("Discord Webhook Error: HTTP $code"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Discord network call failed", e)
            Result.failure(e)
        }
    }

    private fun executeDiscordSimple(webhookUrl: String, content: String): Result<String> {
        return try {
            val payload = JSONObject().apply {
                put("username", "SMS Forwarder")
                put("content", content)
            }.toString()

            val request = Request.Builder()
                .url(webhookUrl)
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val code = response.code

            if (code in 200..299) {
                Result.success("Delivered")
            } else {
                val body = response.body?.string().orEmpty()
                Result.failure(Exception("Discord Webhook Error: HTTP $code ($body)"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Discord network call failed", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "DiscordSender"
    }
}
