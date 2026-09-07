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
    private val discordSender: DiscordSender = DiscordSender(securePreferences),
    private val genericWebhookSender: GenericWebhookSender = GenericWebhookSender(securePreferences),
    private val emailSender: EmailSender = EmailSender(context, appPreferences, securePreferences),
    private val database: AppDatabase = AppDatabase.getInstance(context)
) {

    suspend fun forwardSms(sms: SmsMessageItem, logId: Long): Boolean {
        val startTime = System.currentTimeMillis()
        val isTelegramEnabled = appPreferences.isTelegramEnabled.first()
        val isWhatsAppEnabled = appPreferences.isWhatsAppEnabled.first()
        val isDiscordEnabled = appPreferences.isDiscordEnabled.first()
        val isWebhookEnabled = appPreferences.isGenericWebhookEnabled.first()
        val isEmailEnabled = appPreferences.isEmailEnabled.first()
        val template = appPreferences.messageTemplate.first()
        val isSensitive = SensitiveFilter.isSensitiveMessage(sms.body)

        val batteryInfo = com.smsforwarder.util.NetworkUtil.getBatteryInfo(context)
        val networkType = com.smsforwarder.util.NetworkUtil.getNetworkType(context)

        var telegramStatus = ForwardStatus.DISABLED.name
        var whatsAppStatus = ForwardStatus.DISABLED.name
        var discordStatus = ForwardStatus.DISABLED.name
        var webhookStatus = ForwardStatus.DISABLED.name
        var emailStatus = ForwardStatus.DISABLED.name
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

        // 3. Process Discord Forwarding
        if (isDiscordEnabled) {
            val plainText = TemplateFormatter.format(template, sms, escapeHtml = false)
            val result = discordSender.sendSms(sms, plainText)
            if (result.isSuccess) {
                discordStatus = ForwardStatus.SUCCESS.name
                Log.d(TAG, "SMS #$logId forwarded to Discord")
            } else {
                discordStatus = ForwardStatus.FAILED.name
                val err = result.exceptionOrNull()?.message ?: "Discord unknown error"
                combinedError = (combinedError?.let { "$it; " } ?: "") + "Discord: $err"
                Log.e(TAG, "SMS #$logId failed Discord forwarding: $err")
            }
        }

        // 4. Process Generic Webhook Forwarding
        if (isWebhookEnabled) {
            val result = genericWebhookSender.sendSms(sms)
            if (result.isSuccess) {
                webhookStatus = ForwardStatus.SUCCESS.name
                Log.d(TAG, "SMS #$logId forwarded to Generic Webhook")
            } else {
                webhookStatus = ForwardStatus.FAILED.name
                val err = result.exceptionOrNull()?.message ?: "Webhook unknown error"
                combinedError = (combinedError?.let { "$it; " } ?: "") + "Generic Webhook: $err"
                Log.e(TAG, "SMS #$logId failed Generic Webhook forwarding: $err")
            }
        }

        // 5. Process Email (SMTP) Forwarding
        if (isEmailEnabled) {
            val formattedHtml = TemplateFormatter.format(template, sms, escapeHtml = true)
            val result = emailSender.sendSms(sms, formattedHtml)
            if (result.isSuccess) {
                emailStatus = ForwardStatus.SUCCESS.name
                Log.d(TAG, "SMS #$logId forwarded to Email")
            } else {
                emailStatus = ForwardStatus.FAILED.name
                val err = result.exceptionOrNull()?.message ?: "Email unknown error"
                combinedError = (combinedError?.let { "$it; " } ?: "") + "Email: $err"
                Log.e(TAG, "SMS #$logId failed Email forwarding: $err")
            }
        }

        val durationMs = System.currentTimeMillis() - startTime

        // 6. Update status and diagnostics in Room Database
        database.smsLogDao().updateAllStatuses(
            id = logId,
            telegramStatus = telegramStatus,
            whatsappStatus = whatsAppStatus,
            discordStatus = discordStatus,
            webhookStatus = webhookStatus,
            emailStatus = emailStatus,
            durationMs = durationMs,
            batteryLevel = batteryInfo.percentage,
            networkType = networkType,
            error = combinedError
        )

        if (combinedError != null) {
            database.diagnosticLogDao().insertLog(
                com.smsforwarder.data.local.DiagnosticLogEntity(
                    timestamp = System.currentTimeMillis(),
                    eventType = com.smsforwarder.data.local.DiagnosticEventType.ERROR.name,
                    message = "Forwarding failed for SMS from ${sms.sender}: $combinedError",
                    batteryLevel = batteryInfo.percentage,
                    isCharging = batteryInfo.isCharging,
                    networkType = networkType,
                    details = "Duration: ${durationMs}ms, SIM: ${sms.simSlotIndex}"
                )
            )
        }

        val tgOk = telegramStatus == ForwardStatus.SUCCESS.name || telegramStatus == ForwardStatus.DISABLED.name
        val waOk = whatsAppStatus == ForwardStatus.SUCCESS.name || whatsAppStatus == ForwardStatus.DISABLED.name || whatsAppStatus.startsWith(ForwardStatus.SKIPPED.name)
        val dcOk = discordStatus == ForwardStatus.SUCCESS.name || discordStatus == ForwardStatus.DISABLED.name
        val whOk = webhookStatus == ForwardStatus.SUCCESS.name || webhookStatus == ForwardStatus.DISABLED.name
        val emOk = emailStatus == ForwardStatus.SUCCESS.name || emailStatus == ForwardStatus.DISABLED.name

        return tgOk && waOk && dcOk && whOk && emOk
    }

    companion object {
        private const val TAG = "ForwarderManager"
    }
}
