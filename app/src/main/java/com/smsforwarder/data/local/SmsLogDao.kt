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

    @Query("UPDATE sms_logs SET telegramStatus = :status WHERE id = :id")
    suspend fun updateTelegramStatus(id: Long, status: String)

    @Query("UPDATE sms_logs SET whatsappStatus = :status WHERE id = :id")
    suspend fun updateWhatsAppStatus(id: Long, status: String)

    @Query("SELECT * FROM sms_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 100): Flow<List<SmsLogEntity>>

    @Query("SELECT * FROM sms_logs WHERE simSlotIndex = :slotIndex ORDER BY timestamp DESC LIMIT :limit")
    fun getLogsBySimSlot(slotIndex: Int, limit: Int = 100): Flow<List<SmsLogEntity>>

    @Query("SELECT * FROM sms_logs WHERE telegramStatus = 'FAILED' OR whatsappStatus = 'FAILED' ORDER BY timestamp DESC")
    fun getFailedLogs(): Flow<List<SmsLogEntity>>

    @Query("SELECT * FROM sms_logs WHERE id = :id")
    suspend fun getLogById(id: Long): SmsLogEntity?

    @Query("SELECT COUNT(*) FROM sms_logs")
    fun getTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sms_logs WHERE telegramStatus = 'SUCCESS' OR whatsappStatus = 'SUCCESS'")
    fun getSuccessCount(): Flow<Int>

    @Query("DELETE FROM sms_logs")
    suspend fun clearAll()

    @Query("DELETE FROM sms_logs WHERE timestamp < :thresholdTimestamp")
    suspend fun deleteOlderThan(thresholdTimestamp: Long)
}
