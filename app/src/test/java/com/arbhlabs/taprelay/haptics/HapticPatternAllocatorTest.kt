package com.arbhlabs.taprelay.haptics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticPatternAllocatorTest {
    private val allocator = HapticPatternAllocator()
    private val twoMotor = HapticRenderCapabilities(2, amplitudeControl = true)

    private fun descriptor(id: String, family: HapticActionClass = HapticActionClass.SMART_DEVICE, transition: HapticTransition = HapticTransition.ON) =
        HapticSemanticDescriptor(id, HapticActivationSource.GAMEPAD, "DPAD_LEFT", family, "item-$id", transition, HapticResult.SUCCESS)

    @Test fun assignmentIsDeterministicForTheSameMapping() {
        val descriptor = descriptor("left-lamp")
        assertEquals(
            allocator.allocate(descriptor, emptyList(), twoMotor).id,
            allocator.allocate(descriptor, emptyList(), twoMotor).id
        )
    }

    @Test fun allocatorMaximizesDistanceFromExistingFamilyAssignments() {
        val first = allocator.allocate(descriptor("one"), emptyList(), twoMotor)
        val second = allocator.allocate(descriptor("two"), listOf(first.id), twoMotor)
        assertNotEquals(first.id, second.id)
        val alternatives = CandidatePatterns.all.filter { it.family == HapticActionClass.SMART_DEVICE && it.id != first.id }
        assertTrue(allocator.distance(second, first, twoMotor) >= alternatives.minOf { allocator.distance(it, first, twoMotor) })
    }

    @Test fun semanticFamiliesRemainRecognizable() {
        assertTrue(allocator.allocate(descriptor("dose", HapticActionClass.LASTDOSE), emptyList(), twoMotor).id.startsWith("lastdose-double"))
        assertTrue(allocator.allocate(descriptor("macro", HapticActionClass.MACRO), emptyList(), twoMotor).id.startsWith("macro-"))
    }

    @Test fun capabilityDegradationKeepsLogicalSelectionStable() {
        val descriptor = descriptor("same")
        val four = allocator.allocate(descriptor, emptyList(), HapticRenderCapabilities(4, true))
        val one = allocator.allocate(descriptor, emptyList(), HapticRenderCapabilities(1, false))
        assertEquals(four.id, one.id)
    }

    @Test fun onAndOffEnvelopesAreDistinct() {
        val base = CandidatePatterns.all.first { it.id == "device-rise-0" }
        assertNotEquals(HapticEnvelope.RISING, HapticEnvelope.FALLING)
        assertTrue(base.channels.first().amplitudes.count { it > 0 } >= 2)
    }
}
