package com.example.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TriggerDao {
    @Query("SELECT * FROM triggers ORDER BY id DESC")
    fun getAllTriggers(): Flow<List<TriggerEntity>>

    @Query("SELECT * FROM triggers WHERE enabled = 1")
    suspend fun getEnabledTriggers(): List<TriggerEntity>

    @Query("SELECT * FROM triggers WHERE type = 'PHRASE_SHORTCUT' AND enabled = 1")
    suspend fun getEnabledShortcuts(): List<TriggerEntity>

    @Query("SELECT * FROM triggers WHERE id = :id")
    suspend fun getTriggerById(id: Long): TriggerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrigger(trigger: TriggerEntity): Long

    @Update
    suspend fun updateTrigger(trigger: TriggerEntity)

    @Delete
    suspend fun deleteTrigger(trigger: TriggerEntity)
}
