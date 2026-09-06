package com.smsforwarder

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.smsforwarder.data.local.AppDatabase

class SmsForwarderApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Pre-initialize Room Database
        AppDatabase.getInstance(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return

            val serviceChannel = NotificationChannel(
                "sms_forwarder_foreground_channel",
                getString(R.string.channel_name_forwarder),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_desc_forwarder)
                setShowBadge(false)
            }

            val alertsChannel = NotificationChannel(
                "whatsapp_drafts_channel",
                getString(R.string.channel_name_alerts),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.channel_desc_alerts)
            }

            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(alertsChannel)
        }
    }
}
