package com.arbhlabs.taprelay.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Which crossing of a place's edge should fire the action. */
enum class PlaceTransition {
    ARRIVE,
    LEAVE,
    BOTH;

    val label: String
        get() = when (this) {
            ARRIVE -> "When I arrive"
            LEAVE -> "When I leave"
            BOTH -> "Arriving and leaving"
        }
}

/**
 * A place that drives an item — "turn the desk lamp off when I leave home".
 *
 * The circle is stored here rather than in Play Services so it can be rebuilt after a reboot,
 * after Play Services is updated, or when location permission is granted later.
 */
@Entity(tableName = "place_triggers")
data class PlaceTriggerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    /** Metres. Android is unreliable below about 100 m, so the UI does not offer less. */
    val radiusMeters: Float = 150f,
    val transition: PlaceTransition = PlaceTransition.ARRIVE,
    /** The item this place drives. */
    val tagId: String,
    /** Fired only when leaving, if the trigger covers both directions. */
    val leaveTagId: String? = null,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastFiredAt: Long? = null
) {
    /** The stable id handed to Play Services for this circle. */
    val geofenceId: String get() = "taprelay-place-$id"

    /**
     * Which item this crossing drives, or null when the trigger does not cover that direction.
     * A two-way place falls back to the arrive item when no separate leave item was chosen.
     */
    fun tagIdFor(arriving: Boolean): String? = when (transition) {
        PlaceTransition.ARRIVE -> if (arriving) tagId else null
        PlaceTransition.LEAVE -> if (arriving) null else tagId
        PlaceTransition.BOTH -> if (arriving) tagId else (leaveTagId ?: tagId)
    }
}
