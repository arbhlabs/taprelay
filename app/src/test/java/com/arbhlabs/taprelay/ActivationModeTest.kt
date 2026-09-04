package com.arbhlabs.taprelay

import com.arbhlabs.taprelay.data.local.AppDatabase
import com.arbhlabs.taprelay.data.local.Converters
import com.arbhlabs.taprelay.data.local.entity.ControllerMappingEntity
import com.arbhlabs.taprelay.data.local.entity.TagEntity
import com.arbhlabs.taprelay.domain.model.ActionType
import com.arbhlabs.taprelay.domain.model.ActivationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * trigger -> item -> activation mode. The rule has to hold the same way for every trigger type,
 * and tags written before 0.1.1 must keep executing exactly as they did.
 */
class ActivationModeTest {

    private fun tag(mode: ActivationMode? = null) = TagEntity(
        tagId = "t1",
        friendlyName = "Desk Lamp",
        iconKey = "lamp",
        providerId = "govee",
        deviceId = "d1",
        deviceSku = "H6008",
        actionType = ActionType.TOGGLE,
        activationMode = mode
    )

    private fun mapping(mode: ActivationMode? = null) = ControllerMappingEntity(
        controllerDescriptor = "*",
        controllerName = "Xbox Wireless Controller",
        inputKey = "BUTTON_A",
        inputLabel = "A Button",
        tagId = "t1",
        activationMode = mode
    )

    @Test
    fun a_tag_written_before_0_1_1_still_executes() {
        assertNull(tag().activationMode)
        assertEquals(ActivationMode.EXECUTE, tag().activation)
    }

    @Test
    fun item_mode_applies_when_the_trigger_has_no_opinion() {
        assertEquals(
            ActivationMode.QUICK_CONTROLS,
            ActivationMode.resolve(mapping().activationMode, tag(ActivationMode.QUICK_CONTROLS).activationMode)
        )
    }

    @Test
    fun a_trigger_override_beats_the_item() {
        assertEquals(
            ActivationMode.OPEN_ITEM,
            ActivationMode.resolve(
                mapping(ActivationMode.OPEN_ITEM).activationMode,
                tag(ActivationMode.EXECUTE).activationMode
            )
        )
        // Two buttons on the same controller can do different things with one lamp.
        assertEquals(
            ActivationMode.EXECUTE,
            ActivationMode.resolve(
                mapping(ActivationMode.EXECUTE).activationMode,
                tag(ActivationMode.QUICK_CONTROLS).activationMode
            )
        )
    }

    @Test
    fun nothing_configured_anywhere_executes() {
        assertEquals(ActivationMode.EXECUTE, ActivationMode.resolve(null, null))
    }

    @Test
    fun modes_survive_a_round_trip_through_the_database() {
        val converters = Converters()
        ActivationMode.entries.forEach { mode ->
            val stored = converters.activationModeToString(mode)
            assertEquals(mode, converters.stringToActivationMode(stored))
        }
        assertNull(converters.activationModeToString(null))
        assertNull(converters.stringToActivationMode(null))
        // A value written by a future release must not crash an older parser.
        assertNull(converters.stringToActivationMode("SOMETHING_NEW"))
    }

    @Test
    fun migration_7_8_versions() {
        assertEquals(7, AppDatabase.MIGRATION_7_8.startVersion)
        assertEquals(8, AppDatabase.MIGRATION_7_8.endVersion)
    }
}
