package com.smsforwarder.server

object MiniJson {

    fun escape(text: String?): String {
        if (text == null) return ""
        val sb = StringBuilder()
        for (c in text) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (c.code < 0x20) {
                        sb.append(String.format("\\u%04x", c.code))
                    } else {
                        sb.append(c)
                    }
                }
            }
        }
        return sb.toString()
    }

    fun getString(json: String, key: String): String? {
        val pattern = Regex(""""$key"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""")
        val match = pattern.find(json) ?: return null
        val raw = match.groupValues[1]
        return unescape(raw)
    }

    fun getInt(json: String, key: String, default: Int = -1): Int {
        val pattern = Regex(""""$key"\s*:\s*(-?\d+)""")
        val match = pattern.find(json) ?: return default
        return match.groupValues[1].toIntOrNull() ?: default
    }

    fun getBoolean(json: String, key: String, default: Boolean = false): Boolean {
        val pattern = Regex(""""$key"\s*:\s*(true|false)""", RegexOption.IGNORE_CASE)
        val match = pattern.find(json) ?: return default
        return match.groupValues[1].equals("true", ignoreCase = true)
    }

    fun unescape(input: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '\\' && i + 1 < input.length) {
                val next = input[i + 1]
                when (next) {
                    '\\' -> { sb.append('\\'); i += 2 }
                    '"' -> { sb.append('"'); i += 2 }
                    '/' -> { sb.append('/'); i += 2 }
                    'b' -> { sb.append('\b'); i += 2 }
                    'f' -> { sb.append('\u000C'); i += 2 }
                    'n' -> { sb.append('\n'); i += 2 }
                    'r' -> { sb.append('\r'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    'u' -> {
                        if (i + 5 < input.length) {
                            val hex = input.substring(i + 2, i + 6)
                            val code = hex.toIntOrNull(16)
                            if (code != null) {
                                sb.append(code.toChar())
                                i += 6
                                continue
                            }
                        }
                        sb.append(next)
                        i += 2
                    }
                    else -> { sb.append(next); i += 2 }
                }
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }
}
