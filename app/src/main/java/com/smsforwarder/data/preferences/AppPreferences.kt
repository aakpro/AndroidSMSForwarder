package com.smsforwarder.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.smsforwarder.data.model.SimFilterOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

enum class WhatsAppMode(val key: String, val title: String) {
    CALLMEBOT("CALLMEBOT", "Automated (CallMeBot API)"),
    WEBHOOK("WEBHOOK", "Automated (Custom Webhook)"),
    INTENT_DRAFT("INTENT_DRAFT", "Interactive Draft (WhatsApp Intent)");

    companion object {
        fun fromKey(key: String): WhatsAppMode =
            entries.find { it.key.equals(key, ignoreCase = true) } ?: CALLMEBOT
    }
}

enum class SmtpEncryption(val key: String, val title: String, val defaultPort: Int) {
    STARTTLS("STARTTLS", "STARTTLS", 587),
    SSL_TLS("SSL_TLS", "SSL / TLS", 465),
    PLAIN("PLAIN", "Plain / Unencrypted", 25);

    companion object {
        fun fromKey(key: String): SmtpEncryption =
            entries.find { it.key.equals(key, ignoreCase = true) } ?: STARTTLS
    }
}

class AppPreferences(private val context: Context) {

    private object PreferencesKeys {
        val SERVICE_ENABLED = booleanPreferencesKey("service_enabled")
        val SIM_FILTER = stringPreferencesKey("sim_filter")
        val TELEGRAM_ENABLED = booleanPreferencesKey("telegram_enabled")
        val WHATSAPP_ENABLED = booleanPreferencesKey("whatsapp_enabled")
        val WHATSAPP_MODE = stringPreferencesKey("whatsapp_mode")
        val EXCLUDE_SENSITIVE_CALLMEBOT = booleanPreferencesKey("exclude_sensitive_callmebot")
        val REDACT_OTP_CALLMEBOT = booleanPreferencesKey("redact_otp_callmebot")
        val CALLMEBOT_WARNING_ACCEPTED = booleanPreferencesKey("callmebot_warning_accepted")
        val MESSAGE_TEMPLATE = stringPreferencesKey("message_template")
        val DISCORD_ENABLED = booleanPreferencesKey("discord_enabled")
        val GENERIC_WEBHOOK_ENABLED = booleanPreferencesKey("generic_webhook_enabled")
        val HEARTBEAT_ENABLED = booleanPreferencesKey("heartbeat_enabled")
        val HEARTBEAT_INTERVAL_HOURS = intPreferencesKey("heartbeat_interval_hours")
        val LOW_BATTERY_ALERT_ENABLED = booleanPreferencesKey("low_battery_alert_enabled")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val EMAIL_ENABLED = booleanPreferencesKey("email_enabled")
        val SMTP_HOST = stringPreferencesKey("smtp_host")
        val SMTP_PORT = intPreferencesKey("smtp_port")
        val SMTP_ENCRYPTION = stringPreferencesKey("smtp_encryption")
        val EMAIL_FROM = stringPreferencesKey("email_from")
        val EMAIL_RECIPIENTS = stringPreferencesKey("email_recipients")
        val EMAIL_SUBJECT_TEMPLATE = stringPreferencesKey("email_subject_template")
    }

