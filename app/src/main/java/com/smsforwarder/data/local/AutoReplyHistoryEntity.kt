package com.smsforwarder.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "auto_reply_history")
data class AutoReplyHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val phoneNumber: String,
    val timestamp: Long,
    val replyText: String,
    val simSlotIndex: Int = 0
)
