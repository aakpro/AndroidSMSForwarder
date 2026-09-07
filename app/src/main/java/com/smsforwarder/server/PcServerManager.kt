package com.smsforwarder.server

import android.content.Context
import android.util.Log
import com.smsforwarder.data.local.AppDatabase
import com.smsforwarder.data.preferences.AppPreferences
import com.smsforwarder.util.NetworkUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PcServerManager private constructor(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var server: LocalWebServer? = null
    private val appPreferences = AppPreferences(context)
    private val database = AppDatabase.getInstance(context)

    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

    private val _serverUrl = MutableStateFlow<String?>(null)
    val serverUrl: StateFlow<String?> = _serverUrl.asStateFlow()

    init {
        // Sync initial state if preference was enabled
        scope.launch {
            val enabled = appPreferences.isPcServerEnabled.first()
            if (enabled) {
                val port = appPreferences.pcServerPort.first()
                val pin = appPreferences.pcServerPin.first()
                val requireAuth = appPreferences.isPcServerRequireAuth.first()
                startServer(port, pin, requireAuth)
            }
        }
    }

    @Synchronized
    fun startServer(port: Int? = null, pin: String? = null, requireAuth: Boolean? = null): Result<String> {
        if (server?.isRunning == true) {
            val currentPort = server?.port ?: 8080
            val url = getLocalUrl(currentPort)
            _serverUrl.value = url
            _isServerRunning.value = true
            return Result.success(url)
        }

        return try {
            val targetPort = port ?: 8080
            val targetPin = pin ?: "1234"
            val targetRequireAuth = requireAuth ?: true

            server = LocalWebServer(
                context = context,
                port = targetPort,
                requireAuth = targetRequireAuth,
                pin = targetPin,
                database = database,
                isServiceEnabledProvider = {
                    runBlockingSafe { appPreferences.isServiceEnabled.first() }
                }
            ).also { it.start() }

            val url = getLocalUrl(targetPort)
            _serverUrl.value = url
            _isServerRunning.value = true
            Log.i(TAG, "PC Server started successfully at $url")
            Result.success(url)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start PC server", e)
            _isServerRunning.value = false
            _serverUrl.value = null
            Result.failure(e)
        }
    }

    @Synchronized
    fun stopServer() {
        try {
            server?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping PC server", e)
        } finally {
            server = null
            _isServerRunning.value = false
            _serverUrl.value = null
        }
        Log.i(TAG, "PC Server stopped")
    }

    fun getLocalUrl(port: Int): String {
        val ip = NetworkUtil.getLocalIpAddress() ?: "127.0.0.1"
        return "http://$ip:$port"
    }

    private fun <T> runBlockingSafe(block: suspend () -> T): T {
        return kotlinx.coroutines.runBlocking { block() }
    }

    companion object {
        private const val TAG = "PcServerManager"

        @Volatile
        private var instance: PcServerManager? = null

        fun getInstance(context: Context): PcServerManager {
            return instance ?: synchronized(this) {
                instance ?: PcServerManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
