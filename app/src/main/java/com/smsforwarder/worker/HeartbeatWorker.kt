package com.smsforwarder.worker

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.*
import com.smsforwarder.data.local.AppDatabase
import com.smsforwarder.data.local.DiagnosticEventType
import com.smsforwarder.data.local.DiagnosticLogEntity
import com.smsforwarder.data.preferences.AppPreferences
import com.smsforwarder.data.preferences.SecurePreferences
import com.smsforwarder.sender.DiscordSender
import com.smsforwarder.sender.GenericWebhookSender
import com.smsforwarder.sender.TelegramSender
import com.smsforwarder.util.NetworkUtil
import com.smsforwarder.util.SimUtil
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class HeartbeatWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "HeartbeatWorker executing health ping")

        val appPrefs = AppPreferences(context)
        val securePrefs = SecurePreferences(context)
        val database = AppDatabase.getInstance(context)

        val isHeartbeatEnabled = appPrefs.isHeartbeatEnabled.first()
        if (!isHeartbeatEnabled) {
            Log.d(TAG, "Heartbeat is disabled in settings. Skipping.")
            return Result.success()
        }

        val batteryInfo = NetworkUtil.getBatteryInfo(context)
        val networkType = NetworkUtil.getNetworkType(context)
        val activeSims = SimUtil(context).getActiveSimCards()
        val isServiceRunning = appPrefs.isServiceEnabled.first()
        val successCount = database.smsLogDao().getSuccessCount().first()

        val simDetails = if (activeSims.isEmpty()) {
            "No active SIM detected"
        } else {
            activeSims.joinToString(", ") { "SIM ${it.slotIndex + 1}: ${it.carrierName}" }
        }

        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val deviceModel = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"

        // Formatted Telegram HTML Message
        val tgMessage = """
            💓 <b>SMS Forwarder Heartbeat</b>
            • <b>Status:</b> ${if (isServiceRunning) "🟢 Running" else "🟡 Idle / Paused"}
            • <b>Device:</b> $deviceModel
            • <b>Battery:</b> ${batteryInfo.percentage}% (${if (batteryInfo.isCharging) "Charging - ${batteryInfo.plugType}" else "Discharging"})
            • <b>Network:</b> $networkType
            • <b>SIMs:</b> $simDetails
            • <b>Forwarded Total:</b> $successCount messages
            • <b>Timestamp:</b> $dateStr
        """.trimIndent()

        // Formatted Plain text for Discord / Webhook
        val plainMessage = """
            💓 **SMS Forwarder Heartbeat**
            • Status: ${if (isServiceRunning) "Online (Service Active)" else "Idle / Paused"}
            • Device: $deviceModel
            • Battery: ${batteryInfo.percentage}% (${if (batteryInfo.isCharging) "Charging - ${batteryInfo.plugType}" else "Discharging"})
            • Network: $networkType
            • SIMs: $simDetails
            • Forwarded Total: $successCount messages
            • Timestamp: $dateStr
        """.trimIndent()

        var sentAny = false

        // Send via Telegram
        if (appPrefs.isTelegramEnabled.first()) {
            val telegramSender = TelegramSender(securePrefs)
            val res = telegramSender.sendMessage(tgMessage)
            if (res.isSuccess) sentAny = true
        }

        // Send via Discord
        if (appPrefs.isDiscordEnabled.first()) {
            val discordSender = DiscordSender(securePrefs)
            val res = discordSender.sendMessage(plainMessage)
            if (res.isSuccess) sentAny = true
        }

        // Send via Generic Webhook
        if (appPrefs.isGenericWebhookEnabled.first()) {
            val webhookSender = GenericWebhookSender(securePrefs)
            val payload = JSONObject().apply {
                put("status", if (isServiceRunning) "ONLINE" else "IDLE")
                put("battery", batteryInfo.percentage)
                put("charging", batteryInfo.isCharging)
                put("network", networkType)
                put("active_sims", activeSims.size)
                put("forwarded_count", successCount)
                put("device", deviceModel)
            }
            val res = webhookSender.sendEvent("heartbeat", payload)
            if (res.isSuccess) sentAny = true
        }

        // Record diagnostic log
        database.diagnosticLogDao().insertLog(
            DiagnosticLogEntity(
                timestamp = System.currentTimeMillis(),
                eventType = DiagnosticEventType.HEARTBEAT.name,
                message = "Heartbeat dispatched: Battery ${batteryInfo.percentage}%, Network $networkType",
                batteryLevel = batteryInfo.percentage,
                isCharging = batteryInfo.isCharging,
                networkType = networkType,
                details = "Delivered to enabled channels: $sentAny. SIMs: $simDetails"
            )
        )

        return Result.success()
    }

    companion object {
        private const val TAG = "HeartbeatWorker"
        const val WORK_NAME = "sms_forwarder_heartbeat"

        fun schedule(context: Context, intervalHours: Int = 12) {
            val safeHours = intervalHours.coerceIn(1, 24).toLong()
            val request = PeriodicWorkRequestBuilder<HeartbeatWorker>(safeHours, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
            Log.d(TAG, "Heartbeat scheduled every $safeHours hours")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Heartbeat work cancelled")
        }
    }
}
