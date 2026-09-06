package com.smsforwarder.sender

import android.content.Context
import android.util.Log
import com.smsforwarder.data.local.AppDatabase
import com.smsforwarder.data.model.ForwardStatus
import com.smsforwarder.data.model.SmsMessageItem
import com.smsforwarder.data.preferences.AppPreferences
import com.smsforwarder.data.preferences.SecurePreferences
import com.smsforwarder.data.preferences.WhatsAppMode
import com.smsforwarder.util.SensitiveFilter
import com.smsforwarder.util.TemplateFormatter
import kotlinx.coroutines.flow.first

class ForwarderManager(
    private val context: Context,
    private val appPreferences: AppPreferences,
    private val securePreferences: SecurePreferences,
    private val telegramSender: TelegramSender = TelegramSender(securePreferences),
    private val whatsAppSender: WhatsAppSender = WhatsAppSender(context, securePreferences),
    private val database: AppDatabase = AppDatabase.getInstance(context)
) {

    suspend fun forwardSms(sms: SmsMessageItem, logId: Long): Boolean {
        val isTelegramEnabled = appPreferences.isTelegramEnabled.first()
        val isWhatsAppEnabled = appPreferences.isWhatsAppEnabled.first()
        val template = appPreferences.messageTemplate.first()
        val isSensitive = SensitiveFilter.isSensitiveMessage(sms.body)

        var telegramStatus = ForwardStatus.DISABLED.name
        var whatsAppStatus = ForwardStatus.DISABLED.name
        var combinedError: String? = null

        // 1. Process Telegram Forwarding
        if (isTelegramEnabled) {
            val formattedHtml = TemplateFormatter.format(template, sms, escapeHtml = true)
            val result = telegramSender.sendMessage(formattedHtml)
            if (result.isSuccess) {
                telegramStatus = ForwardStatus.SUCCESS.name
                Log.d(TAG, "SMS #$logId forwarded to Telegram")
            } else {
                telegramStatus = ForwardStatus.FAILED.name
                val err = result.exceptionOrNull()?.message ?: "Telegram unknown error"
                combinedError = (combinedError?.let { "$it; " } ?: "") + "TG: $err"
                Log.e(TAG, "SMS #$logId failed Telegram forwarding: $err")
            }
        }

        // 2. Process WhatsApp Forwarding
        if (isWhatsAppEnabled) {
            val waMode = appPreferences.whatsAppMode.first()

            when (waMode) {
                WhatsAppMode.CALLMEBOT -> {
                    val excludeSensitive = appPreferences.excludeSensitiveFromCallMeBot.first()
                    val redactOtp = appPreferences.redactOtpInCallMeBot.first()

                    if (isSensitive && excludeSensitive) {
                        whatsAppStatus = "${ForwardStatus.SKIPPED.name} (Privacy Guard: OTP)"
                        Log.w(TAG, "SMS #$logId skipped for CallMeBot due to Privacy Guard")
                    } else {
                        val bodyToSend = if (isSensitive && redactOtp) {
                            SensitiveFilter.redactSensitiveDigits(sms.body)
                        } else {
                            sms.body
                        }
                        val plainText = TemplateFormatter.format(
                            template,
                            sms.copy(body = bodyToSend),
                            escapeHtml = false
                        )
                        val result = whatsAppSender.sendCallMeBot(plainText)
                        if (result.isSuccess) {
                            whatsAppStatus = ForwardStatus.SUCCESS.name
                        } else {
                            whatsAppStatus = ForwardStatus.FAILED.name
                            val err = result.exceptionOrNull()?.message ?: "CallMeBot failed"
                            combinedError = (combinedError?.let { "$it; " } ?: "") + "WA: $err"
                        }
                    }
                }

                WhatsAppMode.WEBHOOK -> {
                    val result = whatsAppSender.sendWebhook(
                        sender = sms.sender,
                        body = sms.body,
                        simSlot = sms.simSlotIndex,
                        carrier = sms.carrierName,
                        timestamp = sms.timestamp
                    )
                    if (result.isSuccess) {
                        whatsAppStatus = ForwardStatus.SUCCESS.name
                    } else {
                        whatsAppStatus = ForwardStatus.FAILED.name
                        val err = result.exceptionOrNull()?.message ?: "Webhook failed"
                        combinedError = (combinedError?.let { "$it; " } ?: "") + "WA Webhook: $err"
                    }
                }

                WhatsAppMode.INTENT_DRAFT -> {
                    val draftText = TemplateFormatter.format(template, sms, escapeHtml = false)
                    val result = whatsAppSender.createDraftNotification(sms.sender, draftText)
                    whatsAppStatus = if (result.isSuccess) {
                        ForwardStatus.DRAFT_CREATED.name
                    } else {
                        ForwardStatus.FAILED.name
                    }
                }
            }
        }

        // 3. Update status in Room Database
        database.smsLogDao().updateStatus(logId, telegramStatus, whatsAppStatus, combinedError)

        return (telegramStatus == ForwardStatus.SUCCESS.name || telegramStatus == ForwardStatus.DISABLED.name) &&
                (whatsAppStatus == ForwardStatus.SUCCESS.name || whatsAppStatus == ForwardStatus.DISABLED.name || whatsAppStatus.startsWith(ForwardStatus.SKIPPED.name))
    }

    companion object {
        private const val TAG = "ForwarderManager"
    }
}
