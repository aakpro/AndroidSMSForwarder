package com.smsforwarder.data.model

data class SmsMessageItem(
    val sender: String,
    val body: String,
    val timestamp: Long,
    val simSlotIndex: Int,       // 0 = SIM 1, 1 = SIM 2
    val subscriptionId: Int,
    val carrierName: String = "Unknown",
    val simDisplayName: String = "SIM ${simSlotIndex + 1}"
)
