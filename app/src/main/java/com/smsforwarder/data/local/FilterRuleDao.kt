package com.smsforwarder.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FilterRuleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: FilterRuleEntity): Long

    @Update
    suspend fun updateRule(rule: FilterRuleEntity)

    @Delete
    suspend fun deleteRule(rule: FilterRuleEntity)

    @Query("DELETE FROM filter_rules WHERE id = :id")
    suspend fun deleteRuleById(id: Long)

    @Query("SELECT * FROM filter_rules ORDER BY priority ASC, id ASC")
    fun getAllRules(): Flow<List<FilterRuleEntity>>

    @Query("SELECT * FROM filter_rules ORDER BY priority ASC, id ASC")
    suspend fun getAllRulesSync(): List<FilterRuleEntity>

    @Query("SELECT * FROM filter_rules WHERE isEnabled = 1 ORDER BY priority ASC, id ASC")
    suspend fun getEnabledRulesSync(): List<FilterRuleEntity>

    @Query("SELECT COUNT(*) FROM filter_rules")
    fun getRuleCount(): Flow<Int>

    @Query("UPDATE filter_rules SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun setRuleEnabled(id: Long, isEnabled: Boolean)
}
