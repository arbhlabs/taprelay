package com.arbhlabs.taprelay.domain.repository

import com.arbhlabs.taprelay.data.local.dao.TagDao
import com.arbhlabs.taprelay.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow

class TagRepository(private val tagDao: TagDao) {

    fun getAllTags(): Flow<List<TagEntity>> = tagDao.getAllTags()

    suspend fun getTagsOnce(): List<TagEntity> = tagDao.getTagsOnce()

    suspend fun getTagById(tagId: String): TagEntity? = tagDao.getTagById(tagId)

    suspend fun upsert(tag: TagEntity) = tagDao.insertTag(tag)

    suspend fun update(tag: TagEntity) = tagDao.updateTag(tag.copy(modifiedAt = System.currentTimeMillis()))

    suspend fun updateStateAndTimestamp(tagId: String, newState: Int, timestamp: Long) =
        tagDao.updateStateAndTimestamp(tagId, newState, timestamp)

    suspend fun delete(tag: TagEntity) = tagDao.deleteTag(tag)
}
