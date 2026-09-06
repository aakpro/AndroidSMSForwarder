package com.smsforwarder.data.model

enum class ForwardingDestination(val displayName: String) {
    TELEGRAM("Telegram"),
    WHATSAPP("WhatsApp")
}

enum class ForwardStatus(val label: String) {
    PENDING("Pending"),
    SUCCESS("Success"),
    FAILED("Failed"),
    SKIPPED("Skipped"),
    DISABLED("Disabled"),
    DRAFT_CREATED("Draft Ready")
}

data class ForwardResult(
    val destination: ForwardingDestination,
    val status: ForwardStatus,
    val message: String? = null
)
