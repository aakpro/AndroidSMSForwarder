package com.smsforwarder.data.local

import androidx.room.*

@Dao
interface AutoReplyHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: AutoReplyHistoryEntity): Long

    @Query("SELECT * FROM auto_reply_history WHERE phoneNumber = :phoneNumber ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestReplyForNumber(phoneNumber: String): AutoReplyHistoryEntity?

    @Query("SELECT COUNT(*) FROM auto_reply_history WHERE phoneNumber = :phoneNumber AND timestamp >= :sinceTimestamp")
    suspend fun getReplyCountSince(phoneNumber: String, sinceTimestamp: Long): Int

    @Query("DELETE FROM auto_reply_history WHERE timestamp < :thresholdTimestamp")
    suspend fun deleteOlderThan(thresholdTimestamp: Long)
}
