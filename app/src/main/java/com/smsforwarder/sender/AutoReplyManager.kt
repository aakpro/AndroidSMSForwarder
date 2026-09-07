package com.smsforwarder.sender

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.smsforwarder.data.local.AppDatabase
import com.smsforwarder.data.local.AutoReplyHistoryEntity
import com.smsforwarder.data.local.DiagnosticEventType
import com.smsforwarder.data.local.DiagnosticLogEntity
import com.smsforwarder.data.model.SmsMessageItem
import com.smsforwarder.data.preferences.AppPreferences
import com.smsforwarder.util.SimUtil
import com.smsforwarder.util.TemplateFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class AutoReplyManager(
    private val context: Context,
    private val appPreferences: AppPreferences,
    private val database: AppDatabase = AppDatabase.getInstance(context)
) {

    suspend fun processIncomingSmsForAutoReply(sms: SmsMessageItem): Result<String> = withContext(Dispatchers.IO) {
        val isEnabled = appPreferences.isAutoReplyEnabled.first()
        if (!isEnabled) {
            return@withContext Result.failure(IllegalStateException("Auto-Reply is disabled in settings"))
        }

        // 1. Permission check
        val hasSendSmsPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasSendSmsPermission) {
            Log.w(TAG, "SEND_SMS permission not granted. Cannot auto-reply to ${sms.sender}.")
            return@withContext Result.failure(SecurityException("SEND_SMS permission is not granted"))
        }

        // 2. Cooldown check to prevent loops
        val cooldownMinutes = appPreferences.autoReplyCooldownMinutes.first()
        val cooldownMs = TimeUnit.MINUTES.toMillis(cooldownMinutes.toLong())
        val now = System.currentTimeMillis()

        val latestReply = database.autoReplyHistoryDao().getLatestReplyForNumber(sms.sender)
        if (latestReply != null && (now - latestReply.timestamp) < cooldownMs) {
            val remainingSec = TimeUnit.MILLISECONDS.toSeconds(cooldownMs - (now - latestReply.timestamp))
            val msg = "Auto-reply throttled for ${sms.sender}: Cooldown active ($remainingSec seconds remaining)"
            Log.i(TAG, msg)
            database.diagnosticLogDao().insertLog(
                DiagnosticLogEntity(
                    timestamp = now,
                    eventType = "AUTO_REPLY_THROTTLED",
                    message = msg,
                    details = "Last reply was at ${latestReply.timestamp}"
                )
            )
            return@withContext Result.failure(IllegalStateException(msg))
        }

        // 3. Format reply text
        val template = appPreferences.autoReplyTemplate.first()
        val replyText = TemplateFormatter.format(template, sms, escapeHtml = false)

        // 4. Resolve SIM subscription
        val simSlotChoice = appPreferences.autoReplySimSlot.first()
        val simUtil = SimUtil(context)
        val activeSims = simUtil.getActiveSimCards()

        val targetSubId = when (simSlotChoice) {
            0 -> activeSims.find { it.slotIndex == 0 }?.subscriptionId ?: sms.subscriptionId
            1 -> activeSims.find { it.slotIndex == 1 }?.subscriptionId ?: sms.subscriptionId
            else -> sms.subscriptionId
        }

        val targetSlot = if (simSlotChoice in 0..1) simSlotChoice else sms.simSlotIndex

        // 5. Send SMS via SmsManager
        return@withContext try {
            val smsManager = getSmsManagerForSubId(targetSubId)
            val parts = smsManager.divideMessage(replyText)

            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(sms.sender, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(sms.sender, null, replyText, null, null)
            }

            // 6. Record history
            database.autoReplyHistoryDao().insertRecord(
                AutoReplyHistoryEntity(
                    phoneNumber = sms.sender,
                    timestamp = now,
                    replyText = replyText,
                    simSlotIndex = targetSlot
                )
            )

            database.diagnosticLogDao().insertLog(
                DiagnosticLogEntity(
                    timestamp = now,
                    eventType = "AUTO_REPLY_SENT",
                    message = "Auto-reply sent to ${sms.sender} via SIM ${targetSlot + 1}",
                    details = replyText
                )
            )

            Log.d(TAG, "Auto-reply successfully sent to ${sms.sender} via SIM ${targetSlot + 1}")
            Result.success("Auto-reply sent: $replyText")
        } catch (e: Exception) {
            val err = e.message ?: "Failed to dispatch SMS"
            Log.e(TAG, "Error sending auto-reply SMS", e)
            database.diagnosticLogDao().insertLog(
                DiagnosticLogEntity(
                    timestamp = now,
                    eventType = DiagnosticEventType.ERROR.name,
                    message = "Failed to send auto-reply to ${sms.sender}: $err",
                    details = "Target SIM: ${targetSlot + 1}"
                )
            )
            Result.failure(e)
        }
    }

    private fun getSmsManagerForSubId(subscriptionId: Int): SmsManager {
        return if (subscriptionId > 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java).createForSubscriptionId(subscriptionId)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
        }
    }

    companion object {
        private const val TAG = "AutoReplyManager"
    }
}
