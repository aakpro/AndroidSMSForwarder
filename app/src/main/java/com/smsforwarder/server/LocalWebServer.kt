package com.smsforwarder.server

import android.content.Context
import android.util.Log
import com.smsforwarder.data.local.AppDatabase
import com.smsforwarder.data.local.DiagnosticLogEntity
import com.smsforwarder.data.local.SmsLogEntity
import com.smsforwarder.util.NetworkUtil
import com.smsforwarder.util.SimUtil
import com.smsforwarder.util.SmsSenderHelper
import kotlinx.coroutines.runBlocking
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.util.concurrent.Executors

class LocalWebServer(
    private val context: Context? = null,
    val port: Int = 8080,
    var requireAuth: Boolean = true,
    var pin: String = "1234",
    private val database: AppDatabase? = null,
    private val smsSender: ((recipient: String, message: String, subscriptionId: Int) -> Result<Unit>)? = null,
    private val isServiceEnabledProvider: (() -> Boolean)? = null
) {
    private val executor = Executors.newFixedThreadPool(8)

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    var isRunning = false
        private set

    val actualPort: Int
        get() = serverSocket?.localPort ?: port

    fun start() {
        if (isRunning) return
        try {
            serverSocket = ServerSocket().apply {
                reuseAddress = true
                bind(java.net.InetSocketAddress(port))
            }
            isRunning = true
            logI("LocalWebServer started on port $actualPort")

            executor.execute {
                while (isRunning && serverSocket != null && !serverSocket!!.isClosed) {
                    try {
                        val client = serverSocket!!.accept()
                        executor.execute { handleClient(client) }
                    } catch (e: SocketException) {
                        if (!isRunning) break
                    } catch (e: Exception) {
                        logE("Error accepting client connection", e)
                    }
                }
            }
        } catch (e: Exception) {
            logE("Failed to start LocalWebServer on port $port", e)
            isRunning = false
            throw e
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            logE("Error closing server socket", e)
        } finally {
            serverSocket = null
        }
        logI("LocalWebServer stopped")
    }

    private fun handleClient(socket: Socket) {
        val output = socket.getOutputStream()
        try {
            socket.soTimeout = 10000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))

            val requestLine = reader.readLine() ?: return
            val requestParts = requestLine.split(" ")
            if (requestParts.size < 2) return

            val method = requestParts[0].uppercase()
            val rawUri = requestParts[1]
            val path = rawUri.substringBefore("?")
            val queryString = if (rawUri.contains("?")) rawUri.substringAfter("?") else ""
            val queryParams = parseQueryParams(queryString)

            // Parse headers
            val headers = mutableMapOf<String, String>()
            var headerLine: String?
            while (reader.readLine().also { headerLine = it } != null) {
                if (headerLine.isNullOrEmpty()) break
                val colonIdx = headerLine!!.indexOf(':')
                if (colonIdx > 0) {
                    val k = headerLine!!.substring(0, colonIdx).trim().lowercase()
                    val v = headerLine!!.substring(colonIdx + 1).trim()
                    headers[k] = v
                }
            }

            // Parse body if present
            val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
            val body = if (contentLength > 0) {
                val buf = CharArray(contentLength)
                var totalRead = 0
                while (totalRead < contentLength) {
                    val read = reader.read(buf, totalRead, contentLength - totalRead)
                    if (read == -1) break
                    totalRead += read
                }
                String(buf, 0, totalRead)
            } else ""

            // Handle CORS preflight
            if (method == "OPTIONS") {
                sendResponse(output, 204, "No Content", "text/plain", "")
                return
            }

            // Check Authentication
            if (requireAuth && !isPublicEndpoint(path)) {
                val authHeader = headers["authorization"]
                val xPin = headers["x-auth-pin"]
                val queryPin = queryParams["pin"]

                val tokenFromBearer = authHeader?.removePrefix("Bearer ")?.trim()
                val providedPin = tokenFromBearer ?: xPin ?: queryPin

                if (providedPin != pin) {
                    val errJson = """{"authenticated":false,"error":"Unauthorized. Please provide valid PIN."}"""
                    sendResponse(output, 401, "Unauthorized", "application/json", errJson)
                    return
                }
            }

            // Route request
            dispatchRoute(method, path, queryParams, body, output)
        } catch (e: Exception) {
            logE("Error handling client request", e)
            try {
                sendResponse(output, 500, "Internal Server Error", "application/json", """{"error":"Internal Server Error"}""")
            } catch (_: Exception) {}
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {}
        }
    }

    private fun isPublicEndpoint(path: String): Boolean {
        return path == "/" || path == "/index.html" || path == "/api/auth" || path == "/favicon.ico"
    }

    private fun dispatchRoute(
        method: String,
        path: String,
        queryParams: Map<String, String>,
        body: String,
        output: OutputStream
    ) {
        when {
            // Serve Dashboard HTML
            (method == "GET" && (path == "/" || path == "/index.html")) -> {
                val html = WebDashboardAssets.getDashboardHtml(context)
                sendResponse(output, 200, "OK", "text/html; charset=UTF-8", html)
            }

            // PIN Verification
            (method == "POST" && path == "/api/auth") -> {
                handleAuth(body, output)
            }

            // Device Status
            (method == "GET" && path == "/api/status") -> {
                handleStatus(output)
            }

            // List Received Messages
            (method == "GET" && path == "/api/messages") -> {
                handleMessages(queryParams, output)
            }

            // Send Outgoing SMS
            (method == "POST" && path == "/api/send") -> {
                handleSend(body, output)
            }

            else -> {
                val errJson = """{"error":"Not Found","path":"${MiniJson.escape(path)}"}"""
                sendResponse(output, 404, "Not Found", "application/json", errJson)
            }
        }
    }

    private fun handleAuth(body: String, output: OutputStream) {
        val inputPin = MiniJson.getString(body, "pin")?.trim() ?: ""

        if (inputPin == pin) {
            val res = """{"authenticated":true,"token":"${MiniJson.escape(pin)}"}"""
            sendResponse(output, 200, "OK", "application/json", res)
        } else {
            val res = """{"authenticated":false,"error":"Invalid PIN"}"""
            sendResponse(output, 401, "Unauthorized", "application/json", res)
        }
    }

    private fun handleStatus(output: OutputStream) {
        val batteryInfo = context?.let { NetworkUtil.getBatteryInfo(it) }
        val activeSims = context?.let { SimUtil(it).getActiveSimCards() } ?: emptyList()
        val networkType = context?.let { NetworkUtil.getNetworkType(it) } ?: "UNKNOWN"

        val totalCount = runBlocking {
            try {
                database?.smsLogDao()?.getTotalCountSync() ?: 0
            } catch (e: Exception) {
                0
            }
        }

        val serviceRunning = isServiceEnabledProvider?.invoke() ?: false

        val simsJson = activeSims.joinToString(prefix = "[", postfix = "]") { sim ->
            val numStr = if (!sim.phoneNumber.isNullOrBlank()) " (${sim.phoneNumber})" else ""
            "\"${MiniJson.escape("${sim.displayName}: ${sim.carrierName}$numStr")}\""
        }

        val res = """{"status":"online","battery":${batteryInfo?.percentage ?: -1},"isCharging":${batteryInfo?.isCharging ?: false},"networkType":"${MiniJson.escape(networkType)}","serviceEnabled":$serviceRunning,"totalSmsCount":$totalCount,"sims":$simsJson,"port":$actualPort}"""

        sendResponse(output, 200, "OK", "application/json", res)
    }

    private fun handleMessages(queryParams: Map<String, String>, output: OutputStream) {
        val limit = queryParams["limit"]?.toIntOrNull()?.coerceIn(1, 200) ?: 50
        val offset = queryParams["offset"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val filterQuery = queryParams["query"]?.lowercase()

        val logs: List<SmsLogEntity> = runBlocking {
            try {
                database?.smsLogDao()?.getRecentLogsSync(limit, offset) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }

        val sb = StringBuilder("[")
        var first = true
        logs.forEach { log ->
            if (!filterQuery.isNullOrBlank()) {
                val matches = log.sender.lowercase().contains(filterQuery) ||
                        log.messageBody.lowercase().contains(filterQuery) ||
                        log.carrierName.lowercase().contains(filterQuery)
                if (!matches) return@forEach
            }

            if (!first) sb.append(",")
            first = false

            sb.append("""{"id":${log.id},"sender":"${MiniJson.escape(log.sender)}","messageBody":"${MiniJson.escape(log.messageBody)}","simSlotIndex":${log.simSlotIndex},"simDisplayName":"${MiniJson.escape(log.simDisplayName)}","carrierName":"${MiniJson.escape(log.carrierName)}","timestamp":${log.timestamp},"telegramStatus":"${MiniJson.escape(log.telegramStatus)}","whatsappStatus":"${MiniJson.escape(log.whatsappStatus)}","emailStatus":"${MiniJson.escape(log.emailStatus)}","discordStatus":"${MiniJson.escape(log.discordStatus)}","isSensitive":${log.isSensitive},"errorMessage":"${MiniJson.escape(log.errorMessage ?: "")}"}""")
        }
        sb.append("]")

        sendResponse(output, 200, "OK", "application/json", sb.toString())
    }

    private fun handleSend(body: String, output: OutputStream) {
        val recipient = MiniJson.getString(body, "recipient")?.trim() ?: ""
        val message = MiniJson.getString(body, "message")?.trim() ?: ""
        val simSlot = MiniJson.getInt(body, "simSlot", -1)

        if (recipient.isBlank() || message.isBlank()) {
            val err = """{"success":false,"error":"Both recipient and message are required"}"""
            sendResponse(output, 400, "Bad Request", "application/json", err)
            return
        }

        // Determine subscriptionId if SIM slot selected
        val subscriptionId = if (simSlot >= 0 && context != null) {
            val activeSims = SimUtil(context).getActiveSimCards()
            activeSims.find { it.slotIndex == simSlot }?.subscriptionId ?: -1
        } else {
            -1
        }

        val sendResult = if (smsSender != null) {
            smsSender.invoke(recipient, message, subscriptionId)
        } else if (context != null) {
            SmsSenderHelper.sendSms(context, recipient, message, subscriptionId)
        } else {
            Result.failure(IllegalStateException("No SMS dispatcher available"))
        }

        if (sendResult.isSuccess) {
            // Log to diagnostics
            runBlocking {
                try {
                    database?.diagnosticLogDao()?.insertLog(
                        DiagnosticLogEntity(
                            timestamp = System.currentTimeMillis(),
                            eventType = "PC_WEB_SMS_SENT",
                            message = "SMS dispatched to $recipient via PC Web",
                            details = message
                        )
                    )
                } catch (_: Exception) {}
            }

            val res = """{"success":true,"message":"SMS dispatched successfully to ${MiniJson.escape(recipient)}"}"""
            sendResponse(output, 200, "OK", "application/json", res)
        } else {
            val err = sendResult.exceptionOrNull()?.message ?: "Failed to dispatch SMS"
            val res = """{"success":false,"error":"${MiniJson.escape(err)}"}"""
            sendResponse(output, 500, "Internal Server Error", "application/json", res)
        }
    }

    private fun sendResponse(
        output: OutputStream,
        statusCode: Int,
        statusText: String,
        contentType: String,
        body: String
    ) {
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        val headerBuilder = StringBuilder().apply {
            append("HTTP/1.1 $statusCode $statusText\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${bodyBytes.size}\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
            append("Access-Control-Allow-Headers: Content-Type, Authorization, X-Auth-PIN\r\n")
            append("Connection: close\r\n\r\n")
        }

        output.write(headerBuilder.toString().toByteArray(Charsets.UTF_8))
        output.write(bodyBytes)
        output.flush()
    }

    private fun parseQueryParams(queryString: String): Map<String, String> {
        if (queryString.isBlank()) return emptyMap()
        val result = mutableMapOf<String, String>()
        val pairs = queryString.split("&")
        for (pair in pairs) {
            val idx = pair.indexOf("=")
            if (idx > 0) {
                val key = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                val value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                result[key] = value
            }
        }
        return result
    }

    companion object {
        private const val TAG = "LocalWebServer"

        private fun logI(msg: String) {
            try {
                Log.i(TAG, msg)
            } catch (_: Throwable) {
                // Ignore in unmocked test environments
            }
        }

        private fun logE(msg: String, tr: Throwable? = null) {
            try {
                if (tr != null) Log.e(TAG, msg, tr) else Log.e(TAG, msg)
            } catch (_: Throwable) {
                // Ignore in unmocked test environments
            }
        }
    }
}
