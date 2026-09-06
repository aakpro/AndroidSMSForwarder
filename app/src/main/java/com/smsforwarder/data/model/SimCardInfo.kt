package com.smsforwarder.data.model

data class SimCardInfo(
    val slotIndex: Int,          // 0 for SIM 1, 1 for SIM 2
    val subscriptionId: Int,     // Android system subId
    val displayName: String,     // e.g. "SIM 1" or user custom name
    val carrierName: String,     // e.g. "T-Mobile", "Vodafone"
    val phoneNumber: String? = null
)

enum class SimFilterOption(val key: String, val title: String) {
    ALL("ALL", "Both SIMs (All Incoming SMS)"),
    SIM_1("SIM_1", "SIM 1 Only"),
    SIM_2("SIM_2", "SIM 2 Only");

    companion object {
        fun fromKey(key: String): SimFilterOption =
            entries.find { it.key.equals(key, ignoreCase = true) } ?: ALL
    }
}
