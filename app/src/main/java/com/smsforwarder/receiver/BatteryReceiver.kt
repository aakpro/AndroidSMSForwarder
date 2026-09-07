package com.smsforwarder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.smsforwarder.data.local.AppDatabase
import com.smsforwarder.data.local.DiagnosticEventType
import com.smsforwarder.data.local.DiagnosticLogEntity
import com.smsforwarder.data.preferences.AppPreferences
import com.smsforwarder.data.preferences.SecurePreferences
import com.smsforwarder.sender.DiscordSender
import com.smsforwarder.sender.TelegramSender
import com.smsforwarder.util.NetworkUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BatteryReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.d(TAG, "Received broadcast action: $action")

        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO)

        scope.launch {
            try {
                val appPrefs = AppPreferences(context)
                val isAlertEnabled = appPrefs.isLowBatteryAlertEnabled.first()
                if (!isAlertEnabled) return@launch

                val batteryInfo = NetworkUtil.getBatteryInfo(context)
                val networkType = NetworkUtil.getNetworkType(context)
                val database = AppDatabase.getInstance(context)

                // Debounce: don't send multiple low battery alerts within 30 minutes
                val now = System.currentTimeMillis()
                if (now - lastAlertTime < 30 * 60 * 1000) {
                    Log.d(TAG, "Low battery alert debounced (sent recently)")
                    return@launch
                }

                if (action == Intent.ACTION_BATTERY_LOW || (batteryInfo.percentage in 1..15 && !batteryInfo.isCharging)) {
                    lastAlertTime = now
                    val alertMessage = "⚠️ <b>[SMS Forwarder Alert]</b>\nRelay phone battery is critically low (${batteryInfo.percentage}%)! Please connect a charger immediately to avoid missed SMS forwards."
                    val plainAlert = "⚠️ **[SMS Forwarder Alert]**\nRelay phone battery is critically low (${batteryInfo.percentage}%)! Please connect a charger immediately to avoid missed SMS forwards."

                    val securePrefs = SecurePreferences(context)
                    if (appPrefs.isTelegramEnabled.first()) {
                        TelegramSender(securePrefs).sendMessage(alertMessage)
                    }
                    if (appPrefs.isDiscordEnabled.first()) {
                        DiscordSender(securePrefs).sendMessage(plainAlert)
                    }

                    database.diagnosticLogDao().insertLog(
                        DiagnosticLogEntity(
                            timestamp = now,
                            eventType = DiagnosticEventType.BATTERY_LOW.name,
                            message = "Low battery warning triggered at ${batteryInfo.percentage}%",
                            batteryLevel = batteryInfo.percentage,
                            isCharging = batteryInfo.isCharging,
                            networkType = networkType,
                            details = "Alert dispatched to Telegram/Discord"
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling battery event", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "BatteryReceiver"
        private var lastAlertTime = 0L
    }
}
