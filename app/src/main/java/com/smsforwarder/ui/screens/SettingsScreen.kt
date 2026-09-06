package com.smsforwarder.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.smsforwarder.data.preferences.AppPreferences
import com.smsforwarder.data.preferences.SecurePreferences
import com.smsforwarder.data.preferences.WhatsAppMode
import com.smsforwarder.sender.TelegramSender
import com.smsforwarder.sender.WhatsAppSender
import com.smsforwarder.ui.components.CallMeBotWarningDialog
import com.smsforwarder.util.PermissionHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    appPreferences: AppPreferences,
    securePreferences: SecurePreferences,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Telegram State
    var botToken by remember { mutableStateOf(securePreferences.telegramBotToken) }
    var chatId by remember { mutableStateOf(securePreferences.telegramChatId) }
    var isTokenVisible by remember { mutableStateOf(false) }

    // WhatsApp State
    val currentWaMode by appPreferences.whatsAppMode.collectAsState(initial = WhatsAppMode.CALLMEBOT)
    var cmbApiKey by remember { mutableStateOf(securePreferences.callMeBotApiKey) }
    var cmbPhone by remember { mutableStateOf(securePreferences.callMeBotPhoneNumber) }
    var isCmbKeyVisible by remember { mutableStateOf(false) }

    var webhookUrl by remember { mutableStateOf(securePreferences.customWebhookUrl) }
    var webhookAuth by remember { mutableStateOf(securePreferences.customWebhookAuthHeader) }

    var draftPhone by remember { mutableStateOf(securePreferences.whatsappDraftPhoneNumber) }

    val excludeSensitive by appPreferences.excludeSensitiveFromCallMeBot.collectAsState(initial = true)
    val redactOtp by appPreferences.redactOtpInCallMeBot.collectAsState(initial = false)
    val callMeBotWarningAccepted by appPreferences.callMeBotWarningAccepted.collectAsState(initial = false)
    var showCallMeBotWarning by remember { mutableStateOf(false) }

    // Template State
    val template by appPreferences.messageTemplate.collectAsState(initial = AppPreferences.DEFAULT_TEMPLATE)
    var editableTemplate by remember(template) { mutableStateOf(template) }

    // Dialog / Test Status
    var testResultDialogText by remember { mutableStateOf<String?>(null) }
    var isTestingConnection by remember { mutableStateOf(false) }

    val telegramSender = remember { TelegramSender(securePreferences) }
    val whatsAppSender = remember { WhatsAppSender(context, securePreferences) }

    if (showCallMeBotWarning) {
        CallMeBotWarningDialog(
            onDismiss = { showCallMeBotWarning = false },
            onConfirm = {
                showCallMeBotWarning = false
                coroutineScope.launch {
                    appPreferences.setCallMeBotWarningAccepted(true)
                }
            }
        )
    }

    testResultDialogText?.let { message ->
        AlertDialog(
            onDismissRequest = { testResultDialogText = null },
            title = { Text(text = "Connection Test") },
            text = { Text(text = message) },
            confirmButton = {
                TextButton(onClick = { testResultDialogText = null }) {
                    Text(text = "OK")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Settings") },
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ==========================================
            // Section 1: Telegram Bot Configuration
            // ==========================================
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Send, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Telegram Bot (Encrypted Keystore)", style = MaterialTheme.typography.titleMedium)
                    }

                    Text(
                        text = "Forward SMS directly to your Telegram chat or channel via Bot API. 100% background automated.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    OutlinedTextField(
                        value = botToken,
                        onValueChange = {
                            botToken = it
                            securePreferences.telegramBotToken = it
                        },
                        label = { Text("Bot Token (from @BotFather)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (isTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isTokenVisible = !isTokenVisible }) {
                                Icon(
                                    imageVector = if (isTokenVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null
                                )
                            }
                        }
                    )

                    OutlinedTextField(
                        value = chatId,
                        onValueChange = {
                            chatId = it
                            securePreferences.telegramChatId = it
                        },
                        label = { Text("Chat ID (e.g. 12345678 or -100xxx)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            isTestingConnection = true
                            coroutineScope.launch {
                                val result = telegramSender.testConnection(botToken, chatId)
                                isTestingConnection = false
                                testResultDialogText = if (result.isSuccess) {
                                    "✅ Telegram connection succeeded! Check your Telegram chat for the test message."
                                } else {
                                    "❌ Telegram test failed:\n${result.exceptionOrNull()?.message}"
                                }
                            }
                        },
                        enabled = botToken.isNotBlank() && chatId.isNotBlank() && !isTestingConnection
                    ) {
                        if (isTestingConnection) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Test Telegram Connection")
                    }
                }
            }

            // ==========================================
            // Section 2: WhatsApp Configuration
            // ==========================================
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Chat, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "WhatsApp Configuration", style = MaterialTheme.typography.titleMedium)
                    }

                    Text(text = "Delivery Mode:", style = MaterialTheme.typography.labelMedium)

                    WhatsAppMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (mode == currentWaMode),
                                onClick = {
                                    coroutineScope.launch {
                                        appPreferences.setWhatsAppMode(mode)
                                        if (mode == WhatsAppMode.CALLMEBOT && !callMeBotWarningAccepted) {
                                            showCallMeBotWarning = true
                                        }
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(text = mode.title, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = when (mode) {
                                        WhatsAppMode.CALLMEBOT -> "Free API via WhatsApp relay. Throttled & requires API key."
                                        WhatsAppMode.WEBHOOK -> "POST payload to your custom server / Evolution API / n8n."
                                        WhatsAppMode.INTENT_DRAFT -> "Pre-fills message and opens WhatsApp. Requires manual tap to send."
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    // Mode-Specific Fields
                    when (currentWaMode) {
                        WhatsAppMode.CALLMEBOT -> {
                            OutlinedTextField(
                                value = cmbPhone,
                                onValueChange = {
                                    cmbPhone = it
                                    securePreferences.callMeBotPhoneNumber = it
                                },
                                label = { Text("WhatsApp Phone (with country code, e.g. +123456789)") },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = cmbApiKey,
                                onValueChange = {
                                    cmbApiKey = it
                                    securePreferences.callMeBotApiKey = it
                                },
                                label = { Text("CallMeBot API Key") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                visualTransformation = if (isCmbKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { isCmbKeyVisible = !isCmbKeyVisible }) {
                                        Icon(
                                            imageVector = if (isCmbKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = null
                                        )
                                    }
                                }
                            )

                            // Privacy Guard Toggle
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(text = "Privacy Guard (CallMeBot)", style = MaterialTheme.typography.titleSmall)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Protects against sending OTPs/bank alerts through third-party relays.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = "Exclude OTPs & Bank Codes", style = MaterialTheme.typography.bodyMedium)
                                        Switch(
                                            checked = excludeSensitive,
                                            onCheckedChange = { coroutineScope.launch { appPreferences.setExcludeSensitiveFromCallMeBot(it) } }
                                        )
                                    }

                                    if (!excludeSensitive) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(text = "Redact Code Digits", style = MaterialTheme.typography.bodyMedium)
                                            Switch(
                                                checked = redactOtp,
                                                onCheckedChange = { coroutineScope.launch { appPreferences.setRedactOtpInCallMeBot(it) } }
                                            )
                                        }
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    isTestingConnection = true
                                    coroutineScope.launch {
                                        val result = whatsAppSender.testCallMeBot(cmbPhone, cmbApiKey)
                                        isTestingConnection = false
                                        testResultDialogText = if (result.isSuccess) {
                                            "✅ CallMeBot sent test message to your WhatsApp!"
                                        } else {
                                            "❌ CallMeBot failed:\n${result.exceptionOrNull()?.message}"
                                        }
                                    }
                                },
                                enabled = cmbPhone.isNotBlank() && cmbApiKey.isNotBlank() && !isTestingConnection
                            ) {
                                Text("Test CallMeBot")
                            }
                        }

                        WhatsAppMode.WEBHOOK -> {
                            OutlinedTextField(
                                value = webhookUrl,
                                onValueChange = {
                                    webhookUrl = it
                                    securePreferences.customWebhookUrl = it
                                },
                                label = { Text("Webhook HTTPS URL") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = webhookAuth,
                                onValueChange = {
                                    webhookAuth = it
                                    securePreferences.customWebhookAuthHeader = it
                                },
                                label = { Text("Optional Authorization Header (Bearer xxx)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            Button(
                                onClick = {
                                    isTestingConnection = true
                                    coroutineScope.launch {
                                        val result = whatsAppSender.testWebhook(webhookUrl, webhookAuth)
                                        isTestingConnection = false
                                        testResultDialogText = if (result.isSuccess) {
                                            "✅ Webhook received test payload successfully!"
                                        } else {
                                            "❌ Webhook failed:\n${result.exceptionOrNull()?.message}"
                                        }
                                    }
                                },
                                enabled = webhookUrl.isNotBlank() && !isTestingConnection
                            ) {
                                Text("Test Webhook")
                            }
                        }

                        WhatsAppMode.INTENT_DRAFT -> {
                            OutlinedTextField(
                                value = draftPhone,
                                onValueChange = {
                                    draftPhone = it
                                    securePreferences.whatsappDraftPhoneNumber = it
                                },
                                label = { Text("Target Phone (Optional: leaves blank to choose in WhatsApp)") },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true
                            )
                        }
                    }
                }
            }

            // ==========================================
            // Section 3: Message Template
            // ==========================================
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = "Message Template", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Placeholders: {sim}, {carrier}, {sender}, {message}, {time}, {date}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = editableTemplate,
                        onValueChange = {
                            editableTemplate = it
                            coroutineScope.launch { appPreferences.setMessageTemplate(it) }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        maxLines = 8
                    )

                    OutlinedButton(
                        onClick = {
                            editableTemplate = AppPreferences.DEFAULT_TEMPLATE
                            coroutineScope.launch { appPreferences.setMessageTemplate(AppPreferences.DEFAULT_TEMPLATE) }
                        }
                    ) {
                        Text("Reset to Default")
                    }
                }
            }

            // ==========================================
            // Section 4: Battery Optimization Exemption
            // ==========================================
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Battery Optimization", style = MaterialTheme.typography.titleMedium)
                    }

                    Text(
                        text = "To ensure background SMS forwarding is not terminated by Android or OEM battery savers (Xiaomi, Samsung, Huawei), disable battery optimization.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    val isIgnoring = PermissionHelper.isIgnoringBatteryOptimizations(context)
                    Text(
                        text = if (isIgnoring) "Status: Exemption Active (Recommended)" else "Status: Optimization Enabled (May be killed)",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isIgnoring) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )

                    if (!isIgnoring) {
                        OutlinedButton(
                            onClick = {
                                try {
                                    context.startActivity(PermissionHelper.createIgnoreBatteryOptimizationsIntent(context))
                                } catch (e: Exception) {
                                    context.startActivity(PermissionHelper.createAppSettingsIntent(context))
                                }
                            }
                        ) {
                            Text("Request Exemption")
                        }
                    }
                }
            }
        }
    }
}
