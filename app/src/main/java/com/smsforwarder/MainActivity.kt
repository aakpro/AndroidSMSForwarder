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

class MainActivity : ComponentActivity() {

    private lateinit var appPreferences: AppPreferences
    private lateinit var securePreferences: SecurePreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        appPreferences = AppPreferences(applicationContext)
        securePreferences = SecurePreferences(applicationContext)

        setContent {
            SmsForwarderTheme {
                var permissionsGranted by remember {
                    mutableStateOf(PermissionHelper.hasAllRequiredPermissions(this))
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { result ->
                    permissionsGranted = PermissionHelper.hasAllRequiredPermissions(this)
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
