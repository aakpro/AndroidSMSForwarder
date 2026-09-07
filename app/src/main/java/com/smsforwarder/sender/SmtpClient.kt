package com.smsforwarder.sender

import android.util.Log
import com.smsforwarder.data.preferences.SmtpEncryption
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

data class EmailMessage(
    val from: String,
    val recipients: List<String>,
    val subject: String,
    val htmlBody: String,
    val plainBody: String? = null
)

data class SmtpConfig(
    val host: String,
    val port: Int,
    val encryption: SmtpEncryption,
    val username: String = "",
    val password: String = "",
    val timeoutMs: Int = 15000
)

data class SmtpResponse(
    val code: Int,
    val message: String
)

class SmtpClient(private val config: SmtpConfig) {

    @Throws(Exception::class)
    fun send(message: EmailMessage) {
        if (config.host.isBlank()) throw IllegalArgumentException("SMTP host must not be empty")
        if (config.port <= 0 || config.port > 65535) throw IllegalArgumentException("Invalid SMTP port: ${config.port}")
        if (message.recipients.isEmpty()) throw IllegalArgumentException("At least one recipient email address is required")

        var socket: Socket? = null
        var reader: BufferedReader? = null
        var writer: PrintWriter? = null

        try {
            // 1. Establish Initial Connection
            if (config.encryption == SmtpEncryption.SSL_TLS) {
                val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
                val sslSocket = sslFactory.createSocket() as SSLSocket
                sslSocket.connect(InetSocketAddress(config.host, config.port), config.timeoutMs)
                sslSocket.soTimeout = config.timeoutMs
                sslSocket.startHandshake()
                socket = sslSocket
            } else {
                val plainSocket = Socket()
                plainSocket.connect(InetSocketAddress(config.host, config.port), config.timeoutMs)
                plainSocket.soTimeout = config.timeoutMs
                socket = plainSocket
            }

            reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            writer = PrintWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)

            // 2. Read Greeting Banner
            val banner = readResponse(reader)
            if (banner.code != 220) {
                throw SmtpException(banner.code, "Unexpected server greeting: ${banner.message}")
            }

            // 3. Send EHLO
            sendCommand(writer, "EHLO localhost")
            var ehloResp = readResponse(reader)
            if (ehloResp.code != 250) {
                // Fallback to HELO if EHLO rejected
                sendCommand(writer, "HELO localhost")
                ehloResp = readResponse(reader)
                if (ehloResp.code != 250) {
                    throw SmtpException(ehloResp.code, "HELO/EHLO rejected: ${ehloResp.message}")
                }
            }

            // 4. Upgrade via STARTTLS if requested
            if (config.encryption == SmtpEncryption.STARTTLS) {
                sendCommand(writer, "STARTTLS")
                val startTlsResp = readResponse(reader)
                if (startTlsResp.code != 220) {
                    throw SmtpException(startTlsResp.code, "STARTTLS command failed: ${startTlsResp.message}")
                }

                val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
                val sslSocket = sslFactory.createSocket(socket, config.host, config.port, true) as SSLSocket
                sslSocket.soTimeout = config.timeoutMs
                sslSocket.startHandshake()
                socket = sslSocket

                reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
                writer = PrintWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)

                // Re-send EHLO after TLS negotiation
                sendCommand(writer, "EHLO localhost")
                val tlsEhloResp = readResponse(reader)
                if (tlsEhloResp.code != 250) {
                    throw SmtpException(tlsEhloResp.code, "EHLO after TLS failed: ${tlsEhloResp.message}")
                }
            }

            // 5. Authentication (AUTH LOGIN)
            if (config.username.isNotBlank() && config.password.isNotBlank()) {
                sendCommand(writer, "AUTH LOGIN")
                val authResp = readResponse(reader)
                if (authResp.code != 334) {
                    throw SmtpException(authResp.code, "AUTH LOGIN rejected: ${authResp.message}")
                }

                // Send Username
                sendCommand(writer, encodeBase64(config.username))
                val userResp = readResponse(reader)
                if (userResp.code != 334) {
                    throw SmtpException(userResp.code, "SMTP username rejected: ${userResp.message}")
                }

                // Send Password
                sendCommand(writer, encodeBase64(config.password))
                val passResp = readResponse(reader)
                if (passResp.code != 235) {
                    throw SmtpException(passResp.code, "SMTP authentication failed: ${passResp.message}")
                }
            }

            // 6. MAIL FROM
            val senderEmail = message.from.ifBlank { config.username }
            sendCommand(writer, "MAIL FROM:<$senderEmail>")
            val mailFromResp = readResponse(reader)
            if (mailFromResp.code != 250) {
                throw SmtpException(mailFromResp.code, "MAIL FROM failed: ${mailFromResp.message}")
            }

