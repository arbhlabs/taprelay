package com.arbhlabs.taprelay

import com.arbhlabs.taprelay.pc.PcRelayAction
import com.arbhlabs.taprelay.pc.PcRelayProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PcRelayProtocolTest {
    @Test
    fun actionRoundTripKeepsOwnershipAndEventId() {
        val raw = PcRelayProtocol.encodeAction(
            PcRelayProtocol.ActionRequest(
                eventId = "evt-42",
                ownerId = "phone-a",
                action = PcRelayAction.MEDIA_PLAY_PAUSE
            )
        )
        val decoded = PcRelayProtocol.json.decodeFromString<PcRelayProtocol.ActionRequest>(raw)
        assertEquals(PcRelayProtocol.CURRENT_VERSION, decoded.protocol)
        assertEquals("evt-42", decoded.eventId)
        assertEquals("phone-a", decoded.ownerId)
        assertEquals(PcRelayAction.MEDIA_PLAY_PAUSE, decoded.action)
    }

    @Test
    fun unknownFieldsAreIgnoredForForwardCompatibility() {
        val response = PcRelayProtocol.decodeAction(
            """{"protocol":1,"eventId":"e","accepted":true,"futureField":"ignored"}"""
        )
        assertTrue(response.accepted)
    }
}
