package com.smsforwarder.util

object SensitiveFilter {

    private val KEYWORDS_REGEX = Regex(
        pattern = "\\b(otp|code|verification|passcode|password|bank|login|auth|2fa|security|token|pin|cvv|one[- ]time)\\b",
        options = setOf(RegexOption.IGNORE_CASE)
    )

    private val DIGIT_CODE_REGEX = Regex("\\b\\d{4,8}\\b")

    /**
     * Determines whether an SMS likely contains an OTP, banking transaction alert, or 2FA code.
     */
    fun isSensitiveMessage(body: String): Boolean {
        val hasKeyword = KEYWORDS_REGEX.containsMatchIn(body)
        val hasDigits = DIGIT_CODE_REGEX.containsMatchIn(body)
        return hasKeyword && hasDigits
    }

    /**
     * Replaces numerical OTP/passcode digits with [REDACTED] to protect credentials when
     * sending over third-party relays.
     */
    fun redactSensitiveDigits(body: String): String {
        return DIGIT_CODE_REGEX.replace(body, "[REDACTED]")
    }
}
