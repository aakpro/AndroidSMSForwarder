package com.smsforwarder.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.smsforwarder.data.local.DiagnosticLogEntity
import com.smsforwarder.data.local.SmsLogEntity
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogExporter {

    fun exportSmsLogsToCsv(context: Context, logs: List<SmsLogEntity>): Uri? {
        return try {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(exportDir, "sms_forwarder_logs_$timeStamp.csv")

            FileWriter(file).use { writer ->
                // Header
                writer.append("ID,Timestamp,Date,SIM_Slot,Carrier,Sender,Message,Sensitive,Telegram,WhatsApp,Discord,Webhook,Email,Duration_ms,Battery_pct,Network,Error\n")

                val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                for (log in logs) {
                    val dateStr = dateFmt.format(Date(log.timestamp))
                    val escapedMsg = escapeCsv(log.messageBody)
                    val escapedError = escapeCsv(log.errorMessage ?: "")

                    writer.append("${log.id},")
                    writer.append("${log.timestamp},")
                    writer.append("\"$dateStr\",")
                    writer.append("${log.simSlotIndex + 1},")
                    writer.append("\"${escapeCsv(log.carrierName)}\",")
                    writer.append("\"${escapeCsv(log.sender)}\",")
                    writer.append("\"$escapedMsg\",")
                    writer.append("${log.isSensitive},")
                    writer.append("${log.telegramStatus},")
                    writer.append("${log.whatsappStatus},")
                    writer.append("${log.discordStatus},")
                    writer.append("${log.webhookStatus},")
                    writer.append("${log.emailStatus},")
                    writer.append("${log.durationMs},")
                    writer.append("${log.batteryLevel},")
                    writer.append("\"${escapeCsv(log.networkType)}\",")
                    writer.append("\"$escapedError\"\n")
                }
            }

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            android.util.Log.e("LogExporter", "Failed to export SMS logs to CSV", e)
            null
        }
    }

    fun exportDiagnosticsToCsv(context: Context, logs: List<DiagnosticLogEntity>): Uri? {
        return try {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(exportDir, "system_diagnostics_$timeStamp.csv")

            FileWriter(file).use { writer ->
                writer.append("ID,Timestamp,Date,Event_Type,Message,Battery_pct,Is_Charging,Network,Details\n")
                val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                for (log in logs) {
                    val dateStr = dateFmt.format(Date(log.timestamp))
                    writer.append("${log.id},")
                    writer.append("${log.timestamp},")
                    writer.append("\"$dateStr\",")
                    writer.append("\"${escapeCsv(log.eventType)}\",")
                    writer.append("\"${escapeCsv(log.message)}\",")
                    writer.append("${log.batteryLevel},")
                    writer.append("${log.isCharging},")
                    writer.append("\"${escapeCsv(log.networkType)}\",")
                    writer.append("\"${escapeCsv(log.details ?: "")}\"\n")
                }
            }

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            android.util.Log.e("LogExporter", "Failed to export diagnostics to CSV", e)
            null
        }
    }

    fun shareExportedFile(context: Context, fileUri: Uri, title: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(shareIntent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    internal fun escapeCsv(value: String): String {
        return value.replace("\"", "\"\"").replace("\n", " ").replace("\r", "")
    }
}
