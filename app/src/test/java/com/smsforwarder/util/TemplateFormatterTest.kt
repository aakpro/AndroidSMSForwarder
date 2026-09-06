package com.smsforwarder.util

import com.smsforwarder.data.model.SmsMessageItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateFormatterTest {

    @Test
    fun `test template placeholder replacement`() {
        val sms = SmsMessageItem(
            sender = "+1234567890",
            body = "Your verification code is 482019",
            timestamp = 1757182359000L, // Fixed timestamp
            simSlotIndex = 1,
            subscriptionId = 2,
            carrierName = "Vodafone"
        )

        val template = "SIM: {sim} | Carrier: {carrier} | From: {sender} | Msg: {message}"
        val formatted = TemplateFormatter.format(template, sms, escapeHtml = false)

        assertEquals("SIM: SIM 2 | Carrier: Vodafone | From: +1234567890 | Msg: Your verification code is 482019", formatted)
    }

    @Test
    fun `test HTML escaping for Telegram`() {
        val sms = SmsMessageItem(
            sender = "<UnknownBank>",
            body = "Code: <12345> & info",
            timestamp = System.currentTimeMillis(),
            simSlotIndex = 0,
            subscriptionId = 1,
            carrierName = "T-Mobile"
        )

        val template = "From: {sender}\nMsg: {message}"
        val formatted = TemplateFormatter.format(template, sms, escapeHtml = true)

        assertTrue(formatted.contains("&lt;UnknownBank&gt;"))
        assertTrue(formatted.contains("&lt;12345&gt; &amp; info"))
    }
}
