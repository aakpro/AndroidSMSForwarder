package com.smsforwarder.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat

object SmsSenderHelper {
    private const val TAG = "SmsSenderHelper"

    fun sendSms(
        context: Context,
        recipient: String,
        message: String,
        subscriptionId: Int = -1
    ): Result<Unit> {
        val hasSendSmsPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasSendSmsPermission) {
            Log.w(TAG, "SEND_SMS permission not granted")
            return Result.failure(SecurityException("SEND_SMS permission is not granted"))
        }

        val cleanRecipient = recipient.trim()
        val cleanMessage = message.trim()

        if (cleanRecipient.isBlank()) {
            return Result.failure(IllegalArgumentException("Recipient phone number cannot be empty"))
        }
        if (cleanMessage.isBlank()) {
            return Result.failure(IllegalArgumentException("Message content cannot be empty"))
        }

        return try {
            val smsManager = getSmsManager(context, subscriptionId)
            val parts = smsManager.divideMessage(cleanMessage)

            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(cleanRecipient, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(cleanRecipient, null, cleanMessage, null, null)
            }

            Log.d(TAG, "SMS successfully dispatched to $cleanRecipient (subId: $subscriptionId)")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS to $cleanRecipient", e)
            Result.failure(e)
        }
    }

    private fun getSmsManager(context: Context, subscriptionId: Int): SmsManager {
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
}
