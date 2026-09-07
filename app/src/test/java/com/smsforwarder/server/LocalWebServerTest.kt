package com.smsforwarder.server

import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class LocalWebServerTest {

    private var server: LocalWebServer? = null

    @After
    fun tearDown() {
        server?.stop()
    }

    @Test
    fun `test server serves dashboard HTML on root path`() {
        server = LocalWebServer(
            context = null,
            port = 0,
            requireAuth = false
        ).also { it.start() }

        val port = server!!.actualPort
        val conn = URL("http://localhost:$port/").openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 3000
        conn.readTimeout = 3000

        val code = conn.responseCode
        assertEquals(200, code)
        assertTrue(conn.contentType.contains("text/html"))

        val content = conn.inputStream.bufferedReader().readText()
        assertTrue(content.contains("Android SMS Forwarder") || content.contains("SMS Forwarder"))
    }

    @Test
    fun `test auth endpoint with correct and incorrect pin`() {
        server = LocalWebServer(
            context = null,
            port = 0,
            requireAuth = true,
            pin = "5678"
        ).also { it.start() }

        val port = server!!.actualPort

        // 1. Incorrect PIN
        var conn = URL("http://localhost:$port/api/auth").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        OutputStreamWriter(conn.outputStream).use { it.write("""{"pin":"1111"}""") }

        assertEquals(401, conn.responseCode)
        val errResponse = conn.errorStream.bufferedReader().readText()
        assertFalse(MiniJson.getBoolean(errResponse, "authenticated", true))

        // 2. Correct PIN
        conn = URL("http://localhost:$port/api/auth").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        OutputStreamWriter(conn.outputStream).use { it.write("""{"pin":"5678"}""") }

        assertEquals(200, conn.responseCode)
        val okResponse = conn.inputStream.bufferedReader().readText()
        assertTrue(MiniJson.getBoolean(okResponse, "authenticated", false))
        assertEquals("5678", MiniJson.getString(okResponse, "token"))
    }

    @Test
    fun `test protected endpoint returns 401 when pin is missing or wrong`() {
        server = LocalWebServer(
            context = null,
            port = 0,
            requireAuth = true,
            pin = "4321"
        ).also { it.start() }

        val port = server!!.actualPort

        // Request without Auth header
        var conn = URL("http://localhost:$port/api/status").openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        assertEquals(401, conn.responseCode)

        // Request with wrong Bearer header
        conn = URL("http://localhost:$port/api/status").openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer 9999")
        assertEquals(401, conn.responseCode)

        // Request with correct Bearer header
        conn = URL("http://localhost:$port/api/status").openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer 4321")
        assertEquals(200, conn.responseCode)

        val jsonStr = conn.inputStream.bufferedReader().readText()
        assertEquals("online", MiniJson.getString(jsonStr, "status"))
        assertEquals(port, MiniJson.getInt(jsonStr, "port"))
    }

    @Test
    fun `test send SMS endpoint invokes dispatcher callback`() {
        var dispatchedRecipient: String? = null
        var dispatchedMessage: String? = null

        server = LocalWebServer(
            context = null,
            port = 0,
            requireAuth = true,
            pin = "9999",
            smsSender = { recipient, message, _ ->
                dispatchedRecipient = recipient
                dispatchedMessage = message
                Result.success(Unit)
            }
        ).also { it.start() }

        val port = server!!.actualPort
        val conn = URL("http://localhost:$port/api/send").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer 9999")

        val payload = """{"recipient":"+15551234567","message":"Hello from PC browser test!","simSlot":0}"""
        OutputStreamWriter(conn.outputStream).use { it.write(payload) }

        assertEquals(200, conn.responseCode)
        val res = conn.inputStream.bufferedReader().readText()
        assertTrue(MiniJson.getBoolean(res, "success", false))

        assertEquals("+15551234567", dispatchedRecipient)
        assertEquals("Hello from PC browser test!", dispatchedMessage)
    }

    @Test
    fun `test CORS preflight OPTIONS request returns 204`() {
        server = LocalWebServer(
            context = null,
            port = 0,
            requireAuth = true
        ).also { it.start() }

        val port = server!!.actualPort
        val conn = URL("http://localhost:$port/api/messages").openConnection() as HttpURLConnection
        conn.requestMethod = "OPTIONS"
        assertEquals(204, conn.responseCode)
        assertEquals("*", conn.getHeaderField("Access-Control-Allow-Origin"))
        assertTrue(conn.getHeaderField("Access-Control-Allow-Methods").contains("POST"))
    }

    @Test
    fun `test 404 for unknown endpoint`() {
        server = LocalWebServer(
            context = null,
            port = 0,
            requireAuth = false
        ).also { it.start() }

        val port = server!!.actualPort
        val conn = URL("http://localhost:$port/api/nonexistent").openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        assertEquals(404, conn.responseCode)
    }
}
