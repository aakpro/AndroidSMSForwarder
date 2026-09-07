package com.smsforwarder.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.smsforwarder.R
import com.smsforwarder.data.preferences.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun AutoReplyCard(
    appPreferences: AppPreferences,
    coroutineScope: CoroutineScope,
    onRequestSendSmsPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val isEnabled by appPreferences.isAutoReplyEnabled.collectAsState(initial = false)
    val template by appPreferences.autoReplyTemplate.collectAsState(initial = AppPreferences.DEFAULT_AUTO_REPLY_TEMPLATE)
    var editableTemplate by remember(template) { mutableStateOf(template) }

    val cooldownMinutes by appPreferences.autoReplyCooldownMinutes.collectAsState(initial = 15)
    val simChoice by appPreferences.autoReplySimSlot.collectAsState(initial = -1)

    val hasSendSmsPermission = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Filled.Reply,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.section_auto_reply),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { checked ->
                        coroutineScope.launch { appPreferences.setAutoReplyEnabled(checked) }
                    }
                )
            }

            Text(
                text = stringResource(R.string.auto_reply_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            if (!hasSendSmsPermission.value && isEnabled) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.auto_reply_permission_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = onRequestSendSmsPermission,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Grant", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            AnimatedVisibility(visible = isEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Template Text Field
                    OutlinedTextField(
                        value = editableTemplate,
                        onValueChange = {
                            editableTemplate = it
                            coroutineScope.launch { appPreferences.setAutoReplyTemplate(it) }
                        },
                        label = { Text(stringResource(R.string.auto_reply_template)) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4
                    )

                    // Cooldown (Anti-Loop) Selection
                    Text(
                        text = stringResource(R.string.auto_reply_cooldown),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(5, 15, 60, 1440).forEach { mins ->
                            val label = when (mins) {
                                5 -> "5 mins"
                                15 -> "15 mins"
                                60 -> "1 hour"
                                else -> "24 hours"
                            }
                            FilterChip(
                                selected = cooldownMinutes == mins,
                                onClick = {
                                    coroutineScope.launch {
                                        appPreferences.setAutoReplyCooldownMinutes(mins)
                                    }
                                },
                                label = { Text(label) }
                            )
                        }
                    }

                    // Reply SIM Selector
                    Text(
                        text = stringResource(R.string.auto_reply_sim),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            -1 to "Same SIM",
                            0 to "SIM 1",
                            1 to "SIM 2"
                        ).forEach { (slot, title) ->
                            FilterChip(
                                selected = simChoice == slot,
                                onClick = {
                                    coroutineScope.launch {
                                        appPreferences.setAutoReplySimSlot(slot)
                                    }
                                },
                                label = { Text(title) }
                            )
                        }
                    }
                }
            }
        }
    }
}
