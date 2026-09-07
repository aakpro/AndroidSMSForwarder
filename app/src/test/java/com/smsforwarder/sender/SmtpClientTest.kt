package com.smsforwarder.sender

import com.smsforwarder.data.model.SmsMessageItem
import com.smsforwarder.data.preferences.SmtpEncryption
import com.smsforwarder.util.TemplateFormatter
import org.junit.Assert.*
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

class SmtpClientTest {

    @Test
    fun `test SmtpEncryption enum parsing and default ports`() {
        assertEquals(SmtpEncryption.STARTTLS, SmtpEncryption.fromKey("STARTTLS"))
        assertEquals(587, SmtpEncryption.STARTTLS.defaultPort)

        assertEquals(SmtpEncryption.SSL_TLS, SmtpEncryption.fromKey("SSL_TLS"))
        assertEquals(465, SmtpEncryption.SSL_TLS.defaultPort)

        assertEquals(SmtpEncryption.PLAIN, SmtpEncryption.fromKey("PLAIN"))
        assertEquals(25, SmtpEncryption.PLAIN.defaultPort)

        // Fallback check
        assertEquals(SmtpEncryption.STARTTLS, SmtpEncryption.fromKey("UNKNOWN_SCHEME"))
    }

    @Test
    fun `test config validation fails for empty host`() {
        val config = SmtpConfig(
            host = "",
            port = 587,
            encryption = SmtpEncryption.PLAIN
        )
        val message = EmailMessage(
            from = "sender@test.com",
            recipients = listOf("rcpt@test.com"),
            subject = "Test",
            htmlBody = "<b>Hello</b>"
        )

        val client = SmtpClient(config)
        try {
            client.send(message)
            fail("Expected IllegalArgumentException for blank host")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("host"))
        }
    }

    @Test
    fun `test config validation fails for invalid port`() {
        val config = SmtpConfig(
            host = "smtp.example.com",
            port = 99999,
            encryption = SmtpEncryption.PLAIN
        )
        val message = EmailMessage(
            from = "sender@test.com",
            recipients = listOf("rcpt@test.com"),
            subject = "Test",
            htmlBody = "<b>Hello</b>"
        )

        val client = SmtpClient(config)
        try {
            client.send(message)
            fail("Expected IllegalArgumentException for invalid port")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("port"))
        }
    }

    @Test
    fun `test config validation fails for empty recipients`() {
        val config = SmtpConfig(
            host = "smtp.example.com",
            port = 25,
            encryption = SmtpEncryption.PLAIN
        )
        val message = EmailMessage(
            from = "sender@test.com",
            recipients = emptyList(),
            subject = "Test",
            htmlBody = "<b>Hello</b>"
        )

        val client = SmtpClient(config)
        try {
            client.send(message)
            fail("Expected IllegalArgumentException for empty recipients")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("recipient"))
        }
    }

    @Test
    fun `test email subject and body template formatting`() {
        val sms = SmsMessageItem(
            sender = "BankOfAmerica",
            body = "Your one-time code is 782194",
            timestamp = 1757182359000L,
            simSlotIndex = 0,
            subscriptionId = 1,
            carrierName = "AT&T"
        )

        val subjectTemplate = "[SMS] From {sender} ({sim}) - {time}"
        val subject = TemplateFormatter.format(subjectTemplate, sms, escapeHtml = false)

        assertTrue(subject.contains("BankOfAmerica"))
        assertTrue(subject.contains("SIM 1"))

        val htmlTemplate = "<div>New message from {sender}: {message}</div>"
        val htmlBody = TemplateFormatter.format(htmlTemplate, sms, escapeHtml = true)

        assertTrue(htmlBody.contains("BankOfAmerica"))
        assertTrue(htmlBody.contains("782194"))
    }

    @Test(timeout = 5000)
    fun `test simulated SMTP transaction on loopback`() {
        val server = ServerSocket(0)
        val port = server.localPort
        val receivedCommands = mutableListOf<String>()

        val executor = Executors.newSingleThreadExecutor()
        executor.submit {
            val socket: Socket = server.accept()
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = PrintWriter(socket.getOutputStream(), true)

            // Greeting
            writer.println("220 mock.smtp.server Service Ready")

            // Read EHLO
            receivedCommands.add(reader.readLine() ?: "")
            writer.println("250-mock.smtp.server")
            writer.println("250 AUTH LOGIN")

            // Read AUTH LOGIN
            receivedCommands.add(reader.readLine() ?: "")
            writer.println("334 VXNlcm5hbWU6") // Username:

            // Read Username
            receivedCommands.add(reader.readLine() ?: "")
            writer.println("334 UGFzc3dvcmQ6") // Password:

            // Read Password
            receivedCommands.add(reader.readLine() ?: "")
            writer.println("235 2.7.0 Authentication successful")

            // Read MAIL FROM
            receivedCommands.add(reader.readLine() ?: "")
            writer.println("250 2.1.0 Sender OK")

            // Read RCPT TO
            receivedCommands.add(reader.readLine() ?: "")
            writer.println("250 2.1.5 Recipient OK")

            // Read DATA
            receivedCommands.add(reader.readLine() ?: "")
            writer.println("354 Start mail input; end with <CRLF>.<CRLF>")

            // Read MIME content until .
            while (true) {
                val line = reader.readLine() ?: break
                if (line == ".") break
            }
            writer.println("250 2.0.0 OK message queued")

            // Read QUIT
            receivedCommands.add(reader.readLine() ?: "")
            writer.println("221 2.0.0 Bye")

            socket.close()
            server.close()
        }

        val config = SmtpConfig(
            host = "127.0.0.1",
            port = port,
            encryption = SmtpEncryption.PLAIN,
            username = "testuser@gmail.com",
            password = "testpassword123"
        )

        val message = EmailMessage(
            from = "testuser@gmail.com",
            recipients = listOf("recipient@test.com"),
            subject = "Forwarded SMS Alert",
            htmlBody = "<h3>New SMS</h3><p>Test body content</p>"
        )

        val client = SmtpClient(config)
        client.send(message)

        // Verify sent commands
        assertTrue(receivedCommands.any { it.startsWith("EHLO") })
        assertTrue(receivedCommands.any { it.startsWith("AUTH LOGIN") })
        assertTrue(receivedCommands.any { it.startsWith("MAIL FROM:<testuser@gmail.com>") })
        assertTrue(receivedCommands.any { it.startsWith("RCPT TO:<recipient@test.com>") })
        assertTrue(receivedCommands.any { it.startsWith("DATA") })
        assertTrue(receivedCommands.any { it.startsWith("QUIT") })

        executor.shutdown()
    }

    @Test(timeout = 5000)
    fun `test SMTP authentication failure handling`() {
        val server = ServerSocket(0)
        val port = server.localPort

        val executor = Executors.newSingleThreadExecutor()
        executor.submit {
            val socket: Socket = server.accept()
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = PrintWriter(socket.getOutputStream(), true)

            writer.println("220 mock.smtp.server Service Ready")
            reader.readLine() // EHLO
            writer.println("250 AUTH LOGIN")
            reader.readLine() // AUTH LOGIN
            writer.println("334 VXNlcm5hbWU6")
            reader.readLine() // Username
            writer.println("334 UGFzc3dvcmQ6")
            reader.readLine() // Password
            writer.println("535 5.7.8 Authentication credentials invalid") // Fail here

            socket.close()
            server.close()
        }

        val config = SmtpConfig(
            host = "127.0.0.1",
            port = port,
            encryption = SmtpEncryption.PLAIN,
            username = "wrong@gmail.com",
            password = "wrongpassword"
        )

        val message = EmailMessage(
            from = "wrong@gmail.com",
            recipients = listOf("recipient@test.com"),
            subject = "Test",
            htmlBody = "<p>Test</p>"
        )

        val client = SmtpClient(config)
        try {
            client.send(message)
            fail("Expected SmtpException with code 535")
        } catch (e: SmtpException) {
            assertEquals(535, e.code)
            assertTrue(e.message!!.contains("535"))
        } finally {
            executor.shutdown()
        }
    }
}
