package com.arbhlabs.taprelay

import com.arbhlabs.taprelay.data.local.Converters
import com.arbhlabs.taprelay.data.local.entity.TagEntity
import com.arbhlabs.taprelay.domain.model.ActionType
import com.arbhlabs.taprelay.domain.model.TagTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** One tag can drive several lights; they all follow the primary's decision. */
class MultiTargetTagTest {

    private fun tag(extras: List<TagTarget>) = TagEntity(
        tagId = "tag-1",
        friendlyName = "Living room",
        iconKey = "lamp",
        providerId = "tuya_cloud",
        deviceId = "corner",
        deviceSku = "switch_led",
        actionType = ActionType.TOGGLE,
        additionalTargets = extras
    )

    @Test
    fun a_tag_with_no_extras_still_has_exactly_one_target() {
        val targets = tag(emptyList()).allTargets
        assertEquals(1, targets.size)
        assertEquals("corner", targets.single().deviceId)
        assertEquals("switch_led", targets.single().sku)
        assertEquals("tuya_cloud", targets.single().providerId)
    }

    @Test
    fun the_primary_light_always_comes_first() {
        val targets = tag(
            listOf(
                TagTarget("govee_cloud", "desk", "H6003", "Desk Lamp"),
                TagTarget("tuya_cloud", "accent", "switch_led", "Accent Light Top")
            )
        ).allTargets

        assertEquals(3, targets.size)
        assertEquals("corner", targets[0].deviceId)
        assertEquals(listOf("corner", "desk", "accent"), targets.map { it.deviceId })
    }

    /** A group may mix providers, so each target carries its own provider id. */
    @Test
    fun a_group_can_mix_providers() {
        val targets = tag(listOf(TagTarget("govee_cloud", "desk", "H6003", "Desk Lamp"))).allTargets
        assertEquals(setOf("tuya_cloud", "govee_cloud"), targets.map { it.providerId }.toSet())
    }

    @Test
    fun extra_targets_survive_a_database_round_trip() {
        val converters = Converters()
        val extras = listOf(
            TagTarget("govee_cloud", "desk", "H6003", "Desk Lamp"),
            TagTarget("tuya_cloud", "accent", "switch_led", "Accent Light Top")
        )
        val restored = converters.stringToTargets(converters.targetsToString(extras))
        assertEquals(extras, restored)
    }

    @Test
    fun a_missing_or_corrupt_column_reads_as_no_extra_lights() {
        val converters = Converters()
        assertTrue(converters.stringToTargets(null).isEmpty())
        assertTrue(converters.stringToTargets("").isEmpty())
        assertTrue(converters.stringToTargets("not json").isEmpty())
        assertTrue(converters.stringToTargets("[]").isEmpty())
    }
}
