package com.arbhlabs.taprelay.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Diagnostic record of a physical NFC tap execution.
 * Tracks execution duration, outcome, and error state for troubleshooting.
 */
@Entity(tableName = "tap_history")
data class TapLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tagId: String,
    val tagName: String,
    val providerId: String,
    val actionDescription: String,
    val success: Boolean,
    val errorMessage: String? = null,
    val durationMs: Long,
    val timestamp: Long = System.currentTimeMillis(),
    /** The wearer's live pulse when this ran (via LastDose), or null when there was no fresh reading. */
    val heartRateBpm: Int? = null
)
