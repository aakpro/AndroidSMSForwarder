package com.smsforwarder.util

import org.junit.Assert.*
import org.junit.Test

class SensitiveFilterTest {

    @Test
    fun `test OTP detection on banking and auth messages`() {
        val bankOtp = "Your Bank verification code is 849201. Valid for 5 minutes."
        val login2fa = "Google verification code: 394821. Do not share this code."
        val regularText = "Hey are you coming to dinner tonight at 7pm?"
        val addressText = "Meeting at 4520 Main Street room 102"

        assertTrue(SensitiveFilter.isSensitiveMessage(bankOtp))
        assertTrue(SensitiveFilter.isSensitiveMessage(login2fa))
        assertFalse(SensitiveFilter.isSensitiveMessage(regularText))
        assertFalse(SensitiveFilter.isSensitiveMessage(addressText))
    }

    @Test
    fun `test redaction of sensitive digits`() {
        val message = "Your OTP is 948201 for payment"
        val redacted = SensitiveFilter.redactSensitiveDigits(message)

        assertEquals("Your OTP is [REDACTED] for payment", redacted)
    }
}
