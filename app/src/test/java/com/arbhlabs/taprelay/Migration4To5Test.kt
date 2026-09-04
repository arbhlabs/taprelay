package com.arbhlabs.taprelay

import com.arbhlabs.taprelay.data.local.AppDatabase
import com.arbhlabs.taprelay.domain.model.ActionType
import com.arbhlabs.taprelay.domain.model.TagTarget
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Migration4To5Test {

    @Test
    fun migration_4_5_versions() {
        assertEquals(4, AppDatabase.MIGRATION_4_5.startVersion)
        assertEquals(5, AppDatabase.MIGRATION_4_5.endVersion)
    }

    @Test
    fun tagTarget_routine_serialization() {
        val targets = listOf(
            TagTarget(
                providerId = "govee",
                deviceId = "dev_1",
                sku = "H6100",
                name = "Desk Lamp",
                actionType = ActionType.TURN_ON,
                brightnessPercent = 80,
                colorRgb = 0xFF0000
            ),
            TagTarget(
                providerId = "sensibo",
                deviceId = "pod_pure",
                sku = "pure",
                name = "Air Purifier",
                actionType = ActionType.TURN_ON,
                brightnessPercent = 50
            )
        )
        val json = Json.encodeToString(targets)
        val decoded = Json.decodeFromString<List<TagTarget>>(json)

        assertEquals(2, decoded.size)
        assertEquals("dev_1", decoded[0].deviceId)
        assertEquals(ActionType.TURN_ON, decoded[0].actionType)
        assertEquals(80, decoded[0].brightnessPercent)
        assertEquals(0xFF0000, decoded[0].colorRgb)

        assertEquals("sensibo", decoded[1].providerId)
        assertEquals(50, decoded[1].brightnessPercent)
    }
}
