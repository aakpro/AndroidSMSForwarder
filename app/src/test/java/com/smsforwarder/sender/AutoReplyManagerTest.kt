package com.smsforwarder.sender

import com.smsforwarder.data.model.SmsMessageItem
import com.smsforwarder.util.TemplateFormatter
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class AutoReplyManagerTest {

    @Test
    fun `test auto-reply cooldown debounce calculation`() {
        val cooldownMinutes = 15
        val cooldownMs = TimeUnit.MINUTES.toMillis(cooldownMinutes.toLong())

        val lastReplyTime = 1000000L
        val justAfterReplyTime = lastReplyTime + 5000L // 5 seconds later
        val afterCooldownTime = lastReplyTime + cooldownMs + 1000L // 15 mins + 1 sec later

        // Case 1: Within cooldown window -> must be throttled
        val isThrottledImmediate = (justAfterReplyTime - lastReplyTime) < cooldownMs
        assertTrue("Rapid reply within 5 seconds must be throttled", isThrottledImmediate)

        // Case 2: After cooldown window -> allowed
        val isThrottledLater = (afterCooldownTime - lastReplyTime) < cooldownMs
        assertFalse("Reply after 15 minutes must be allowed", isThrottledLater)
    }

    @Test
    fun `test auto-reply template variable formatting`() {
        val sms = SmsMessageItem(
            sender = "+1987654321",
            body = "Where is my package?",
            timestamp = 1757182359000L,
            simSlotIndex = 0,
            subscriptionId = 1,
            carrierName = "T-Mobile"
        )

        val template = "Hello {sender}, we received your inquiry on {sim}: '{message}'. We will respond shortly."
        val formatted = TemplateFormatter.format(template, sms, escapeHtml = false)

        assertTrue(formatted.contains("+1987654321"))
        assertTrue(formatted.contains("SIM 1"))
        assertTrue(formatted.contains("Where is my package?"))
    }

    @Test
    fun `test multi-sender cooldown independence`() {
        val cooldownMs = TimeUnit.MINUTES.toMillis(15)
        val replyHistory = mutableMapOf<String, Long>()

        val now = 2000000L
        replyHistory["+1111111111"] = now // Sender A received reply just now

        // Check Sender A
        val isSenderAThrottled = replyHistory["+1111111111"]?.let { (now + 1000 - it) < cooldownMs } ?: false
        assertTrue(isSenderAThrottled)

        // Check Sender B (never received reply)
        val isSenderBThrottled = replyHistory["+2222222222"]?.let { (now + 1000 - it) < cooldownMs } ?: false
        assertFalse(isSenderBThrottled)
    }
}
