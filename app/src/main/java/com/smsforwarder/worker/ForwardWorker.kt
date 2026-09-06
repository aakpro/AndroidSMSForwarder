package com.smsforwarder.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.smsforwarder.data.model.SmsMessageItem
import com.smsforwarder.data.preferences.AppPreferences
import com.smsforwarder.data.preferences.SecurePreferences
import com.smsforwarder.sender.ForwarderManager

class ForwardWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val logId = inputData.getLong(KEY_LOG_ID, -1L)
        val sender = inputData.getString(KEY_SENDER).orEmpty()
        val body = inputData.getString(KEY_BODY).orEmpty()
        val simSlot = inputData.getInt(KEY_SIM_SLOT, 0)
        val timestamp = inputData.getLong(KEY_TIMESTAMP, System.currentTimeMillis())
        val carrier = inputData.getString(KEY_CARRIER) ?: "Unknown"
        val subId = inputData.getInt(KEY_SUB_ID, -1)

        if (logId == -1L || sender.isBlank()) {
            Log.e(TAG, "Invalid work data for ForwardWorker")
            return Result.failure()
        }

        val sms = SmsMessageItem(
            sender = sender,
            body = body,
            timestamp = timestamp,
            simSlotIndex = simSlot,
            subscriptionId = subId,
            carrierName = carrier
        )

        val appPreferences = AppPreferences(applicationContext)
        val securePreferences = SecurePreferences(applicationContext)
        val manager = ForwarderManager(applicationContext, appPreferences, securePreferences)

        Log.d(TAG, "ForwardWorker executing for SMS #$logId (attempt $runAttemptCount)")

        return try {
            val success = manager.forwardSms(sms, logId)
            if (success) {
                Result.success()
            } else if (runAttemptCount < 3) {
                Log.w(TAG, "Forwarding failed, scheduling retry (attempt $runAttemptCount)")
                Result.retry()
            } else {
                Log.e(TAG, "Forwarding permanently failed after $runAttemptCount attempts")
                Result.failure()
            }
        } catch (e: Exception) {
            Log.e(TAG, "ForwardWorker encountered exception", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "ForwardWorker"
        const val KEY_LOG_ID = "arg_log_id"
        const val KEY_SENDER = "arg_sender"
        const val KEY_BODY = "arg_body"
        const val KEY_SIM_SLOT = "arg_sim_slot"
        const val KEY_TIMESTAMP = "arg_timestamp"
        const val KEY_CARRIER = "arg_carrier"
        const val KEY_SUB_ID = "arg_sub_id"
    }
}
