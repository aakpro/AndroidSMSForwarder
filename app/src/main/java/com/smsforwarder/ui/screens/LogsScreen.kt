package com.smsforwarder.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.smsforwarder.R
import com.smsforwarder.data.local.AppDatabase
import com.smsforwarder.data.local.DiagnosticLogEntity
import com.smsforwarder.data.local.SmsLogEntity
import com.smsforwarder.ui.theme.GreenSuccess
import com.smsforwarder.ui.theme.OrangeWarning
import com.smsforwarder.ui.theme.RedError
import com.smsforwarder.util.LogExporter
import com.smsforwarder.worker.ForwardWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var currentFilter by remember { mutableStateOf(LogFilter.ALL) }
    var showClearDialog by remember { mutableStateOf(false) }

    // SMS Logs Flow
    val smsLogsFlow = remember(currentFilter, searchQuery) {
        if (searchQuery.isNotBlank()) {
            database.smsLogDao().searchLogs(searchQuery.trim(), 200)
        } else {
            when (currentFilter) {
                LogFilter.ALL -> database.smsLogDao().getRecentLogs(200)
                LogFilter.SIM_1 -> database.smsLogDao().getLogsBySimSlot(0, 200)
                LogFilter.SIM_2 -> database.smsLogDao().getLogsBySimSlot(1, 200)
                LogFilter.FAILED -> database.smsLogDao().getFailedLogs()
            }
        }
    }
    val smsLogs by smsLogsFlow.collectAsState(initial = emptyList())

    // Diagnostics Flow
    val diagnosticsFlow = remember { database.diagnosticLogDao().getRecentLogs(200) }
    val diagnosticLogs by diagnosticsFlow.collectAsState(initial = emptyList())

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.clear_logs)) },
            text = { Text(stringResource(R.string.clear_logs_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    coroutineScope.launch {
                        if (selectedTabIndex == 0) {
                            database.smsLogDao().clearAll()
                        } else {
                            database.diagnosticLogDao().clearAll()
                        }
                    }
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.logs_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    // Export CSV
                    IconButton(onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            if (selectedTabIndex == 0) {
                                val allLogs = database.smsLogDao().getAllLogsSync()
                                val uri = LogExporter.exportSmsLogsToCsv(context, allLogs)
                                if (uri != null) {
                                    withContext(Dispatchers.Main) {
                                        LogExporter.shareExportedFile(context, uri, "SMS Forwarder Logs")
                                    }
                                }
                            } else {
                                val uri = LogExporter.exportDiagnosticsToCsv(context, diagnosticLogs)
                                if (uri != null) {
                                    withContext(Dispatchers.Main) {
                                        LogExporter.shareExportedFile(context, uri, "System Diagnostic Logs")
                                    }
                                }
                            }
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.export_csv))
                    }

                    // Clear Logs
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.clear_logs))
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
            // Tabs: SMS Logs vs System Diagnostics
            TabRow(selectedTabIndex = selectedTabIndex) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text(stringResource(R.string.tab_sms_logs)) },
                    icon = { Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null) }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text(stringResource(R.string.tab_diagnostics)) },
                    icon = { Icon(Icons.Default.HealthAndSafety, contentDescription = null) }
                )
            }

            if (selectedTabIndex == 0) {
                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.search_logs_hint)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = null)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true
                )

                // Filter Chips (when not searching)
                if (searchQuery.isEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = currentFilter == LogFilter.ALL,
                            onClick = { currentFilter = LogFilter.ALL },
                            label = { Text(stringResource(R.string.filter_all)) }
                        )
                        FilterChip(
                            selected = currentFilter == LogFilter.SIM_1,
                            onClick = { currentFilter = LogFilter.SIM_1 },
                            label = { Text(stringResource(R.string.filter_sim1)) }
                        )
                        FilterChip(
                            selected = currentFilter == LogFilter.SIM_2,
                            onClick = { currentFilter = LogFilter.SIM_2 },
                            label = { Text(stringResource(R.string.filter_sim2)) }
                        )
                        FilterChip(
                            selected = currentFilter == LogFilter.FAILED,
                            onClick = { currentFilter = LogFilter.FAILED },
                            label = { Text(stringResource(R.string.filter_failed)) }
                        )
                    }
                }

                if (smsLogs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.no_logs_found),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(smsLogs, key = { it.id }) { log ->
                            SmsLogCard(
                                log = log,
                                onRetry = {
                                    val retryWork = OneTimeWorkRequestBuilder<ForwardWorker>()
                                        .setInputData(
                                            workDataOf(
                                                ForwardWorker.KEY_LOG_ID to log.id,
                                                ForwardWorker.KEY_SENDER to log.sender,
                                                ForwardWorker.KEY_BODY to log.messageBody,
                                                ForwardWorker.KEY_SIM_SLOT to log.simSlotIndex,
                                                ForwardWorker.KEY_CARRIER to log.carrierName,
                                                ForwardWorker.KEY_TIMESTAMP to log.timestamp
                                            )
                                        )
                                        .build()
                                    WorkManager.getInstance(context).enqueue(retryWork)
                                }
                            )
                        }
                    }
                }
            } else {
                // Diagnostics List
                if (diagnosticLogs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.no_diagnostics_found),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(diagnosticLogs, key = { it.id }) { diag ->
                            DiagnosticLogCard(diag)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SmsLogCard(
    log: SmsLogEntity,
    onRetry: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val dateStr = remember(log.timestamp) {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))
    }

    val isFailed = log.telegramStatus == "FAILED" || log.whatsappStatus == "FAILED" ||
            log.discordStatus == "FAILED" || log.webhookStatus == "FAILED" || log.emailStatus == "FAILED"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded },
        colors = CardDefaults.cardColors(
            containerColor = if (isFailed) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text("SIM ${log.simSlotIndex + 1}") },
                        modifier = Modifier.height(26.dp)
                    )
                    Text(
                        text = log.sender,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // Message preview
            Text(
                text = log.messageBody,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (isExpanded) Int.MAX_VALUE else 2
            )

            // Status Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (log.telegramStatus != "DISABLED") {
                    StatusBadge("TG: ${log.telegramStatus}", log.telegramStatus)
                }
                if (log.whatsappStatus != "DISABLED") {
                    StatusBadge("WA: ${log.whatsappStatus}", log.whatsappStatus)
                }
                if (log.discordStatus != "DISABLED") {
                    StatusBadge("DC: ${log.discordStatus}", log.discordStatus)
                }
                if (log.webhookStatus != "DISABLED") {
                    StatusBadge("WH: ${log.webhookStatus}", log.webhookStatus)
                }
                if (log.emailStatus != "DISABLED") {
                    StatusBadge("EM: ${log.emailStatus}", log.emailStatus)
                }
            }

            // Expanded Telemetry & Actions
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Carrier: ${log.carrierName}", style = MaterialTheme.typography.labelSmall)
                        if (log.batteryLevel >= 0) {
                            Text("Battery: ${log.batteryLevel}%", style = MaterialTheme.typography.labelSmall)
                        }
                        if (log.networkType != "UNKNOWN") {
                            Text("Net: ${log.networkType}", style = MaterialTheme.typography.labelSmall)
                        }
                        if (log.durationMs > 0) {
                            Text("Latency: ${log.durationMs}ms", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    if (!log.errorMessage.isNullOrBlank()) {
                        Text(
                            text = "Error: ${log.errorMessage}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    if (isFailed) {
                        Button(
                            onClick = onRetry,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DiagnosticLogCard(diag: DiagnosticLogEntity) {
    val dateStr = remember(diag.timestamp) {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(diag.timestamp))
    }

    val isError = diag.eventType == "ERROR" || diag.eventType == "BATTERY_LOW"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SuggestionChip(
                    onClick = {},
                    label = { Text(diag.eventType) },
                    modifier = Modifier.height(26.dp)
                )
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Text(
                text = diag.message,
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (diag.batteryLevel >= 0) {
                    Text(
                        "Battery: ${diag.batteryLevel}% (${if (diag.isCharging) "Charging" else "Unplugged"})",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                if (diag.networkType.isNotBlank()) {
                    Text("Network: ${diag.networkType}", style = MaterialTheme.typography.labelSmall)
                }
            }

            if (!diag.details.isNullOrBlank()) {
                Text(
                    text = diag.details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
fun StatusBadge(text: String, status: String) {
    val (bgColor, textColor) = when {
        status == "SUCCESS" -> GreenSuccess.copy(alpha = 0.15f) to GreenSuccess
        status == "FAILED" -> RedError.copy(alpha = 0.15f) to RedError
        status.startsWith("SKIPPED") -> OrangeWarning.copy(alpha = 0.15f) to OrangeWarning
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = bgColor,
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
