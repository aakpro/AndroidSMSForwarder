package com.smsforwarder.server

import org.junit.Assert.*
import org.junit.Test

class MiniJsonTest {

    @Test
    fun `test escape escapes quotes and backslashes`() {
        val input = """Hello "World" \ newline
tab	test"""
        val escaped = MiniJson.escape(input)
        assertTrue(escaped.contains("""\"World\""""))
        assertTrue(escaped.contains("""\\"""))
        assertTrue(escaped.contains("""\n"""))
        assertTrue(escaped.contains("""\t"""))
    }

    @Test
    fun `test getString extracts string field`() {
        val json = """{"recipient":"+1234567890","message":"Verification code is 1234"}"""
        assertEquals("+1234567890", MiniJson.getString(json, "recipient"))
        assertEquals("Verification code is 1234", MiniJson.getString(json, "message"))
        assertNull(MiniJson.getString(json, "nonexistent"))
    }

    @Test
    fun `test getInt extracts integer field`() {
        val json = """{"port":8080,"simSlot":1,"negative":-5}"""
        assertEquals(8080, MiniJson.getInt(json, "port"))
        assertEquals(1, MiniJson.getInt(json, "simSlot"))
        assertEquals(-5, MiniJson.getInt(json, "negative"))
        assertEquals(999, MiniJson.getInt(json, "missing", 999))
    }

    @Test
    fun `test getBoolean extracts boolean field`() {
        val json = """{"enabled":true,"active":false,"status":"ok"}"""
        assertTrue(MiniJson.getBoolean(json, "enabled"))
        assertFalse(MiniJson.getBoolean(json, "active"))
        assertFalse(MiniJson.getBoolean(json, "missing"))
    }

    @Test
    fun `test unescape converts escape sequences back to characters`() {
        val input = """Hello \"World\" \n \t \u0041"""
        val unescaped = MiniJson.unescape(input)
        assertEquals("Hello \"World\" \n \t A", unescaped)
    }
}
