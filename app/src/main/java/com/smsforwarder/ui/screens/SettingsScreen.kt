package com.smsforwarder.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.smsforwarder.R
import com.smsforwarder.data.preferences.AppPreferences
import com.smsforwarder.data.preferences.SecurePreferences
import com.smsforwarder.data.preferences.WhatsAppMode
import com.smsforwarder.sender.DiscordSender
import com.smsforwarder.sender.GenericWebhookSender
import com.smsforwarder.sender.TelegramSender
import com.smsforwarder.sender.WhatsAppSender
import com.smsforwarder.ui.components.BatteryOptimizationCard
import com.smsforwarder.ui.components.CallMeBotWarningDialog
import com.smsforwarder.worker.HeartbeatWorker
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    appPreferences: AppPreferences,
    securePreferences: SecurePreferences,
    onRequestPermissions: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Language
    val currentLang by appPreferences.appLanguage.collectAsState(initial = "system")

    // Telegram State
    val isTelegramEnabled by appPreferences.isTelegramEnabled.collectAsState(initial = false)
    var botToken by remember { mutableStateOf(securePreferences.telegramBotToken) }
    var chatId by remember { mutableStateOf(securePreferences.telegramChatId) }
    var isTokenVisible by remember { mutableStateOf(false) }

    // WhatsApp State
    val isWhatsAppEnabled by appPreferences.isWhatsAppEnabled.collectAsState(initial = false)
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

    // Discord State
    val isDiscordEnabled by appPreferences.isDiscordEnabled.collectAsState(initial = false)
    var discordWebhookUrl by remember { mutableStateOf(securePreferences.discordWebhookUrl) }

    // Generic Webhook State
    val isGenericWebhookEnabled by appPreferences.isGenericWebhookEnabled.collectAsState(initial = false)
    var genericWebhookUrl by remember { mutableStateOf(securePreferences.genericWebhookUrl) }
    var genericWebhookAuth by remember { mutableStateOf(securePreferences.genericWebhookAuthHeader) }

    // Email (SMTP) State
    val isEmailEnabled by appPreferences.isEmailEnabled.collectAsState(initial = false)
    val smtpHost by appPreferences.smtpHost.collectAsState(initial = "smtp.gmail.com")
    var emailHost by remember(smtpHost) { mutableStateOf(smtpHost) }
    val smtpPort by appPreferences.smtpPort.collectAsState(initial = 587)
    var emailPort by remember(smtpPort) { mutableStateOf(smtpPort.toString()) }
    val smtpEncryption by appPreferences.smtpEncryption.collectAsState(initial = com.smsforwarder.data.preferences.SmtpEncryption.STARTTLS)
    var emailUsername by remember { mutableStateOf(securePreferences.smtpUsername) }
    var emailPassword by remember { mutableStateOf(securePreferences.smtpPassword) }
    var isEmailPasswordVisible by remember { mutableStateOf(false) }
    val emailFrom by appPreferences.emailFrom.collectAsState(initial = "")
    var emailFromField by remember(emailFrom) { mutableStateOf(emailFrom) }
    val emailRecipients by appPreferences.emailRecipients.collectAsState(initial = "")
    var emailRecipientsField by remember(emailRecipients) { mutableStateOf(emailRecipients) }
    val emailSubjectTemplate by appPreferences.emailSubjectTemplate.collectAsState(initial = AppPreferences.DEFAULT_EMAIL_SUBJECT)
    var emailSubjectField by remember(emailSubjectTemplate) { mutableStateOf(emailSubjectTemplate) }

    // Reliability & Health State
    val isHeartbeatEnabled by appPreferences.isHeartbeatEnabled.collectAsState(initial = false)
    val heartbeatInterval by appPreferences.heartbeatIntervalHours.collectAsState(initial = 12)
    val isLowBatteryAlertEnabled by appPreferences.isLowBatteryAlertEnabled.collectAsState(initial = true)

    // Template State
    val template by appPreferences.messageTemplate.collectAsState(initial = AppPreferences.DEFAULT_TEMPLATE)
    var editableTemplate by remember(template) { mutableStateOf(template) }

    // Dialog / Test Status
    var testResultDialogText by remember { mutableStateOf<String?>(null) }
    var isTestingConnection by remember { mutableStateOf(false) }

    val telegramSender = remember { TelegramSender(securePreferences) }
    val whatsAppSender = remember { WhatsAppSender(context, securePreferences) }
    val discordSender = remember { DiscordSender(securePreferences) }
    val genericWebhookSender = remember { GenericWebhookSender(securePreferences) }
    val emailSender = remember { com.smsforwarder.sender.EmailSender(context, appPreferences, securePreferences) }
    val database = remember { com.smsforwarder.data.local.AppDatabase.getInstance(context) }

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
            title = { Text(text = stringResource(R.string.test_connection)) },
            text = { Text(text = message) },
            confirmButton = {
                TextButton(onClick = { testResultDialogText = null }) {
                    Text(text = stringResource(R.string.ok))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.settings_title)) },
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
            // ==========================================
            // Section 0: General / Language
            // ==========================================
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.app_language), style = MaterialTheme.typography.titleMedium)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = currentLang == "system",
                            onClick = { coroutineScope.launch { appPreferences.setAppLanguage("system") } },
                            label = { Text(stringResource(R.string.lang_system)) }
                        )
                        FilterChip(
                            selected = currentLang == "en",
                            onClick = { coroutineScope.launch { appPreferences.setAppLanguage("en") } },
                            label = { Text("English") }
                        )
                        FilterChip(
                            selected = currentLang == "fa",
                            onClick = { coroutineScope.launch { appPreferences.setAppLanguage("fa") } },
                            label = { Text("فارسی") }
                        )
                    }
                }
            }

            // ==========================================
            // Section 1: Telegram Bot Configuration
            // ==========================================
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(R.string.channel_telegram), style = MaterialTheme.typography.titleMedium)
                        }
                        Switch(
                            checked = isTelegramEnabled,
                            onCheckedChange = { checked ->
                                coroutineScope.launch { appPreferences.setTelegramEnabled(checked) }
                            }
                        )
                    }

                    OutlinedTextField(
                        value = botToken,
                        onValueChange = {
                            botToken = it
                            securePreferences.telegramBotToken = it
                        },
                        label = { Text(stringResource(R.string.telegram_token)) },
                        placeholder = { Text(stringResource(R.string.telegram_token_hint)) },
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
                        label = { Text(stringResource(R.string.telegram_chat_id)) },
                        placeholder = { Text(stringResource(R.string.telegram_chat_id_hint)) },
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
                                    "✅ Telegram connection succeeded!"
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
                        Text(stringResource(R.string.test_connection))
                    }
                }
            }

            // ==========================================
            // Section 2: Discord Webhook
            // ==========================================
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(R.string.channel_discord), style = MaterialTheme.typography.titleMedium)
                        }
                        Switch(
                            checked = isDiscordEnabled,
                            onCheckedChange = { checked ->
                                coroutineScope.launch { appPreferences.setDiscordEnabled(checked) }
                            }
                        )
                    }

                    OutlinedTextField(
                        value = discordWebhookUrl,
                        onValueChange = {
                            discordWebhookUrl = it
                            securePreferences.discordWebhookUrl = it
                        },
                        label = { Text(stringResource(R.string.discord_webhook_url)) },
                        placeholder = { Text(stringResource(R.string.discord_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            isTestingConnection = true
                            coroutineScope.launch {
                                val result = discordSender.testConnection(discordWebhookUrl)
                                isTestingConnection = false
                                testResultDialogText = if (result.isSuccess) {
                                    "✅ Discord connection test succeeded!"
                                } else {
                                    "❌ Discord test failed:\n${result.exceptionOrNull()?.message}"
                                }
                            }
                        },
                        enabled = discordWebhookUrl.isNotBlank() && !isTestingConnection
                    ) {
                        Text(stringResource(R.string.test_connection))
                    }
                }
            }

            // ==========================================
            // Section 3: Generic HTTP Webhook
            // ==========================================
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Http, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(R.string.channel_generic_webhook), style = MaterialTheme.typography.titleMedium)
                        }
                        Switch(
                            checked = isGenericWebhookEnabled,
                            onCheckedChange = { checked ->
                                coroutineScope.launch { appPreferences.setGenericWebhookEnabled(checked) }
                            }
                        )
                    }

                    OutlinedTextField(
                        value = genericWebhookUrl,
                        onValueChange = {
                            genericWebhookUrl = it
                            securePreferences.genericWebhookUrl = it
                        },
                        label = { Text(stringResource(R.string.webhook_url)) },
                        placeholder = { Text("https://your-server.com/webhook") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = genericWebhookAuth,
                        onValueChange = {
                            genericWebhookAuth = it
                            securePreferences.genericWebhookAuthHeader = it
                        },
                        label = { Text(stringResource(R.string.webhook_auth)) },
                        placeholder = { Text(stringResource(R.string.webhook_auth_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            isTestingConnection = true
                            coroutineScope.launch {
                                val result = genericWebhookSender.testConnection(genericWebhookUrl, genericWebhookAuth)
                                isTestingConnection = false
                                testResultDialogText = if (result.isSuccess) {
                                    "✅ Webhook received test payload successfully!"
                                } else {
                                    "❌ Webhook failed:\n${result.exceptionOrNull()?.message}"
                                }
                            }
                        },
                        enabled = genericWebhookUrl.isNotBlank() && !isTestingConnection
                    ) {
                        Text(stringResource(R.string.test_connection))
                    }
                }
            }

            // ==========================================
            // Section 4: WhatsApp Configuration
            // ==========================================
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(R.string.channel_whatsapp), style = MaterialTheme.typography.titleMedium)
                        }
                        Switch(
                            checked = isWhatsAppEnabled,
                            onCheckedChange = { checked ->
                                coroutineScope.launch { appPreferences.setWhatsAppEnabled(checked) }
                            }
                        )
                    }

                    Text(text = stringResource(R.string.whatsapp_mode), style = MaterialTheme.typography.labelMedium)

                    WhatsAppMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
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
                            Text(
                                text = when (mode) {
                                    WhatsAppMode.CALLMEBOT -> stringResource(R.string.wa_mode_callmebot)
                                    WhatsAppMode.WEBHOOK -> stringResource(R.string.wa_mode_webhook)
                                    WhatsAppMode.INTENT_DRAFT -> stringResource(R.string.wa_mode_draft)
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    HorizontalDivider()

                    when (currentWaMode) {
                        WhatsAppMode.CALLMEBOT -> {
                            OutlinedTextField(
                                value = cmbPhone,
                                onValueChange = {
                                    cmbPhone = it
                                    securePreferences.callMeBotPhoneNumber = it
                                },
                                label = { Text(stringResource(R.string.callmebot_phone)) },
                                placeholder = { Text(stringResource(R.string.callmebot_phone_hint)) },
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
                                label = { Text(stringResource(R.string.callmebot_api_key)) },
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
                        }
                        WhatsAppMode.WEBHOOK -> {
                            OutlinedTextField(
                                value = webhookUrl,
                                onValueChange = {
                                    webhookUrl = it
                                    securePreferences.customWebhookUrl = it
                                },
                                label = { Text(stringResource(R.string.webhook_url)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                        WhatsAppMode.INTENT_DRAFT -> {
                            OutlinedTextField(
                                value = draftPhone,
                                onValueChange = {
                                    draftPhone = it
                                    securePreferences.whatsappDraftPhoneNumber = it
                                },
                                label = { Text("WhatsApp Draft Phone (Optional)") },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true
                            )
                        }
                    }
                }
            }

            // ==========================================
            // Section 5: Email (SMTP) Forwarding
            // ==========================================
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(R.string.channel_email), style = MaterialTheme.typography.titleMedium)
                        }
                        Switch(
                            checked = isEmailEnabled,
                            onCheckedChange = { checked ->
                                coroutineScope.launch { appPreferences.setEmailEnabled(checked) }
                            }
                        )
                    }

                    // Quick Provider Presets
                    Text(text = "Quick Presets:", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = emailHost == "smtp.gmail.com" && emailPort == "587",
                            onClick = {
                                emailHost = "smtp.gmail.com"
                                emailPort = "587"
                                coroutineScope.launch {
                                    appPreferences.setSmtpHost("smtp.gmail.com")
                                    appPreferences.setSmtpPort(587)
                                    appPreferences.setSmtpEncryption(com.smsforwarder.data.preferences.SmtpEncryption.STARTTLS)
                                }
                            },
                            label = { Text("Gmail") }
                        )
                        FilterChip(
                            selected = emailHost == "smtp.office365.com",
                            onClick = {
                                emailHost = "smtp.office365.com"
                                emailPort = "587"
                                coroutineScope.launch {
                                    appPreferences.setSmtpHost("smtp.office365.com")
                                    appPreferences.setSmtpPort(587)
                                    appPreferences.setSmtpEncryption(com.smsforwarder.data.preferences.SmtpEncryption.STARTTLS)
                                }
                            },
                            label = { Text("Outlook") }
                        )
                        FilterChip(
                            selected = emailHost == "smtp.mail.yahoo.com",
                            onClick = {
                                emailHost = "smtp.mail.yahoo.com"
                                emailPort = "465"
                                coroutineScope.launch {
                                    appPreferences.setSmtpHost("smtp.mail.yahoo.com")
                                    appPreferences.setSmtpPort(465)
                                    appPreferences.setSmtpEncryption(com.smsforwarder.data.preferences.SmtpEncryption.SSL_TLS)
                                }
                            },
                            label = { Text("Yahoo") }
                        )
                    }

                    // Host & Port Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = emailHost,
                            onValueChange = {
                                emailHost = it
                                coroutineScope.launch { appPreferences.setSmtpHost(it) }
                            },
                            label = { Text(stringResource(R.string.smtp_host)) },
                            placeholder = { Text(stringResource(R.string.smtp_host_hint)) },
                            modifier = Modifier.weight(2f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = emailPort,
                            onValueChange = {
                                emailPort = it
                                it.toIntOrNull()?.let { p ->
                                    coroutineScope.launch { appPreferences.setSmtpPort(p) }
                                }
                            },
                            label = { Text(stringResource(R.string.smtp_port)) },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }

                    // Encryption Mode
                    Text(text = stringResource(R.string.smtp_encryption), style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        com.smsforwarder.data.preferences.SmtpEncryption.entries.forEach { enc ->
                            FilterChip(
                                selected = smtpEncryption == enc,
                                onClick = {
                                    coroutineScope.launch {
                                        appPreferences.setSmtpEncryption(enc)
                                        if (emailPort.isBlank() || emailPort == "587" || emailPort == "465" || emailPort == "25") {
                                            emailPort = enc.defaultPort.toString()
                                            appPreferences.setSmtpPort(enc.defaultPort)
                                        }
                                    }
                                },
                                label = { Text(enc.title) }
                            )
                        }
                    }

                    // Username
                    OutlinedTextField(
                        value = emailUsername,
                        onValueChange = {
                            emailUsername = it
                            securePreferences.smtpUsername = it
                        },
                        label = { Text(stringResource(R.string.smtp_username)) },
                        placeholder = { Text(stringResource(R.string.smtp_username_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true
                    )

                    // Password / App Password
                    OutlinedTextField(
                        value = emailPassword,
                        onValueChange = {
                            emailPassword = it
                            securePreferences.smtpPassword = it
                        },
                        label = { Text(stringResource(R.string.smtp_password)) },
                        placeholder = { Text(stringResource(R.string.smtp_password_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (isEmailPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isEmailPasswordVisible = !isEmailPasswordVisible }) {
                                Icon(
                                    imageVector = if (isEmailPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true
                    )

                    // Recipient(s)
                    OutlinedTextField(
                        value = emailRecipientsField,
                        onValueChange = {
                            emailRecipientsField = it
                            coroutineScope.launch { appPreferences.setEmailRecipients(it) }
                        },
                        label = { Text(stringResource(R.string.email_recipients)) },
                        placeholder = { Text(stringResource(R.string.email_recipients_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = false,
                        maxLines = 2
                    )

                    // Subject Template
                    OutlinedTextField(
                        value = emailSubjectField,
                        onValueChange = {
                            emailSubjectField = it
                            coroutineScope.launch { appPreferences.setEmailSubjectTemplate(it) }
                        },
                        label = { Text(stringResource(R.string.email_subject_template)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            isTestingConnection = true
                            coroutineScope.launch {
                                val result = emailSender.testConnection(
                                    host = emailHost,
                                    port = emailPort.toIntOrNull() ?: 587,
                                    encryption = smtpEncryption,
                                    username = emailUsername,
                                    password = emailPassword,
                                    from = emailFromField,
                                    recipientsRaw = emailRecipientsField
                                )
                                isTestingConnection = false
                                testResultDialogText = if (result.isSuccess) {
                                    "✅ ${result.getOrNull()}"
                                } else {
                                    "❌ Email Test Failed:\n${result.exceptionOrNull()?.message}"
                                }
                            }
                        },
                        enabled = emailHost.isNotBlank() && emailRecipientsField.isNotBlank() && !isTestingConnection
                    ) {
                        Text(stringResource(R.string.test_email))
                    }
                }
            }

            // ==========================================
            // Section 6: 24/7 Relay Reliability & Health
            // ==========================================
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.section_reliability), style = MaterialTheme.typography.titleMedium)
                    }

                    // Heartbeat Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = stringResource(R.string.heartbeat_title), style = MaterialTheme.typography.bodyMedium)
                            Text(text = stringResource(R.string.heartbeat_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Switch(
                            checked = isHeartbeatEnabled,
                            onCheckedChange = { enabled ->
                                coroutineScope.launch {
                                    appPreferences.setHeartbeatEnabled(enabled)
                                    if (enabled) {
                                        HeartbeatWorker.schedule(context, heartbeatInterval)
                                    } else {
                                        HeartbeatWorker.cancel(context)
                                    }
                                }
                            }
                        )
                    }

                    if (isHeartbeatEnabled) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Interval:", style = MaterialTheme.typography.labelMedium)
                            listOf(1, 6, 12, 24).forEach { hours ->
                                FilterChip(
                                    selected = heartbeatInterval == hours,
                                    onClick = {
                                        coroutineScope.launch {
                                            appPreferences.setHeartbeatIntervalHours(hours)
                                            HeartbeatWorker.schedule(context, hours)
                                        }
                                    },
                                    label = { Text("${hours}h") }
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    // Low Battery Alert Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = stringResource(R.string.battery_alert_title), style = MaterialTheme.typography.bodyMedium)
                            Text(text = stringResource(R.string.battery_alert_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Switch(
                            checked = isLowBatteryAlertEnabled,
                            onCheckedChange = { enabled ->
                                coroutineScope.launch { appPreferences.setLowBatteryAlertEnabled(enabled) }
                            }
                        )
                    }

                    HorizontalDivider()

                    // Battery Optimization Helper Card
                    BatteryOptimizationCard()
                }
            }

            // ==========================================
            // Section 7: Smart Keyword & Sender Filtering
            // ==========================================
            com.smsforwarder.ui.components.SmartFilterCard(
                filterRuleDao = database.filterRuleDao(),
                coroutineScope = coroutineScope
            )

            // ==========================================
            // Section 8: SMS Auto-Reply & Response
            // ==========================================
            com.smsforwarder.ui.components.AutoReplyCard(
                appPreferences = appPreferences,
                coroutineScope = coroutineScope,
                onRequestSendSmsPermission = onRequestPermissions
            )

            // ==========================================
            // Section 9: Send & Receive on PC
            // ==========================================
            com.smsforwarder.ui.components.PcConnectCard(
                appPreferences = appPreferences,
                coroutineScope = coroutineScope
            )

            // ==========================================
            // Section 10: Message Template
            // ==========================================
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = stringResource(R.string.section_template), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = stringResource(R.string.template_desc),
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
                            .height(130.dp),
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
        }
    }
}