            // 7. RCPT TO (for each recipient)
            for (recipient in message.recipients) {
                val cleanRcpt = recipient.trim()
                if (cleanRcpt.isBlank()) continue
                sendCommand(writer, "RCPT TO:<$cleanRcpt>")
                val rcptResp = readResponse(reader)
                if (rcptResp.code != 250 && rcptResp.code != 251) {
                    throw SmtpException(rcptResp.code, "RCPT TO failed for '$cleanRcpt': ${rcptResp.message}")
                }
            }

            // 8. DATA
            sendCommand(writer, "DATA")
            val dataResp = readResponse(reader)
            if (dataResp.code != 354) {
                throw SmtpException(dataResp.code, "DATA command rejected: ${dataResp.message}")
            }

            // 9. Send MIME payload
            val rawMime = buildMimePayload(message, senderEmail)
            writer.print(rawMime)
            writer.print("\r\n.\r\n")
            writer.flush()

            val endDataResp = readResponse(reader)
            if (endDataResp.code != 250) {
                throw SmtpException(endDataResp.code, "Message content rejected: ${endDataResp.message}")
            }

            // 10. QUIT
            try {
                sendCommand(writer, "QUIT")
                readResponse(reader)
            } catch (ignored: Exception) {}

        } finally {
            try { writer?.close() } catch (ignored: Exception) {}
            try { reader?.close() } catch (ignored: Exception) {}
            try { socket?.close() } catch (ignored: Exception) {}
        }
    }

    private fun sendCommand(writer: PrintWriter, command: String) {
        writer.print("$command\r\n")
        writer.flush()
    }

    private fun readResponse(reader: BufferedReader): SmtpResponse {
        val lines = mutableListOf<String>()
        var line: String?

        while (true) {
            line = reader.readLine() ?: throw SmtpException(-1, "Connection terminated unexpectedly by SMTP server")
            lines.add(line)
            if (line.length >= 4) {
                val codeStr = line.substring(0, 3)
                val sep = line[3]
                if (sep == ' ' && codeStr.toIntOrNull() != null) {
                    val code = codeStr.toInt()
                    val fullMsg = lines.joinToString("\n")
                    return SmtpResponse(code, fullMsg)
                }
            }
        }
    }

    private fun buildMimePayload(message: EmailMessage, senderEmail: String): String {
        val boundary = "==_Bound_${UUID.randomUUID().toString().replace("-", "")}"
        val rfcDate = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US).format(Date())
        val encodedSubject = encodeMimeHeader(message.subject)
        val recipientsHeader = message.recipients.joinToString(", ")
        val plainText = message.plainBody ?: stripHtml(message.htmlBody)

        val sb = StringBuilder()
        sb.append("From: <").append(senderEmail).append(">\r\n")
        sb.append("To: ").append(recipientsHeader).append("\r\n")
        sb.append("Date: ").append(rfcDate).append("\r\n")
        sb.append("Subject: ").append(encodedSubject).append("\r\n")
        sb.append("Message-ID: <").append(UUID.randomUUID()).append("@").append(config.host).append(">\r\n")
        sb.append("MIME-Version: 1.0\r\n")
        sb.append("Content-Type: multipart/alternative; boundary=\"").append(boundary).append("\"\r\n")
        sb.append("\r\n")

        // Plain Text Alternative
        sb.append("--").append(boundary).append("\r\n")
        sb.append("Content-Type: text/plain; charset=UTF-8\r\n")
        sb.append("Content-Transfer-Encoding: 8bit\r\n\r\n")
        sb.append(plainText).append("\r\n\r\n")

        // HTML Alternative
        sb.append("--").append(boundary).append("\r\n")
        sb.append("Content-Type: text/html; charset=UTF-8\r\n")
        sb.append("Content-Transfer-Encoding: 8bit\r\n\r\n")
        sb.append(message.htmlBody).append("\r\n\r\n")

        sb.append("--").append(boundary).append("--")

        return sb.toString()
    }

    private fun encodeMimeHeader(text: String): String {
        val b64 = encodeBase64(text)
        return "=?UTF-8?B?$b64?="
    }

    private fun encodeBase64(str: String): String {
        return Base64.getEncoder().encodeToString(str.toByteArray(StandardCharsets.UTF_8))
    }

    private fun stripHtml(html: String): String {
        return html.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
    }

    companion object {
        private const val TAG = "SmtpClient"
    }
}

class SmtpException(val code: Int, message: String) : Exception("SMTP error [$code]: $message")
