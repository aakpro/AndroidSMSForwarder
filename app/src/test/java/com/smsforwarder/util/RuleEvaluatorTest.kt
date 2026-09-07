package com.smsforwarder.util

import com.smsforwarder.data.local.FilterRuleEntity
import com.smsforwarder.data.local.MatchField
import com.smsforwarder.data.local.MatchType
import com.smsforwarder.data.local.RuleAction
import org.junit.Assert.*
import org.junit.Test

class RuleEvaluatorTest {

    @Test
    fun `test CONTAINS keyword matching with multiple keywords`() {
        val rule = FilterRuleEntity(
            name = "Spam Blocker",
            pattern = "offer, discount, promo, win free",
            matchField = MatchField.BODY.key,
            matchType = MatchType.CONTAINS.key,
            action = RuleAction.BLOCK_AND_SKIP.key,
            caseSensitive = false
        )

        val result1 = RuleEvaluator.evaluate("+12345", "Get 50% discount on your next ride!", listOf(rule))
        assertTrue(result1.isBlocked)
        assertEquals("Spam Blocker", result1.matchedRule?.name)

        val result2 = RuleEvaluator.evaluate("+12345", "Congratulations you win free tokens", listOf(rule))
        assertTrue(result2.isBlocked)

        val result3 = RuleEvaluator.evaluate("+12345", "Your taxi has arrived at your location", listOf(rule))
        assertFalse(result3.isBlocked)
    }

    @Test
    fun `test case sensitivity in matching`() {
        val ruleSensitive = FilterRuleEntity(
            name = "Exact Case",
            pattern = "URGENT",
            matchField = MatchField.BODY.key,
            matchType = MatchType.CONTAINS.key,
            caseSensitive = true
        )

        assertFalse(RuleEvaluator.matches(ruleSensitive, "+1", "this is urgent action"))
        assertTrue(RuleEvaluator.matches(ruleSensitive, "+1", "this is URGENT action"))
    }

    @Test
    fun `test SENDER matching`() {
        val rule = FilterRuleEntity(
            name = "Ignore Telemarketer",
            pattern = "1800SPAM, +18005550199",
            matchField = MatchField.SENDER.key,
            matchType = MatchType.CONTAINS.key,
            action = RuleAction.BLOCK_AND_SKIP.key
        )

        val result = RuleEvaluator.evaluate("+18005550199", "Important account notice", listOf(rule))
        assertTrue(result.isBlocked)

        val allowedResult = RuleEvaluator.evaluate("+15551234567", "Important account notice", listOf(rule))
        assertFalse(allowedResult.isBlocked)
    }

    @Test
    fun `test REGEX matching and malformed pattern safety`() {
        val regexRule = FilterRuleEntity(
            name = "OTP Regex",
            pattern = """\b\d{4,6}\b""",
            matchField = MatchField.BODY.key,
            matchType = MatchType.REGEX.key,
            action = RuleAction.FORWARD_SELECTED.key,
            targetChannels = "TELEGRAM,EMAIL"
        )

        val result = RuleEvaluator.evaluate("AuthService", "Your security code is 849201", listOf(regexRule))
        assertFalse(result.isBlocked)
        assertEquals("OTP Regex", result.matchedRule?.name)
        assertEquals(setOf("TELEGRAM", "EMAIL"), result.allowedChannels)

        // Invalid regex should not crash and should return false
        val invalidRule = FilterRuleEntity(
            name = "Invalid Regex",
            pattern = "[a-z", // Unclosed bracket
            matchType = MatchType.REGEX.key
        )
        assertFalse(RuleEvaluator.matches(invalidRule, "+1", "test"))
    }

    @Test
    fun `test rule priority ordering`() {
        val lowPriorityBlock = FilterRuleEntity(
            id = 1,
            name = "Block All Promos",
            pattern = "sale",
            action = RuleAction.BLOCK_AND_SKIP.key,
            priority = 10
        )

        val highPriorityAllow = FilterRuleEntity(
            id = 2,
            name = "Allow VIP Bank",
            pattern = "Chase Bank",
            matchField = MatchField.ANY.key,
            action = RuleAction.FORWARD_ALL.key,
            priority = 1
        )

        // Text matches both: contains "sale" and from "Chase Bank"
        val rules = listOf(lowPriorityBlock, highPriorityAllow)
        val result = RuleEvaluator.evaluate("Chase Bank", "Flash sale for cardholders", rules)

        // High priority rule (priority 1) must evaluate first!
        assertFalse(result.isBlocked)
        assertEquals("Allow VIP Bank", result.matchedRule?.name)
    }

    @Test
    fun `test disabled rules are ignored`() {
        val disabledRule = FilterRuleEntity(
            name = "Disabled Filter",
            pattern = "blockedword",
            action = RuleAction.BLOCK_AND_SKIP.key,
            isEnabled = false
        )

        val result = RuleEvaluator.evaluate("Sender", "Message with blockedword", listOf(disabledRule))
        assertFalse(result.isBlocked)
    }

    @Test
    fun `test STARTS_WITH and ENDS_WITH`() {
        val startRule = FilterRuleEntity(
            name = "Prefix Rule",
            pattern = "#ALERT, [URGENT]",
            matchType = MatchType.STARTS_WITH.key
        )
        assertTrue(RuleEvaluator.matches(startRule, "+1", "#ALERT: System reboot scheduled"))
        assertFalse(RuleEvaluator.matches(startRule, "+1", "System reboot scheduled #ALERT"))

        val endRule = FilterRuleEntity(
            name = "Suffix Rule",
            pattern = "STOP, UNSUBSCRIBE",
            matchType = MatchType.ENDS_WITH.key
        )
        assertTrue(RuleEvaluator.matches(endRule, "+1", "Reply to opt out: STOP"))
        assertFalse(RuleEvaluator.matches(endRule, "+1", "STOP now for offers"))
    }
}
