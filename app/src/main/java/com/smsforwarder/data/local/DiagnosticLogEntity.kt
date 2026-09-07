package com.smsforwarder.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DiagnosticEventType {
    HEARTBEAT,
    BATTERY_LOW,
    BATTERY_CHARGING,
    SERVICE_STARTED,
    SERVICE_STOPPED,
    CHANNEL_TEST,
    ERROR
}

@Entity(tableName = "diagnostic_logs")
data class DiagnosticLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val eventType: String,
    val message: String,
    val batteryLevel: Int = -1,
    val isCharging: Boolean = false,
    val networkType: String = "UNKNOWN",
    val details: String? = null
)
