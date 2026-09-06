package com.smsforwarder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.smsforwarder.data.preferences.AppPreferences
import com.smsforwarder.service.SmsForwarderService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.d(TAG, "Device booted, checking if SMS Forwarder should restart...")
        val scope = CoroutineScope(Dispatchers.IO)
        val pendingResult = goAsync()

        scope.launch {
            try {
                val appPreferences = AppPreferences(context)
                val isServiceEnabled = appPreferences.isServiceEnabled.first()

                if (isServiceEnabled) {
                    Log.d(TAG, "SMS Forwarder was enabled, starting foreground service")
                    val serviceIntent = Intent(context, SmsForwarderService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in BootReceiver restart", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
