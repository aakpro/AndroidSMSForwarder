package com.smsforwarder.util

import org.junit.Assert.assertEquals
import org.junit.Test

class LogExporterTest {

    @Test
    fun `test CSV escaping of double quotes`() {
        val input = "He said \"Hello World\""
        val escaped = LogExporter.escapeCsv(input)
        assertEquals("He said \"\"Hello World\"\"", escaped)
    }

    @Test
    fun `test CSV escaping of newlines and carriage returns`() {
        val input = "Line 1\nLine 2\r\nLine 3"
        val escaped = LogExporter.escapeCsv(input)
        assertEquals("Line 1 Line 2 Line 3", escaped)
    }

    @Test
    fun `test CSV escaping with clean string`() {
        val input = "Simple plain text message 12345"
        val escaped = LogExporter.escapeCsv(input)
        assertEquals(input, escaped)
    }
}
