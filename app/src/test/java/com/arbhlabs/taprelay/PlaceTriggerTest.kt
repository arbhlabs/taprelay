package com.arbhlabs.taprelay

import com.arbhlabs.taprelay.data.local.AppDatabase
import com.arbhlabs.taprelay.data.local.Converters
import com.arbhlabs.taprelay.data.local.entity.PlaceTransition
import com.arbhlabs.taprelay.data.local.entity.PlaceTriggerEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A place fires the wrong item, or fires twice, if this routing is wrong — and it runs while the
 * app is closed, where a mistake is invisible. So it is pinned down here.
 */
class PlaceTriggerTest {

    private fun place(
        transition: PlaceTransition,
        leaveTagId: String? = null
    ) = PlaceTriggerEntity(
        id = 7,
        name = "Home",
        latitude = 52.52,
        longitude = 13.405,
        transition = transition,
        tagId = "arrive-tag",
        leaveTagId = leaveTagId
    )

    @Test
    fun arrive_only_ignores_leaving() {
        val p = place(PlaceTransition.ARRIVE)
        assertEquals("arrive-tag", p.tagIdFor(arriving = true))
        assertNull(p.tagIdFor(arriving = false))
    }

    @Test
    fun leave_only_ignores_arriving() {
        val p = place(PlaceTransition.LEAVE)
        assertNull(p.tagIdFor(arriving = true))
        assertEquals("arrive-tag", p.tagIdFor(arriving = false))
    }

    @Test
    fun both_directions_can_drive_different_items() {
        val p = place(PlaceTransition.BOTH, leaveTagId = "leave-tag")
        assertEquals("arrive-tag", p.tagIdFor(arriving = true))
        assertEquals("leave-tag", p.tagIdFor(arriving = false))
    }

    @Test
    fun both_directions_fall_back_to_one_item() {
        val p = place(PlaceTransition.BOTH)
        assertEquals("arrive-tag", p.tagIdFor(arriving = true))
        assertEquals("arrive-tag", p.tagIdFor(arriving = false))
    }

    @Test
    fun geofence_id_round_trips_to_the_row_id() {
        val p = place(PlaceTransition.ARRIVE)
        assertEquals("taprelay-place-7", p.geofenceId)
        assertEquals(7L, p.geofenceId.removePrefix("taprelay-place-").toLong())
    }

    @Test
    fun transition_survives_the_database() {
        val converters = Converters()
        PlaceTransition.entries.forEach {
            assertEquals(it, converters.stringToPlaceTransition(converters.placeTransitionToString(it)))
        }
        // A value from a future release must not crash an older parser.
        assertEquals(PlaceTransition.ARRIVE, converters.stringToPlaceTransition("SOMETHING_NEW"))
    }

    @Test
    fun migration_8_9_versions() {
        assertEquals(8, AppDatabase.MIGRATION_8_9.startVersion)
        assertEquals(9, AppDatabase.MIGRATION_8_9.endVersion)
    }
}
