package com.smsforwarder.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sms_logs")
data class SmsLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sender: String,
    val messageBody: String,
    val simSlotIndex: Int,          // 0 = SIM 1, 1 = SIM 2
    val simDisplayName: String,
    val carrierName: String,
    val timestamp: Long,
    val telegramStatus: String,     // PENDING, SUCCESS, FAILED, SKIPPED, DISABLED
    val whatsappStatus: String,     // PENDING, SUCCESS, FAILED, SKIPPED, DISABLED, DRAFT_CREATED
    val isSensitive: Boolean = false, // True if OTP / banking keywords matched
    val errorMessage: String? = null
)
