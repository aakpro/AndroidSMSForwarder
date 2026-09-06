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

    companion object {
        const val DEFAULT_TEMPLATE = """📬 <b>New SMS Received</b>
📱 <b>SIM:</b> {sim} ({carrier})
👤 <b>From:</b> <code>{sender}</code>
🕒 <b>Time:</b> {time}

💬 <b>Message:</b>
{message}"""
    }
}
