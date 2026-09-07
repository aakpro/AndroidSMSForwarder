package com.smsforwarder.data.preferences

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Hardware-backed encrypted storage using Android Keystore and AES-256 GCM.
 * Stores sensitive credentials (tokens, API keys, webhook URLs, phone numbers).
 */
class SecurePreferences(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_FILENAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize EncryptedSharedPreferences, falling back to private prefs", e)
        context.getSharedPreferences("${PREFS_FILENAME}_fallback", Context.MODE_PRIVATE)
    }

    var telegramBotToken: String
        get() = prefs.getString(KEY_TELEGRAM_BOT_TOKEN, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_TELEGRAM_BOT_TOKEN, value.trim()).apply()

    var telegramChatId: String
        get() = prefs.getString(KEY_TELEGRAM_CHAT_ID, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_TELEGRAM_CHAT_ID, value.trim()).apply()

    var callMeBotApiKey: String
        get() = prefs.getString(KEY_CALLMEBOT_API_KEY, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_CALLMEBOT_API_KEY, value.trim()).apply()

    var callMeBotPhoneNumber: String
        get() = prefs.getString(KEY_CALLMEBOT_PHONE, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_CALLMEBOT_PHONE, value.trim()).apply()

    var customWebhookUrl: String
        get() = prefs.getString(KEY_CUSTOM_WEBHOOK_URL, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_CUSTOM_WEBHOOK_URL, value.trim()).apply()

    var customWebhookAuthHeader: String
        get() = prefs.getString(KEY_CUSTOM_WEBHOOK_AUTH, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_CUSTOM_WEBHOOK_AUTH, value.trim()).apply()

    var whatsappDraftPhoneNumber: String
        get() = prefs.getString(KEY_WHATSAPP_DRAFT_PHONE, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_WHATSAPP_DRAFT_PHONE, value.trim()).apply()

    var discordWebhookUrl: String
        get() = prefs.getString(KEY_DISCORD_WEBHOOK_URL, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_DISCORD_WEBHOOK_URL, value.trim()).apply()

    var genericWebhookUrl: String
        get() = prefs.getString(KEY_GENERIC_WEBHOOK_URL, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_GENERIC_WEBHOOK_URL, value.trim()).apply()

    var genericWebhookAuthHeader: String
        get() = prefs.getString(KEY_GENERIC_WEBHOOK_AUTH, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_GENERIC_WEBHOOK_AUTH, value.trim()).apply()

    var smtpUsername: String
        get() = prefs.getString(KEY_SMTP_USERNAME, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_SMTP_USERNAME, value.trim()).apply()

    var smtpPassword: String
        get() = prefs.getString(KEY_SMTP_PASSWORD, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_SMTP_PASSWORD, value.trim()).apply()

    fun clearAllSecrets() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val TAG = "SecurePrefs"
        private const val PREFS_FILENAME = "secure_preferences"

        private const val KEY_TELEGRAM_BOT_TOKEN = "sec_tg_bot_token"
        private const val KEY_TELEGRAM_CHAT_ID = "sec_tg_chat_id"
        private const val KEY_CALLMEBOT_API_KEY = "sec_cmb_api_key"
        private const val KEY_CALLMEBOT_PHONE = "sec_cmb_phone"
        private const val KEY_CUSTOM_WEBHOOK_URL = "sec_webhook_url"
        private const val KEY_CUSTOM_WEBHOOK_AUTH = "sec_webhook_auth"
        private const val KEY_WHATSAPP_DRAFT_PHONE = "sec_wa_draft_phone"
        private const val KEY_DISCORD_WEBHOOK_URL = "sec_discord_webhook_url"
        private const val KEY_GENERIC_WEBHOOK_URL = "sec_generic_webhook_url"
        private const val KEY_GENERIC_WEBHOOK_AUTH = "sec_generic_webhook_auth"
        private const val KEY_SMTP_USERNAME = "sec_smtp_username"
        private const val KEY_SMTP_PASSWORD = "sec_smtp_password"
    }
}
