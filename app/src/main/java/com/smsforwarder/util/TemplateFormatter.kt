package com.smsforwarder.util

import com.smsforwarder.data.model.SmsMessageItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TemplateFormatter {

    fun format(
        template: String,
        sms: SmsMessageItem,
        escapeHtml: Boolean = true
    ): String {
        val date = Date(sms.timestamp)
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        val rawBody = if (escapeHtml) escapeHtml(sms.body) else sms.body
        val rawSender = if (escapeHtml) escapeHtml(sms.sender) else sms.sender
        val rawCarrier = if (escapeHtml) escapeHtml(sms.carrierName) else sms.carrierName
        val simName = "SIM ${sms.simSlotIndex + 1}"

        return template
            .replace("{sim}", simName)
            .replace("{carrier}", rawCarrier)
            .replace("{sender}", rawSender)
            .replace("{message}", rawBody)
            .replace("{time}", timeFormat.format(date))
            .replace("{date}", dateFormat.format(date))
    }

    fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}
