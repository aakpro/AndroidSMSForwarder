package com.smsforwarder.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.smsforwarder.data.local.AppDatabase
import com.smsforwarder.data.local.SmsLogEntity
import com.smsforwarder.ui.theme.GreenSuccess
import com.smsforwarder.ui.theme.OrangeWarning
import com.smsforwarder.ui.theme.RedError
import com.smsforwarder.worker.ForwardWorker
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class LogFilter {
    ALL, SIM_1, SIM_2, FAILED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val database = remember { AppDatabase.getInstance(context) }

    var currentFilter by remember { mutableStateOf(LogFilter.ALL) }
    var showClearDialog by remember { mutableStateOf(false) }

    val logsFlow = remember(currentFilter) {
        when (currentFilter) {
            LogFilter.ALL -> database.smsLogDao().getRecentLogs(200)
            LogFilter.SIM_1 -> database.smsLogDao().getLogsBySimSlot(0, 200)
            LogFilter.SIM_2 -> database.smsLogDao().getLogsBySimSlot(1, 200)
            LogFilter.FAILED -> database.smsLogDao().getFailedLogs()
        }
    }
    val logs by logsFlow.collectAsState(initial = emptyList())

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear All Logs") },
            text = { Text("Are you sure you want to permanently delete all SMS forwarding logs?") },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    coroutineScope.launch { database.smsLogDao().clearAll() }
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Forwarding Logs") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    if (logs.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear All")
                        }
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = currentFilter == LogFilter.ALL,
                    onClick = { currentFilter = LogFilter.ALL },
                    label = { Text("All (${logs.size})") }
                )
                FilterChip(
                    selected = currentFilter == LogFilter.SIM_1,
                    onClick = { currentFilter = LogFilter.SIM_1 },
                    label = { Text("SIM 1") }
                )
                FilterChip(
                    selected = currentFilter == LogFilter.SIM_2,
                    onClick = { currentFilter = LogFilter.SIM_2 },
                    label = { Text("SIM 2") }
                )
                FilterChip(
                    selected = currentFilter == LogFilter.FAILED,
                    onClick = { currentFilter = LogFilter.FAILED },
                    label = { Text("Failed") }
                )
            }

            HorizontalDivider()

            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Inbox,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No forwarding activity yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(logs, key = { it.id }) { log ->
                        LogItemCard(
                            log = log,
                            onRetry = {
                                val workData = workDataOf(
                                    ForwardWorker.KEY_LOG_ID to log.id,
                                    ForwardWorker.KEY_SENDER to log.sender,
                                    ForwardWorker.KEY_BODY to log.messageBody,
                                    ForwardWorker.KEY_SIM_SLOT to log.simSlotIndex,
                                    ForwardWorker.KEY_TIMESTAMP to log.timestamp,
                                    ForwardWorker.KEY_CARRIER to log.carrierName,
                                    ForwardWorker.KEY_SUB_ID to -1
                                )
                                val workRequest = OneTimeWorkRequestBuilder<ForwardWorker>()
                                    .setInputData(workData)
                                    .build()
                                WorkManager.getInstance(context).enqueue(workRequest)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LogItemCard(
    log: SmsLogEntity,
    onRetry: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: SIM Badge + Timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = if (log.simSlotIndex == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "SIM ${log.simSlotIndex + 1} (${log.carrierName})",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = dateFormat.format(Date(log.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sender
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "From: ${log.sender}",
                    style = MaterialTheme.typography.titleSmall
                )
                if (log.isSensitive) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = "OTP / Code",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Message Body (Preview or Full)
            Text(
                text = if (expanded) log.messageBody else log.messageBody.take(80) + if (log.messageBody.length > 80) "..." else "",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Destination Status Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusBadge(target = "Telegram", status = log.telegramStatus)
                StatusBadge(target = "WhatsApp", status = log.whatsappStatus)
            }

            // Expanded details & retry action
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    log.errorMessage?.let { error ->
                        Text(
                            text = "Error: $error",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (log.telegramStatus == "FAILED" || log.whatsappStatus == "FAILED") {
                        Button(
                            onClick = onRetry,
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Retry Forwarding")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBadge(target: String, status: String) {
    val (color, text) = when {
        status == "SUCCESS" -> GreenSuccess to "$target: ✓ Sent"
        status == "FAILED" -> RedError to "$target: ✗ Failed"
        status.startsWith("SKIPPED") -> OrangeWarning to "$target: ⊘ Skipped"
        status == "PENDING" -> MaterialTheme.colorScheme.primary to "$target: ⏳ Sending"
        status == "DRAFT_CREATED" -> MaterialTheme.colorScheme.secondary to "$target: 📋 Draft"
        else -> MaterialTheme.colorScheme.outline to "$target: Off"
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
