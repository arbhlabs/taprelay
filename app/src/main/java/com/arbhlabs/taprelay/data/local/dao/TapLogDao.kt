package com.arbhlabs.taprelay.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.arbhlabs.taprelay.data.local.entity.TapLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TapLogDao {
    @Insert
    suspend fun insert(log: TapLogEntity): Long

    @Query("SELECT * FROM tap_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 100): Flow<List<TapLogEntity>>

    @Query("DELETE FROM tap_history WHERE timestamp < :cutoff")
    suspend fun pruneOldLogs(cutoff: Long)

    @Query("DELETE FROM tap_history")
    suspend fun clearAll()
}
