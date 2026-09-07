package com.smsforwarder.util

import com.smsforwarder.data.local.FilterRuleEntity
import com.smsforwarder.data.local.MatchField
import com.smsforwarder.data.local.MatchType
import com.smsforwarder.data.local.RuleAction
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

data class RuleEvaluationResult(
    val isBlocked: Boolean,
    val matchedRule: FilterRuleEntity? = null,
    val reason: String = "No rules triggered",
    val allowedChannels: Set<String>? = null // null indicates all enabled channels are allowed
)

object RuleEvaluator {

    fun evaluate(sender: String, body: String, rules: List<FilterRuleEntity>): RuleEvaluationResult {
        val activeRules = rules.filter { it.isEnabled }.sortedBy { it.priority }

        for (rule in activeRules) {
            if (matches(rule, sender, body)) {
                return when (RuleAction.fromKey(rule.action)) {
                    RuleAction.BLOCK_AND_SKIP -> {
                        RuleEvaluationResult(
                            isBlocked = true,
                            matchedRule = rule,
                            reason = "Blocked by rule: '${rule.name}'"
                        )
                    }
                    RuleAction.FORWARD_SELECTED -> {
                        val channels = parseChannels(rule.targetChannels)
                        RuleEvaluationResult(
                            isBlocked = false,
                            matchedRule = rule,
                            reason = "Matched rule: '${rule.name}' (Routing to: ${channels.joinToString(", ")})",
                            allowedChannels = channels
                        )
                    }
                    RuleAction.FORWARD_ALL -> {
                        RuleEvaluationResult(
                            isBlocked = false,
                            matchedRule = rule,
                            reason = "Matched whitelist rule: '${rule.name}'",
                            allowedChannels = null
                        )
                    }
                }
            }
        }

        return RuleEvaluationResult(
            isBlocked = false,
            matchedRule = null,
            reason = "Allowed by default (No matching filter rules)"
        )
    }

    fun matches(rule: FilterRuleEntity, sender: String, body: String): Boolean {
        val targetText = when (MatchField.fromKey(rule.matchField)) {
            MatchField.BODY -> body
            MatchField.SENDER -> sender
            MatchField.ANY -> "$sender\n$body"
        }

        val matchType = MatchType.fromKey(rule.matchType)
        if (matchType == MatchType.REGEX) {
            return matchesRegex(rule.pattern, targetText, rule.caseSensitive)
        }

        // Split keywords by comma
        val keywords = rule.pattern.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (keywords.isEmpty()) return false

        val compareText = if (rule.caseSensitive) targetText else targetText.lowercase()

        return when (matchType) {
            MatchType.CONTAINS -> {
                keywords.any { kw ->
                    val term = if (rule.caseSensitive) kw else kw.lowercase()
                    compareText.contains(term)
                }
            }
            MatchType.NOT_CONTAINS -> {
                keywords.none { kw ->
                    val term = if (rule.caseSensitive) kw else kw.lowercase()
                    compareText.contains(term)
                }
            }
            MatchType.STARTS_WITH -> {
                keywords.any { kw ->
                    val term = if (rule.caseSensitive) kw else kw.lowercase()
                    compareText.startsWith(term)
                }
            }
            MatchType.ENDS_WITH -> {
                keywords.any { kw ->
                    val term = if (rule.caseSensitive) kw else kw.lowercase()
                    compareText.endsWith(term)
                }
            }
            MatchType.EXACT -> {
                keywords.any { kw ->
                    val term = if (rule.caseSensitive) kw else kw.lowercase()
                    compareText == term
                }
            }
            MatchType.REGEX -> false // Handled above
        }
    }

    private fun matchesRegex(regexPattern: String, text: String, caseSensitive: Boolean): Boolean {
        return try {
            val flags = if (caseSensitive) 0 else (Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE)
            val pattern = Pattern.compile(regexPattern, flags)
            pattern.matcher(text).find()
        } catch (e: PatternSyntaxException) {
            false
        }
    }

    private fun parseChannels(channelsRaw: String): Set<String> {
        if (channelsRaw.isBlank() || channelsRaw.equals("ALL", ignoreCase = true)) {
            return setOf("TELEGRAM", "WHATSAPP", "DISCORD", "WEBHOOK", "EMAIL")
        }
        return channelsRaw.split(",", ";")
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }
            .toSet()
    }
}
