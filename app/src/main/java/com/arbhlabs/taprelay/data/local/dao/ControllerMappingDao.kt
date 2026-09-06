package com.arbhlabs.taprelay.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.arbhlabs.taprelay.data.local.entity.ControllerMappingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ControllerMappingDao {

    @Query("SELECT * FROM controller_mappings ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ControllerMappingEntity>>

    @Query("SELECT * FROM controller_mappings WHERE controllerDescriptor = :descriptor OR controllerDescriptor = '*' ORDER BY createdAt DESC")
    fun observeForController(descriptor: String): Flow<List<ControllerMappingEntity>>

    @Query("SELECT * FROM controller_mappings WHERE (controllerDescriptor = :descriptor OR controllerDescriptor = '*') AND inputKey = :inputKey AND enabled = 1 LIMIT 1")
    suspend fun findActiveMapping(descriptor: String, inputKey: String): ControllerMappingEntity?

    /**
     * Distinct enabled input keys (single buttons and chords like "BUTTON_L1+BUTTON_A"). Read
     * synchronously off the main thread by the LastDose hand-off provider so LastDose can skip a
     * cross-process call for a button nobody mapped. Not a Flow: the caller re-reads on resume.
     */
    @Query("SELECT DISTINCT inputKey FROM controller_mappings WHERE enabled = 1")
    fun activeInputKeysBlocking(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mapping: ControllerMappingEntity): Long

    @Update
    suspend fun update(mapping: ControllerMappingEntity)

    @Query("DELETE FROM controller_mappings WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM controller_mappings WHERE tagId = :tagId")
    suspend fun deleteByTagId(tagId: String)
}
