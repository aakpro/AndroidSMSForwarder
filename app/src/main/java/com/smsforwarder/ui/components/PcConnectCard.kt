package com.smsforwarder.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.smsforwarder.R
import com.smsforwarder.data.preferences.AppPreferences
import com.smsforwarder.server.PcServerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun PcConnectCard(
    appPreferences: AppPreferences,
    coroutineScope: CoroutineScope,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pcManager = remember { PcServerManager.getInstance(context) }

    val isEnabled by appPreferences.isPcServerEnabled.collectAsState(initial = false)
    val port by appPreferences.pcServerPort.collectAsState(initial = 8080)
    val pin by appPreferences.pcServerPin.collectAsState(initial = "1234")
    val requireAuth by appPreferences.isPcServerRequireAuth.collectAsState(initial = true)

    val isRunning by pcManager.isServerRunning.collectAsState()
    val serverUrl by pcManager.serverUrl.collectAsState()

    var editablePin by remember(pin) { mutableStateOf(pin) }
    var editablePort by remember(port) { mutableStateOf(port.toString()) }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Computer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.pc_connect_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { checked ->
                        coroutineScope.launch {
                            appPreferences.setPcServerEnabled(checked)
                            if (checked) {
                                pcManager.startServer(port, pin, requireAuth)
                            } else {
                                pcManager.stopServer()
                            }
                        }
                    }
                )
            }

            Text(
                text = stringResource(R.string.pc_connect_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            AnimatedVisibility(visible = isEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Running URL Banner
                    Surface(
                        color = if (isRunning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isRunning) Icons.Default.CheckCircle else Icons.Default.PauseCircle,
                                        contentDescription = null,
                                        tint = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isRunning) stringResource(R.string.pc_server_running) else stringResource(R.string.pc_server_stopped),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                val displayUrl = serverUrl ?: pcManager.getLocalUrl(port)
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("PC Web URL", displayUrl)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, context.getString(R.string.pc_server_copied), Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "Copy URL",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            val displayUrl = serverUrl ?: pcManager.getLocalUrl(port)
                            Text(
                                text = displayUrl,
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

                    // Wi-Fi Guidance Info
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Wifi,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.pc_server_wifi_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Security & Port Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = editablePin,
                            onValueChange = {
                                editablePin = it
                                coroutineScope.launch {
                                    appPreferences.setPcServerPin(it)
                                    if (isRunning) pcManager.startServer(port, it, requireAuth)
                                }
                            },
                            label = { Text(stringResource(R.string.pc_server_pin)) },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = editablePort,
                            onValueChange = {
                                editablePort = it
                                val p = it.toIntOrNull()
                                if (p != null && p in 1024..65535) {
                                    coroutineScope.launch {
                                        appPreferences.setPcServerPort(p)
                                    }
                                }
                            },
                            label = { Text(stringResource(R.string.pc_server_port)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(0.7f)
                        )
                    }

                    // Require PIN Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.pc_server_require_auth),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = requireAuth,
                            onCheckedChange = { auth ->
                                coroutineScope.launch {
                                    appPreferences.setPcServerRequireAuth(auth)
                                    if (isRunning) pcManager.startServer(port, pin, auth)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
