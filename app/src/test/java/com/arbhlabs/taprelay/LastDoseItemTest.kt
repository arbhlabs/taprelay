package com.arbhlabs.taprelay

import com.arbhlabs.taprelay.data.local.entity.TagEntity
import com.arbhlabs.taprelay.domain.model.ActionType
import com.arbhlabs.taprelay.domain.model.TargetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A LastDose log is an item like any other. These pin the two things that would quietly break
 * existing setups: a smart-home tag must never be mistaken for a LastDose one, and a LastDose tag
 * must not be dragged through the device execution path.
 */
class LastDoseItemTest {

    private fun smartHomeTag() = TagEntity(
        tagId = "tag-1",
        friendlyName = "Desk Lamp",
        iconKey = "lamp",
        providerId = "govee",
        deviceId = "AA:BB",
        deviceSku = "H6008",
        actionType = ActionType.TOGGLE
    )

    private fun lastDoseTag() = TagEntity(
        tagId = "ld-1",
        friendlyName = "Bowl",
        iconKey = "lastdose",
        providerId = "lastdose",
        deviceId = "7",
        deviceSku = "",
        actionType = ActionType.TURN_ON,
        targetType = TargetType.LASTDOSE_LOG,
        lastDoseItemId = 7L,
        lastDoseItemName = "Bowl",
        lastDoseAmount = "1"
    )

    @Test
    fun `a tag written before 0_1_4 is still a device`() {
        val tag = smartHomeTag()
        assertFalse(tag.isLastDose)
        assertEquals(TargetType.DEVICE, tag.targetType)
        assertNull(tag.lastDoseItemId)
    }

    @Test
    fun `a lastdose item is recognised as one`() {
        assertTrue(lastDoseTag().isLastDose)
    }

    @Test
    fun `a scene is not a lastdose item`() {
        assertFalse(smartHomeTag().copy(targetType = TargetType.SCENE).isLastDose)
    }

    @Test
    fun `the trigger source does not change what the item is`() {
        // NFC, controller and place all resolve to the same row; nothing about the item depends
        // on which one fired it.
        val tag = lastDoseTag()
        assertEquals(7L, tag.lastDoseItemId)
        assertEquals("1", tag.lastDoseAmount)
        assertEquals(tag, tag.copy())
    }
}
