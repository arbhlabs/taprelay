package com.arbhlabs.taprelay

import com.arbhlabs.taprelay.domain.model.ActionType
import com.arbhlabs.taprelay.domain.model.Toggle
import org.junit.Assert.assertEquals
import org.junit.Test

class ToggleLogicTest {

    @Test fun turn_on_always_targets_on() {
        assertEquals(1, Toggle.target(ActionType.TURN_ON, 1))
        assertEquals(1, Toggle.target(ActionType.TURN_ON, 0))
    }

    @Test fun turn_off_always_targets_off() {
        assertEquals(0, Toggle.target(ActionType.TURN_OFF, 1))
        assertEquals(0, Toggle.target(ActionType.TURN_OFF, 0))
    }

    @Test fun toggle_inverts_last_known_state() {
        assertEquals(0, Toggle.target(ActionType.TOGGLE, 1))
        assertEquals(1, Toggle.target(ActionType.TOGGLE, 0))
    }

    @Test fun inverse_of_authoritative_state() {
        assertEquals(0, Toggle.inverseOf(1))
        assertEquals(1, Toggle.inverseOf(0))
    }
}
