package com.smsforwarder.sender

import android.util.Log
import com.smsforwarder.data.preferences.SecurePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class TelegramSender(
    private val securePreferences: SecurePreferences,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {

    suspend fun sendMessage(formattedHtmlText: String): Result<String> = withContext(Dispatchers.IO) {
        val botToken = securePreferences.telegramBotToken
        val chatId = securePreferences.telegramChatId

        if (botToken.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Telegram Bot Token is not set"))
        }
        if (chatId.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Telegram Chat ID is not set"))
        }

        executeTelegramApi(botToken, chatId, formattedHtmlText)
    }

    suspend fun testConnection(botToken: String, chatId: String): Result<String> = withContext(Dispatchers.IO) {
        if (botToken.isBlank() || chatId.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Please enter both Bot Token and Chat ID"))
        }

        val testMessage = "✅ <b>SMS Forwarder Test</b>\nConnection established successfully!"
        executeTelegramApi(botToken, chatId, testMessage)
    }

    private fun executeTelegramApi(botToken: String, chatId: String, text: String): Result<String> {
        return try {
            val url = "https://api.telegram.org/bot$botToken/sendMessage"
            val jsonPayload = JSONObject().apply {
                put("chat_id", chatId)
                put("text", text)
                put("parse_mode", "HTML")
                put("disable_web_page_preview", true)
            }.toString()

            val request = Request.Builder()
                .url(url)
                .post(jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string().orEmpty()

            if (response.isSuccessful) {
                Log.d(TAG, "Telegram message delivered successfully")
                Result.success("Delivered")
            } else {
                val errorMsg = try {
                    val json = JSONObject(responseBody)
                    json.optString("description", "HTTP ${response.code}")
                } catch (e: Exception) {
                    "HTTP ${response.code}"
                }
                Log.e(TAG, "Telegram error response: $errorMsg")
                Result.failure(Exception("Telegram Error: $errorMsg"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Telegram network call failed", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "TelegramSender"
    }
}
