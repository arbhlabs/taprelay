package com.arbhlabs.taprelay.data.local.dao

import androidx.room.*
import com.arbhlabs.taprelay.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY createdAt DESC")
    fun getAllTags(): Flow<List<TagEntity>>

    /** A single read rather than a flow, for the widget path where nothing is observing. */
    @Query("SELECT * FROM tags ORDER BY createdAt DESC")
    suspend fun getTagsOnce(): List<TagEntity>

    @Query("SELECT * FROM tags WHERE tagId = :tagId LIMIT 1")
    suspend fun getTagById(tagId: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTag(tag: TagEntity)

    @Update
    suspend fun updateTag(tag: TagEntity)

    @Query("UPDATE tags SET lastKnownState = :newState, lastTriggeredAt = :timestamp WHERE tagId = :tagId")
    suspend fun updateStateAndTimestamp(tagId: String, newState: Int, timestamp: Long)

    @Delete
    suspend fun deleteTag(tag: TagEntity)
}
