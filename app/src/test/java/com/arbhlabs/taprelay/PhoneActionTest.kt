package com.arbhlabs.taprelay

import com.arbhlabs.taprelay.domain.model.PhoneAction
import com.arbhlabs.taprelay.domain.model.WebhookPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The "My phone" action set and the web presets: every action a picker can offer must have a
 * label, and only Do Not Disturb may ask for a permission.
 */
class PhoneActionTest {

    @Test fun every_action_has_a_real_label() {
        PhoneAction.ALL.forEach { key ->
            val label = PhoneAction.label(key)
            assertFalse("$key fell through to the default label", label == "Phone action")
            assertTrue(label.isNotBlank())
        }
    }

    @Test fun categories_cover_exactly_the_action_set() {
        val fromCategories = PhoneAction.CATEGORIES.flatMap { it.second }
        assertEquals(PhoneAction.ALL.toSet(), fromCategories.toSet())
        assertEquals("a category lists an action twice", fromCategories.size, fromCategories.toSet().size)
    }

    @Test fun only_do_not_disturb_needs_a_permission() {
        val needing = PhoneAction.ALL.filter { PhoneAction.needsDndAccess(it) }
        assertEquals(
            listOf(PhoneAction.DND_ON, PhoneAction.DND_OFF, PhoneAction.DND_TOGGLE).toSet(),
            needing.toSet()
        )
        // Media, volume and torch must never gate behind a system switch.
        assertFalse(PhoneAction.needsDndAccess(PhoneAction.MEDIA_PLAY_PAUSE))
        assertFalse(PhoneAction.needsDndAccess(PhoneAction.VOLUME_UP))
        assertFalse(PhoneAction.needsDndAccess(PhoneAction.FLASHLIGHT_TOGGLE))
    }

    @Test fun media_actions_are_the_first_thing_a_new_picker_offers() {
        assertEquals(PhoneAction.MEDIA_PLAY_PAUSE, PhoneAction.ALL.first())
        assertEquals("Music & media", PhoneAction.CATEGORIES.first().first)
    }

    @Test fun webhook_presets_are_distinct_and_reachable_shapes() {
        assertEquals(WebhookPreset.ALL.size, WebhookPreset.ALL.map { it.id }.toSet().size)
        WebhookPreset.ALL.forEach { preset ->
            assertTrue("${preset.id} hint is not https", preset.urlHint.startsWith("https://"))
            assertTrue("${preset.id} has no help line", preset.help.isNotBlank())
        }
    }
}
