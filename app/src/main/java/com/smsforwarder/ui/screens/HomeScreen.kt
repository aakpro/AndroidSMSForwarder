package com.smsforwarder.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.smsforwarder.R
import com.smsforwarder.data.local.AppDatabase
import com.smsforwarder.data.model.SimCardInfo
import com.smsforwarder.data.model.SimFilterOption
import com.smsforwarder.data.preferences.AppPreferences
import com.smsforwarder.service.SmsForwarderService
import com.smsforwarder.ui.components.PermissionBanner
import com.smsforwarder.ui.components.SimCardSelector
import com.smsforwarder.ui.components.StatusCard
import com.smsforwarder.util.NetworkUtil
import com.smsforwarder.util.PermissionHelper
import com.smsforwarder.util.SimUtil
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    appPreferences: AppPreferences,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val isServiceEnabled by appPreferences.isServiceEnabled.collectAsState(initial = false)
    val simFilter by appPreferences.simFilter.collectAsState(initial = SimFilterOption.ALL)
    val isTelegramEnabled by appPreferences.isTelegramEnabled.collectAsState(initial = false)
    val isWhatsAppEnabled by appPreferences.isWhatsAppEnabled.collectAsState(initial = false)
    val isDiscordEnabled by appPreferences.isDiscordEnabled.collectAsState(initial = false)
    val isWebhookEnabled by appPreferences.isGenericWebhookEnabled.collectAsState(initial = false)
    val isHeartbeatEnabled by appPreferences.isHeartbeatEnabled.collectAsState(initial = false)

    var activeSims by remember { mutableStateOf<List<SimCardInfo>>(emptyList()) }
    var missingPermissions by remember { mutableStateOf<List<String>>(emptyList()) }
    var batteryInfo by remember { mutableStateOf(NetworkUtil.getBatteryInfo(context)) }
    var networkType by remember { mutableStateOf(NetworkUtil.getNetworkType(context)) }

    val database = remember { AppDatabase.getInstance(context) }
    val totalCount by database.smsLogDao().getTotalCount().collectAsState(initial = 0)
    val successCount by database.smsLogDao().getSuccessCount().collectAsState(initial = 0)

    LaunchedEffect(Unit) {
        missingPermissions = PermissionHelper.getMissingPermissions(context)
        if (missingPermissions.isEmpty()) {
            activeSims = SimUtil(context).getActiveSimCards()
        }
        batteryInfo = NetworkUtil.getBatteryInfo(context)
        networkType = NetworkUtil.getNetworkType(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.home_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Permission Warning Banner
            if (missingPermissions.isNotEmpty()) {
                PermissionBanner(
                    missingPermissions = missingPermissions,
                    onRequestPermissions = onRequestPermissions
                )
            }

            // Master Service Switch Card
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = if (isServiceEnabled)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isServiceEnabled) Icons.Default.PlayCircle else Icons.Default.PauseCircle,
                        contentDescription = null,
                        tint = if (isServiceEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isServiceEnabled) stringResource(R.string.service_running) else stringResource(R.string.service_stopped),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (isServiceEnabled) stringResource(R.string.service_status_desc_running) else stringResource(R.string.service_status_desc_stopped),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = isServiceEnabled,
                        onCheckedChange = { enable ->
                            coroutineScope.launch {
                                appPreferences.setServiceEnabled(enable)
                                if (enable) {
                                    SmsForwarderService.start(context)
                                } else {
                                    SmsForwarderService.stop(context)
                                }
                            }
                        }
                    )
                }
            }

            // Relay Device Health Quick Info
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (batteryInfo.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.Battery5Bar,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "${batteryInfo.percentage}% (${if (batteryInfo.isCharging) "Charging" else "Battery"})",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (networkType == "Wi-Fi") Icons.Default.Wifi else Icons.Default.NetworkCell,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(text = networkType, style = MaterialTheme.typography.labelMedium)
                    }

                    if (isHeartbeatEnabled) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(text = "Heartbeat ON", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // Dual-SIM Selector
            SimCardSelector(
                activeSims = activeSims,
                selectedFilter = simFilter,
                onFilterSelected = { newFilter ->
                    coroutineScope.launch {
                        appPreferences.setSimFilter(newFilter)
                    }
                }
            )

            // Destination Status Cards
            Text(text = stringResource(R.string.stat_channels), style = MaterialTheme.typography.titleMedium)

            StatusCard(
                title = stringResource(R.string.channel_telegram),
                subtitle = if (isTelegramEnabled) "Enabled (24/7 Bot API)" else "Disabled (Configure in Settings)",
                isActive = isTelegramEnabled,
                icon = Icons.AutoMirrored.Filled.Send,
                trailingContent = {
                    Switch(
                        checked = isTelegramEnabled,
                        onCheckedChange = { checked ->
                            coroutineScope.launch { appPreferences.setTelegramEnabled(checked) }
                        }
                    )
                }
            )

            StatusCard(
                title = stringResource(R.string.channel_whatsapp),
                subtitle = if (isWhatsAppEnabled) "Enabled" else "Disabled (Configure in Settings)",
                isActive = isWhatsAppEnabled,
                icon = Icons.AutoMirrored.Filled.Chat,
                trailingContent = {
                    Switch(
                        checked = isWhatsAppEnabled,
                        onCheckedChange = { checked ->
                            coroutineScope.launch { appPreferences.setWhatsAppEnabled(checked) }
                        }
                    )
                }
            )

            StatusCard(
                title = stringResource(R.string.channel_discord),
                subtitle = if (isDiscordEnabled) "Enabled (Rich Embeds)" else "Disabled (Configure in Settings)",
                isActive = isDiscordEnabled,
                icon = Icons.Default.Share,
                trailingContent = {
                    Switch(
                        checked = isDiscordEnabled,
                        onCheckedChange = { checked ->
                            coroutineScope.launch { appPreferences.setDiscordEnabled(checked) }
                        }
                    )
                }
            )

            StatusCard(
                title = stringResource(R.string.channel_generic_webhook),
                subtitle = if (isWebhookEnabled) "Enabled (Custom JSON POST)" else "Disabled (Configure in Settings)",
                isActive = isWebhookEnabled,
                icon = Icons.Default.Http,
                trailingContent = {
                    Switch(
                        checked = isWebhookEnabled,
                        onCheckedChange = { checked ->
                            coroutineScope.launch { appPreferences.setGenericWebhookEnabled(checked) }
                        }
                    )
                }
            )

            // Metrics Summary
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "$totalCount", style = MaterialTheme.typography.headlineMedium)
                        Text(text = stringResource(R.string.stat_total), style = MaterialTheme.typography.labelSmall)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$successCount",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(text = stringResource(R.string.stat_success), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
