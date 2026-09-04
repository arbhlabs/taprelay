package com.arbhlabs.taprelay.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.arbhlabs.taprelay.data.local.entity.PlaceTriggerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceTriggerDao {

    @Query("SELECT * FROM place_triggers ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PlaceTriggerEntity>>

    @Query("SELECT * FROM place_triggers WHERE enabled = 1")
    suspend fun getEnabled(): List<PlaceTriggerEntity>

    @Query("SELECT * FROM place_triggers WHERE id = :id")
    suspend fun getById(id: Long): PlaceTriggerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(trigger: PlaceTriggerEntity): Long

    @Update
    suspend fun update(trigger: PlaceTriggerEntity)

    @Delete
    suspend fun delete(trigger: PlaceTriggerEntity)

    @Query("UPDATE place_triggers SET lastFiredAt = :at WHERE id = :id")
    suspend fun markFired(id: Long, at: Long)

    @Query("DELETE FROM place_triggers WHERE tagId = :tagId OR leaveTagId = :tagId")
    suspend fun deleteForTag(tagId: String)
}
