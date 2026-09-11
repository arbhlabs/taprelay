package com.arbhlabs.taprelay.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.arbhlabs.taprelay.data.local.entity.TapLogEntity
import kotlinx.coroutines.flow.Flow

/** The newest successful run of one item, plus how many times it ran since [since]. */
data class TapLogSummary(
    val tagId: String,
    val lastAt: Long,
    val lastHeartRate: Int?,
    val countSince: Int
)

@Dao
interface TapLogDao {
    @Insert
    suspend fun insert(log: TapLogEntity): Long

    @Query("SELECT * FROM tap_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 100): Flow<List<TapLogEntity>>

    /**
     * What the always-on face shows under each favourite. Only successful runs count: a failed
     * attempt did not happen as far as "when did I last do this" is concerned.
     */
    @Query(
        """
        SELECT t.tagId AS tagId, t.timestamp AS lastAt, t.heartRateBpm AS lastHeartRate,
            (SELECT COUNT(*) FROM tap_history c
                WHERE c.tagId = t.tagId AND c.success = 1 AND c.timestamp >= :since) AS countSince
        FROM tap_history t
        WHERE t.success = 1 AND t.tagId IN (:tagIds)
            AND t.id = (SELECT m.id FROM tap_history m
                WHERE m.tagId = t.tagId AND m.success = 1
                ORDER BY m.timestamp DESC, m.id DESC LIMIT 1)
        """
    )
    fun summaries(tagIds: List<String>, since: Long): Flow<List<TapLogSummary>>

    @Query("DELETE FROM tap_history WHERE timestamp < :cutoff")
    suspend fun pruneOldLogs(cutoff: Long)

    @Query("DELETE FROM tap_history")
    suspend fun clearAll()
}
