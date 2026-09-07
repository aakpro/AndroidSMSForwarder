package com.smsforwarder.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DiagnosticLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: DiagnosticLogEntity): Long

    @Query("SELECT * FROM diagnostic_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 100): Flow<List<DiagnosticLogEntity>>

    @Query("SELECT * FROM diagnostic_logs WHERE eventType = :type ORDER BY timestamp DESC LIMIT :limit")
    fun getLogsByType(type: String, limit: Int = 100): Flow<List<DiagnosticLogEntity>>

    @Query("DELETE FROM diagnostic_logs")
    suspend fun clearAll()

    @Query("DELETE FROM diagnostic_logs WHERE timestamp < :thresholdTimestamp")
    suspend fun deleteOlderThan(thresholdTimestamp: Long)
}
