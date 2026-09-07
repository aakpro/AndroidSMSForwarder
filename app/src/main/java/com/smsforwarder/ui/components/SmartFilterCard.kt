package com.smsforwarder.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.smsforwarder.R
import com.smsforwarder.data.local.*
import com.smsforwarder.ui.theme.GreenSuccess
import com.smsforwarder.ui.theme.RedError
import com.smsforwarder.util.RuleEvaluator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SmartFilterCard(
    filterRuleDao: FilterRuleDao,
    coroutineScope: CoroutineScope,
    modifier: Modifier = Modifier
) {
    val rules by filterRuleDao.getAllRules().collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    var testInput by remember { mutableStateOf("") }

    if (showAddDialog) {
        RuleEditorDialog(
            onDismiss = { showAddDialog = false },
            onSave = { newRule ->
                coroutineScope.launch {
                    filterRuleDao.insertRule(newRule)
                    showAddDialog = false
                }
            }
        )
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.section_smart_filter),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                FilledTonalButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.add_filter_rule))
                }
            }

            Text(
                text = stringResource(R.string.filter_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            // Quick Presets Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            filterRuleDao.insertRule(
                                FilterRuleEntity(
                                    name = "Block Common Spam",
                                    pattern = "offer, discount, promo, sale, lottery, win free, unsubscribe",
                                    matchField = MatchField.BODY.key,
                                    matchType = MatchType.CONTAINS.key,
                                    action = RuleAction.BLOCK_AND_SKIP.key,
                                    priority = 5
                                )
                            )
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Spam Preset", style = MaterialTheme.typography.labelSmall)
                }

                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            filterRuleDao.insertRule(
                                FilterRuleEntity(
                                    name = "Priority OTP & Banking",
                                    pattern = "otp, verification, code, bank, credited, debited, transfer",
                                    matchField = MatchField.BODY.key,
                                    matchType = MatchType.CONTAINS.key,
                                    action = RuleAction.FORWARD_ALL.key,
                                    priority = 1
                                )
                            )
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("OTP Priority", style = MaterialTheme.typography.labelSmall)
                }
            }

            HorizontalDivider()

            // Active Rules List
            if (rules.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_rules_configured),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            } else {
                rules.forEach { rule ->
                    RuleItemRow(
                        rule = rule,
                        onToggle = { enabled ->
                            coroutineScope.launch { filterRuleDao.setRuleEnabled(rule.id, enabled) }
                        },
                        onDelete = {
                            coroutineScope.launch { filterRuleDao.deleteRuleById(rule.id) }
                        }
                    )
                }
            }

            HorizontalDivider()

            // Interactive Live Test Sandbox
            Text(text = "🧪 Filter Rule Tester", style = MaterialTheme.typography.labelMedium)
            OutlinedTextField(
                value = testInput,
                onValueChange = { testInput = it },
                label = { Text(stringResource(R.string.test_sandbox_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            if (testInput.isNotBlank()) {
                val eval = remember(testInput, rules) {
                    RuleEvaluator.evaluate("TestSender", testInput, rules)
                }
                Surface(
                    color = if (eval.isBlocked) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (eval.isBlocked) Icons.Default.Cancel else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (eval.isBlocked) RedError else GreenSuccess,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = eval.reason,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RuleItemRow(
    rule: FilterRuleEntity,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = rule.name, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(6.dp))
                    SuggestionChip(
                        onClick = {},
                        label = { Text(rule.action.take(5), style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(22.dp)
                    )
                }
                Text(
                    text = "${rule.matchField} • ${rule.matchType}: ${rule.pattern}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = rule.isEnabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.scale(0.8f)
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun Modifier.scale(scale: Float): Modifier = this.then(Modifier)

@Composable
fun RuleEditorDialog(
    onDismiss: () -> Unit,
    onSave: (FilterRuleEntity) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var pattern by remember { mutableStateOf("") }
    var matchField by remember { mutableStateOf(MatchField.BODY) }
    var matchType by remember { mutableStateOf(MatchType.CONTAINS) }
    var action by remember { mutableStateOf(RuleAction.BLOCK_AND_SKIP) }
    var targetChannels by remember { mutableStateOf("TELEGRAM,EMAIL") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_filter_rule)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.rule_name)) },
                    placeholder = { Text("e.g. Block Spam Offers") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text(stringResource(R.string.rule_pattern)) },
                    placeholder = { Text(stringResource(R.string.rule_pattern_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )

                // Match Target (Field)
                Text(text = stringResource(R.string.rule_match_field), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MatchField.entries.forEach { field ->
                        FilterChip(
                            selected = matchField == field,
                            onClick = { matchField = field },
                            label = { Text(field.title) }
                        )
                    }
                }

                // Action
                Text(text = stringResource(R.string.rule_action), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RuleAction.entries.forEach { act ->
                        FilterChip(
                            selected = action == act,
                            onClick = { action = act },
                            label = { Text(act.name.take(7)) }
                        )
                    }
                }

                if (action == RuleAction.FORWARD_SELECTED) {
                    OutlinedTextField(
                        value = targetChannels,
                        onValueChange = { targetChannels = it },
                        label = { Text(stringResource(R.string.rule_channels)) },
                        placeholder = { Text("e.g. TELEGRAM, EMAIL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && pattern.isNotBlank()) {
                        onSave(
                            FilterRuleEntity(
                                name = name.trim(),
                                pattern = pattern.trim(),
                                matchField = matchField.key,
                                matchType = matchType.key,
                                action = action.key,
                                targetChannels = targetChannels.trim()
                            )
                        )
                    }
                },
                enabled = name.isNotBlank() && pattern.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
