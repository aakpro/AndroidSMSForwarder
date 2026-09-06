package com.smsforwarder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.smsforwarder.data.local.AppDatabase
import com.smsforwarder.data.local.SmsLogEntity
import com.smsforwarder.data.model.ForwardStatus
import com.smsforwarder.data.model.SimFilterOption
import com.smsforwarder.data.preferences.AppPreferences
import com.smsforwarder.util.SensitiveFilter
import com.smsforwarder.util.SimUtil
import com.smsforwarder.worker.ForwardWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO)

        scope.launch {
            try {
                processIncomingSms(context, intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error in SmsReceiver processing", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun processIncomingSms(context: Context, intent: Intent) {
        val appPreferences = AppPreferences(context)
        val isServiceEnabled = appPreferences.isServiceEnabled.first()

        val messages: Array<SmsMessage> = try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: emptyArray()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse SMS messages from intent", e)
            emptyArray()
        }

        if (messages.isEmpty()) return

        // Assemble multi-part SMS body
        val sender = messages[0].originatingAddress ?: "Unknown"
        val timestamp = messages[0].timestampMillis.takeIf { it > 0 } ?: System.currentTimeMillis()
        val fullBody = messages.joinToString(separator = "") { it.messageBody.orEmpty() }

        // Resolve SIM card
        val simUtil = SimUtil(context)
        val activeSims = simUtil.getActiveSimCards()
        val simSlot = simUtil.extractSimSlotFromIntent(intent, activeSims)
        val matchedSim = activeSims.find { it.slotIndex == simSlot }
        val carrierName = matchedSim?.carrierName ?: "Carrier (Slot ${simSlot + 1})"
        val simDisplayName = matchedSim?.displayName ?: "SIM ${simSlot + 1}"
        val isSensitive = SensitiveFilter.isSensitiveMessage(fullBody)

        Log.d(TAG, "Intercepted SMS from $sender on SIM slot $simSlot ($carrierName): ${fullBody.take(50)}")

        val database = AppDatabase.getInstance(context)
        val simFilter = appPreferences.simFilter.first()

        // Check SIM Filter Option
        val isSimAccepted = when (simFilter) {
            SimFilterOption.ALL -> true
            SimFilterOption.SIM_1 -> simSlot == 0
            SimFilterOption.SIM_2 -> simSlot == 1
        }

        if (!isServiceEnabled) {
            Log.i(TAG, "SMS Forwarder service is disabled. Logging without forwarding.")
            database.smsLogDao().insertLog(
                SmsLogEntity(
                    sender = sender,
                    messageBody = fullBody,
                    simSlotIndex = simSlot,
                    simDisplayName = simDisplayName,
                    carrierName = carrierName,
                    timestamp = timestamp,
                    telegramStatus = ForwardStatus.DISABLED.name,
                    whatsappStatus = ForwardStatus.DISABLED.name,
                    isSensitive = isSensitive,
                    errorMessage = "Service was disabled by user"
                )
            )
            return
        }

        if (!isSimAccepted) {
            Log.i(TAG, "SMS on SIM $simSlot skipped due to filter preference: ${simFilter.name}")
            database.smsLogDao().insertLog(
                SmsLogEntity(
                    sender = sender,
                    messageBody = fullBody,
                    simSlotIndex = simSlot,
                    simDisplayName = simDisplayName,
                    carrierName = carrierName,
                    timestamp = timestamp,
                    telegramStatus = "${ForwardStatus.SKIPPED.name} (Filter: ${simFilter.name})",
                    whatsappStatus = "${ForwardStatus.SKIPPED.name} (Filter: ${simFilter.name})",
                    isSensitive = isSensitive,
                    errorMessage = "Filtered by SIM selection rule"
                )
            )
            return
        }

        // Insert pending log in Room DB
        val logId = database.smsLogDao().insertLog(
            SmsLogEntity(
                sender = sender,
                messageBody = fullBody,
                simSlotIndex = simSlot,
                simDisplayName = simDisplayName,
                carrierName = carrierName,
                timestamp = timestamp,
                telegramStatus = ForwardStatus.PENDING.name,
                whatsappStatus = ForwardStatus.PENDING.name,
                isSensitive = isSensitive
            )
        )

        // Enqueue WorkManager for resilient forwarding
        val workData = workDataOf(
            ForwardWorker.KEY_LOG_ID to logId,
            ForwardWorker.KEY_SENDER to sender,
            ForwardWorker.KEY_BODY to fullBody,
            ForwardWorker.KEY_SIM_SLOT to simSlot,
            ForwardWorker.KEY_TIMESTAMP to timestamp,
            ForwardWorker.KEY_CARRIER to carrierName,
            ForwardWorker.KEY_SUB_ID to (matchedSim?.subscriptionId ?: -1)
        )

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val forwardWorkRequest = OneTimeWorkRequestBuilder<ForwardWorker>()
            .setInputData(workData)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueue(forwardWorkRequest)
        Log.d(TAG, "Enqueued ForwardWorker for SMS #$logId")
    }

    companion object {
        private const val TAG = "SmsReceiver"
    }
}
