package com.smsforwarder.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MatchField(val key: String, val title: String) {
    BODY("BODY", "Message Body"),
    SENDER("SENDER", "Sender (Phone / Name)"),
    ANY("ANY", "Sender or Body");

    companion object {
        fun fromKey(key: String): MatchField =
            entries.find { it.key.equals(key, ignoreCase = true) } ?: BODY
    }
}

enum class MatchType(val key: String, val title: String) {
    CONTAINS("CONTAINS", "Contains Keyword(s)"),
    NOT_CONTAINS("NOT_CONTAINS", "Does Not Contain"),
    STARTS_WITH("STARTS_WITH", "Starts With"),
    ENDS_WITH("ENDS_WITH", "Ends With"),
    EXACT("EXACT", "Exact Match"),
    REGEX("REGEX", "Regular Expression (Regex)");

    companion object {
        fun fromKey(key: String): MatchType =
            entries.find { it.key.equals(key, ignoreCase = true) } ?: CONTAINS
    }
}

enum class RuleAction(val key: String, val title: String) {
    BLOCK_AND_SKIP("BLOCK_AND_SKIP", "Block (Do Not Forward)"),
    FORWARD_ALL("FORWARD_ALL", "Forward to All Channels"),
    FORWARD_SELECTED("FORWARD_SELECTED", "Forward to Selected Channels Only");

    companion object {
        fun fromKey(key: String): RuleAction =
            entries.find { it.key.equals(key, ignoreCase = true) } ?: BLOCK_AND_SKIP
    }
}

@Entity(tableName = "filter_rules")
data class FilterRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val isEnabled: Boolean = true,
    val matchField: String = MatchField.BODY.key,
    val matchType: String = MatchType.CONTAINS.key,
    val pattern: String,
    val caseSensitive: Boolean = false,
    val action: String = RuleAction.BLOCK_AND_SKIP.key,
    val targetChannels: String = "ALL", // Comma-separated: "TELEGRAM,EMAIL" or "ALL"
    val priority: Int = 0
)