    val isServiceEnabled: Flow<Boolean> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[PreferencesKeys.SERVICE_ENABLED] ?: false }

    suspend fun setServiceEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.SERVICE_ENABLED] = enabled }
    }

    val simFilter: Flow<SimFilterOption> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { SimFilterOption.fromKey(it[PreferencesKeys.SIM_FILTER] ?: SimFilterOption.ALL.key) }

    suspend fun setSimFilter(option: SimFilterOption) {
        context.dataStore.edit { it[PreferencesKeys.SIM_FILTER] = option.key }
    }

    val isTelegramEnabled: Flow<Boolean> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[PreferencesKeys.TELEGRAM_ENABLED] ?: false }

    suspend fun setTelegramEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.TELEGRAM_ENABLED] = enabled }
    }

    val isWhatsAppEnabled: Flow<Boolean> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[PreferencesKeys.WHATSAPP_ENABLED] ?: false }

    suspend fun setWhatsAppEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.WHATSAPP_ENABLED] = enabled }
    }

    val whatsAppMode: Flow<WhatsAppMode> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { WhatsAppMode.fromKey(it[PreferencesKeys.WHATSAPP_MODE] ?: WhatsAppMode.CALLMEBOT.key) }

    suspend fun setWhatsAppMode(mode: WhatsAppMode) {
        context.dataStore.edit { it[PreferencesKeys.WHATSAPP_MODE] = mode.key }
    }

    val excludeSensitiveFromCallMeBot: Flow<Boolean> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[PreferencesKeys.EXCLUDE_SENSITIVE_CALLMEBOT] ?: true }

    suspend fun setExcludeSensitiveFromCallMeBot(exclude: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.EXCLUDE_SENSITIVE_CALLMEBOT] = exclude }
    }

    val redactOtpInCallMeBot: Flow<Boolean> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[PreferencesKeys.REDACT_OTP_CALLMEBOT] ?: false }

    suspend fun setRedactOtpInCallMeBot(redact: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.REDACT_OTP_CALLMEBOT] = redact }
    }

    val callMeBotWarningAccepted: Flow<Boolean> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[PreferencesKeys.CALLMEBOT_WARNING_ACCEPTED] ?: false }

    suspend fun setCallMeBotWarningAccepted(accepted: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.CALLMEBOT_WARNING_ACCEPTED] = accepted }
    }

    val messageTemplate: Flow<String> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[PreferencesKeys.MESSAGE_TEMPLATE] ?: DEFAULT_TEMPLATE }

    suspend fun setMessageTemplate(template: String) {
        context.dataStore.edit { it[PreferencesKeys.MESSAGE_TEMPLATE] = template }
    }

    val isDiscordEnabled: Flow<Boolean> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[PreferencesKeys.DISCORD_ENABLED] ?: false }

    suspend fun setDiscordEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.DISCORD_ENABLED] = enabled }
    }

    val isGenericWebhookEnabled: Flow<Boolean> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[PreferencesKeys.GENERIC_WEBHOOK_ENABLED] ?: false }

    suspend fun setGenericWebhookEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.GENERIC_WEBHOOK_ENABLED] = enabled }
    }

    val isHeartbeatEnabled: Flow<Boolean> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[PreferencesKeys.HEARTBEAT_ENABLED] ?: false }

    suspend fun setHeartbeatEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.HEARTBEAT_ENABLED] = enabled }
    }

    val heartbeatIntervalHours: Flow<Int> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[PreferencesKeys.HEARTBEAT_INTERVAL_HOURS] ?: 12 }

    suspend fun setHeartbeatIntervalHours(hours: Int) {
        context.dataStore.edit { it[PreferencesKeys.HEARTBEAT_INTERVAL_HOURS] = hours }
    }

    val isLowBatteryAlertEnabled: Flow<Boolean> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[PreferencesKeys.LOW_BATTERY_ALERT_ENABLED] ?: true }

    suspend fun setLowBatteryAlertEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.LOW_BATTERY_ALERT_ENABLED] = enabled }
    }

    val appLanguage: Flow<String> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[PreferencesKeys.APP_LANGUAGE] ?: "system" }

    suspend fun setAppLanguage(lang: String) {
        context.dataStore.edit { it[PreferencesKeys.APP_LANGUAGE] = lang }
    }

    val isEmailEnabled: Flow<Boolean> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[PreferencesKeys.EMAIL_ENABLED] ?: false }

    suspend fun setEmailEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.EMAIL_ENABLED] = enabled }
    }

    val smtpHost: Flow<String> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[PreferencesKeys.SMTP_HOST] ?: "smtp.gmail.com" }

    suspend fun setSmtpHost(host: String) {
        context.dataStore.edit { it[PreferencesKeys.SMTP_HOST] = host }
    }

    val smtpPort: Flow<Int> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[PreferencesKeys.SMTP_PORT] ?: 587 }

    suspend fun setSmtpPort(port: Int) {
        context.dataStore.edit { it[PreferencesKeys.SMTP_PORT] = port }
    }

    val smtpEncryption: Flow<SmtpEncryption> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { SmtpEncryption.fromKey(it[PreferencesKeys.SMTP_ENCRYPTION] ?: SmtpEncryption.STARTTLS.key) }

    suspend fun setSmtpEncryption(encryption: SmtpEncryption) {
        context.dataStore.edit { it[PreferencesKeys.SMTP_ENCRYPTION] = encryption.key }
    }

    val emailFrom: Flow<String> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[PreferencesKeys.EMAIL_FROM] ?: "" }

    suspend fun setEmailFrom(from: String) {
        context.dataStore.edit { it[PreferencesKeys.EMAIL_FROM] = from }
    }

    val emailRecipients: Flow<String> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[PreferencesKeys.EMAIL_RECIPIENTS] ?: "" }

    suspend fun setEmailRecipients(recipients: String) {
        context.dataStore.edit { it[PreferencesKeys.EMAIL_RECIPIENTS] = recipients }
    }

    val emailSubjectTemplate: Flow<String> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[PreferencesKeys.EMAIL_SUBJECT_TEMPLATE] ?: DEFAULT_EMAIL_SUBJECT }

    suspend fun setEmailSubjectTemplate(template: String) {
        context.dataStore.edit { it[PreferencesKeys.EMAIL_SUBJECT_TEMPLATE] = template }
    }

    companion object {
        const val DEFAULT_EMAIL_SUBJECT = "[SMS Forwarder] From {sender} ({sim}) - {time}"

        const val DEFAULT_TEMPLATE = """📬 <b>New SMS Received</b>
📱 <b>SIM:</b> {sim} ({carrier})
👤 <b>From:</b> <code>{sender}</code>
🕒 <b>Time:</b> {time}

💬 <b>Message:</b>
{message}"""
    }
}
