package com.arbhlabs.taprelay

import com.arbhlabs.taprelay.data.local.Converters
import com.arbhlabs.taprelay.domain.model.TargetType
import org.junit.Assert.assertEquals
import org.junit.Test

class TargetTypeMigrationTest {

    private val converters = Converters()

    @Test
    fun targetType_conversions() {
        assertEquals("DEVICE", converters.targetTypeToString(TargetType.DEVICE))
        assertEquals("SCENE", converters.targetTypeToString(TargetType.SCENE))

        assertEquals(TargetType.DEVICE, converters.stringToTargetType("DEVICE"))
        assertEquals(TargetType.SCENE, converters.stringToTargetType("SCENE"))
        // Fallback safety
        assertEquals(TargetType.DEVICE, converters.stringToTargetType("UNKNOWN_VALUE"))
    }
}
