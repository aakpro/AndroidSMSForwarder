package com.smsforwarder.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: SmsLogEntity): Long

    @Query("UPDATE sms_logs SET telegramStatus = :telegramStatus, whatsappStatus = :whatsappStatus, errorMessage = :error WHERE id = :id")
    suspend fun updateStatus(id: Long, telegramStatus: String, whatsappStatus: String, error: String?)

    @Query("UPDATE sms_logs SET telegramStatus = :telegramStatus, whatsappStatus = :whatsappStatus, discordStatus = :discordStatus, webhookStatus = :webhookStatus, emailStatus = :emailStatus, durationMs = :durationMs, batteryLevel = :batteryLevel, networkType = :networkType, errorMessage = :error WHERE id = :id")
    suspend fun updateAllStatuses(
        id: Long,
        telegramStatus: String,
        whatsappStatus: String,
        discordStatus: String,
        webhookStatus: String,
        emailStatus: String,
        durationMs: Long,
        batteryLevel: Int,
        networkType: String,
        error: String?
    )

    @Query("UPDATE sms_logs SET telegramStatus = :status WHERE id = :id")
    suspend fun updateTelegramStatus(id: Long, status: String)

    @Query("UPDATE sms_logs SET whatsappStatus = :status WHERE id = :id")
    suspend fun updateWhatsAppStatus(id: Long, status: String)

    @Query("UPDATE sms_logs SET emailStatus = :status WHERE id = :id")
    suspend fun updateEmailStatus(id: Long, status: String)

    @Query("SELECT * FROM sms_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 100): Flow<List<SmsLogEntity>>

    @Query("SELECT * FROM sms_logs WHERE sender LIKE '%' || :query || '%' OR messageBody LIKE '%' || :query || '%' OR carrierName LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT :limit")
    fun searchLogs(query: String, limit: Int = 100): Flow<List<SmsLogEntity>>

    @Query("SELECT * FROM sms_logs WHERE simSlotIndex = :slotIndex ORDER BY timestamp DESC LIMIT :limit")
    fun getLogsBySimSlot(slotIndex: Int, limit: Int = 100): Flow<List<SmsLogEntity>>

    @Query("SELECT * FROM sms_logs WHERE telegramStatus = 'FAILED' OR whatsappStatus = 'FAILED' OR discordStatus = 'FAILED' OR webhookStatus = 'FAILED' OR emailStatus = 'FAILED' ORDER BY timestamp DESC")
    fun getFailedLogs(): Flow<List<SmsLogEntity>>

    @Query("SELECT * FROM sms_logs ORDER BY timestamp DESC")
    suspend fun getAllLogsSync(): List<SmsLogEntity>

    @Query("SELECT * FROM sms_logs WHERE id = :id")
    suspend fun getLogById(id: Long): SmsLogEntity?

    @Query("SELECT COUNT(*) FROM sms_logs")
    fun getTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sms_logs")
    suspend fun getTotalCountSync(): Int

    @Query("SELECT * FROM sms_logs ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getRecentLogsSync(limit: Int = 50, offset: Int = 0): List<SmsLogEntity>

    @Query("SELECT COUNT(*) FROM sms_logs WHERE telegramStatus = 'SUCCESS' OR whatsappStatus = 'SUCCESS' OR discordStatus = 'SUCCESS' OR webhookStatus = 'SUCCESS' OR emailStatus = 'SUCCESS'")
    fun getSuccessCount(): Flow<Int>

    @Query("DELETE FROM sms_logs")
    suspend fun clearAll()

    @Query("DELETE FROM sms_logs WHERE timestamp < :thresholdTimestamp")
    suspend fun deleteOlderThan(thresholdTimestamp: Long)
}
