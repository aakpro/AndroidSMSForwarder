package com.smsforwarder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.smsforwarder.data.preferences.AppPreferences
import com.smsforwarder.data.preferences.SecurePreferences
import com.smsforwarder.ui.navigation.AppNavigation
import com.smsforwarder.ui.theme.SmsForwarderTheme
import com.smsforwarder.util.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var appPreferences: AppPreferences
    private lateinit var securePreferences: SecurePreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        appPreferences = AppPreferences(applicationContext)
        securePreferences = SecurePreferences(applicationContext)

        handleIntentExtras(intent)

        setContent {
            val appLanguage by appPreferences.appLanguage.collectAsState(initial = "system")
            val layoutDirection = remember(appLanguage) {
                com.smsforwarder.util.LocaleHelper.getLayoutDirection(appLanguage)
            }
            val locale = remember(appLanguage) {
                com.smsforwarder.util.LocaleHelper.getLocale(appLanguage)
            }
            val currentConfig = androidx.compose.ui.platform.LocalConfiguration.current
            val localizedConfig = remember(appLanguage, currentConfig) {
                android.content.res.Configuration(currentConfig).apply {
                    setLocale(locale)
                    setLayoutDirection(locale)
                }
            }

            LaunchedEffect(appLanguage) {
                java.util.Locale.setDefault(locale)
                val config = resources.configuration
                config.setLocale(locale)
                config.setLayoutDirection(locale)
                @Suppress("DEPRECATION")
                resources.updateConfiguration(config, resources.displayMetrics)
            }

            CompositionLocalProvider(
                androidx.compose.ui.platform.LocalConfiguration provides localizedConfig,
                androidx.compose.ui.platform.LocalLayoutDirection provides layoutDirection
            ) {
                SmsForwarderTheme {
                    var permissionsGranted by remember {
                        mutableStateOf(PermissionHelper.hasAllRequiredPermissions(this@MainActivity))
                    }

                    val permissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestMultiplePermissions()
                    ) { result ->
                        permissionsGranted = PermissionHelper.hasAllRequiredPermissions(this@MainActivity)
                    }

                    LaunchedEffect(Unit) {
                        if (!permissionsGranted) {
                            permissionLauncher.launch(PermissionHelper.REQUIRED_PERMISSIONS)
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        AppNavigation(
                            appPreferences = appPreferences,
                            securePreferences = securePreferences,
                            onRequestPermissions = {
                                permissionLauncher.launch(PermissionHelper.REQUIRED_PERMISSIONS)
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleIntentExtras(intent)
    }

    private fun handleIntentExtras(intent: android.content.Intent?) {
        intent?.getStringExtra("extra_telegram_bot_token")?.let { token ->
            if (token.isNotBlank()) securePreferences.telegramBotToken = token
        }
        intent?.getStringExtra("extra_telegram_chat_id")?.let { chatId ->
            if (chatId.isNotBlank()) securePreferences.telegramChatId = chatId
        }
        if (intent?.hasExtra("extra_telegram_enabled") == true) {
            val enabled = intent.getBooleanExtra("extra_telegram_enabled", true)
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                appPreferences.setTelegramEnabled(enabled)
            }
        }
    }
}
