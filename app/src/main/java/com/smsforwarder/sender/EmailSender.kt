package com.smsforwarder.sender

import android.content.Context
import android.util.Log
import com.smsforwarder.data.local.AppDatabase
import com.smsforwarder.data.local.DiagnosticEventType
import com.smsforwarder.data.local.DiagnosticLogEntity
import com.smsforwarder.data.model.SmsMessageItem
import com.smsforwarder.data.preferences.AppPreferences
import com.smsforwarder.data.preferences.SecurePreferences
import com.smsforwarder.data.preferences.SmtpEncryption
import com.smsforwarder.util.NetworkUtil
import com.smsforwarder.util.TemplateFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EmailSender(
    private val context: Context,
    private val appPreferences: AppPreferences,
    private val securePreferences: SecurePreferences,
    private val database: AppDatabase = AppDatabase.getInstance(context)
) {

    suspend fun sendSms(sms: SmsMessageItem, formattedHtml: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val host = appPreferences.smtpHost.first().trim()
            val port = appPreferences.smtpPort.first()
            val encryption = appPreferences.smtpEncryption.first()
            val from = appPreferences.emailFrom.first().trim()
            val recipientsRaw = appPreferences.emailRecipients.first().trim()
            val subjectTemplate = appPreferences.emailSubjectTemplate.first()

            val username = securePreferences.smtpUsername.trim()
            val password = securePreferences.smtpPassword.trim()

            if (host.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("SMTP Host is not configured"))
            }

            val recipients = recipientsRaw.split(",", ";")
                .map { it.trim() }
                .filter { it.isNotBlank() }

            if (recipients.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("No recipient email address configured"))
            }

            val subject = TemplateFormatter.format(subjectTemplate, sms, escapeHtml = false)
            val htmlContent = wrapHtmlTemplate(sms, formattedHtml)

            val config = SmtpConfig(
                host = host,
                port = port,
                encryption = encryption,
                username = username,
                password = password
            )

            val message = EmailMessage(
                from = from.ifBlank { username },
                recipients = recipients,
                subject = subject,
                htmlBody = htmlContent
            )

            val client = SmtpClient(config)
            client.send(message)

            Log.d(TAG, "Email successfully sent to ${recipients.size} recipient(s) via $host:$port")
            Result.success("Email sent successfully to ${recipients.joinToString(", ")}")
        } catch (e: Exception) {
            val errMsg = e.message ?: "Unknown SMTP error"
            Log.e(TAG, "Failed to send email: $errMsg", e)

            val batteryInfo = NetworkUtil.getBatteryInfo(context)
            val netType = NetworkUtil.getNetworkType(context)
            database.diagnosticLogDao().insertLog(
                DiagnosticLogEntity(
                    timestamp = System.currentTimeMillis(),
                    eventType = DiagnosticEventType.ERROR.name,
                    message = "Email dispatch failed: $errMsg",
                    batteryLevel = batteryInfo.percentage,
                    isCharging = batteryInfo.isCharging,
                    networkType = netType,
                    details = "SMS from ${sms.sender}, SIM ${sms.simSlotIndex}"
                )
            )

            Result.failure(e)
        }
    }

    suspend fun testConnection(
        host: String,
        port: Int,
        encryption: SmtpEncryption,
        username: String,
        password: String,
        from: String,
        recipientsRaw: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (host.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Please enter a valid SMTP host"))
            }
            val recipients = recipientsRaw.split(",", ";")
                .map { it.trim() }
                .filter { it.isNotBlank() }

            if (recipients.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Please provide at least one recipient email address"))
            }

            val config = SmtpConfig(
                host = host.trim(),
                port = port,
                encryption = encryption,
                username = username.trim(),
                password = password.trim()
            )

            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val testMsg = EmailMessage(
                from = from.trim().ifBlank { username.trim() },
                recipients = recipients,
                subject = "[SMS Forwarder] Test Connection Successful",
                htmlBody = """
                    <div style="font-family: Arial, sans-serif; padding: 16px; background-color: #f4f6f8; border-radius: 8px;">
                        <h2 style="color: #2e7d32;">✅ SMTP Connection Test Succeeded</h2>
                        <p>This is a test email dispatched from <b>Android SMS Forwarder</b>.</p>
                        <ul>
                            <li><b>Host:</b> $host</li>
                            <li><b>Port:</b> $port ($encryption)</li>
                            <li><b>Time:</b> $dateStr</li>
                        </ul>
                        <p style="color: #666; font-size: 12px;">Your email forwarding channel is properly configured and active.</p>
                    </div>
                """.trimIndent()
            )

            val client = SmtpClient(config)
            client.send(testMsg)

            Result.success("Test email delivered successfully to ${recipients.joinToString(", ")}!")
        } catch (e: Exception) {
            val errMsg = e.message ?: "SMTP connection failed"
            Log.e(TAG, "Test email connection failed: $errMsg", e)
            Result.failure(e)
        }
    }

    private fun wrapHtmlTemplate(sms: SmsMessageItem, bodyHtml: String): String {
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(sms.timestamp))
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; background-color: #f5f5f7; padding: 20px; color: #1f1f1f; }
                    .card { background-color: #ffffff; border-radius: 12px; padding: 24px; max-width: 600px; margin: 0 auto; box-shadow: 0 4px 12px rgba(0,0,0,0.08); border-left: 6px solid #1a73e8; }
                    .header { font-size: 18px; font-weight: bold; margin-bottom: 16px; color: #1a73e8; display: flex; align-items: center; }
                    .badge { display: inline-block; background-color: #e8f0fe; color: #1967d2; padding: 4px 10px; border-radius: 16px; font-size: 12px; font-weight: 600; margin-bottom: 12px; }
                    .meta-table { width: 100%; border-collapse: collapse; margin-bottom: 16px; font-size: 14px; }
                    .meta-table td { padding: 6px 0; }
                    .meta-label { color: #5f6368; width: 100px; font-weight: 500; }
                    .meta-value { color: #202124; font-weight: 600; }
                    .divider { border-top: 1px solid #e0e0e0; margin: 16px 0; }
                    .content-box { background-color: #f8f9fa; border: 1px solid #dadce0; border-radius: 8px; padding: 16px; font-size: 15px; line-height: 1.5; white-space: pre-wrap; word-break: break-word; color: #202124; }
                    .footer { margin-top: 16px; font-size: 12px; color: #70757a; text-align: center; }
                </style>
            </head>
            <body>
                <div class="card">
                    <div class="badge">SIM ${sms.simSlotIndex + 1} • ${sms.carrierName}</div>
                    <div class="header">📬 Incoming SMS Forwarded</div>
                    <table class="meta-table">
                        <tr>
                            <td class="meta-label">From:</td>
                            <td class="meta-value">${sms.sender}</td>
                        </tr>
                        <tr>
                            <td class="meta-label">Time:</td>
                            <td class="meta-value">$dateStr</td>
                        </tr>
                        <tr>
                            <td class="meta-label">Carrier:</td>
                            <td class="meta-value">${sms.carrierName} (Slot ${sms.simSlotIndex + 1})</td>
                        </tr>
                    </table>
                    <div class="divider"></div>
                    <div class="content-box">$bodyHtml</div>
                    <div class="footer">Android SMS Forwarder 24/7 Relay Service</div>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    companion object {
        private const val TAG = "EmailSender"
    }
}
