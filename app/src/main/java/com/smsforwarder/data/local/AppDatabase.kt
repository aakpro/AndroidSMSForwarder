package com.smsforwarder.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SmsLogEntity::class,
        DiagnosticLogEntity::class,
        FilterRuleEntity::class,
        AutoReplyHistoryEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun smsLogDao(): SmsLogDao
    abstract fun diagnosticLogDao(): DiagnosticLogDao
    abstract fun filterRuleDao(): FilterRuleDao
    abstract fun autoReplyHistoryDao(): AutoReplyHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sms_forwarder.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}
