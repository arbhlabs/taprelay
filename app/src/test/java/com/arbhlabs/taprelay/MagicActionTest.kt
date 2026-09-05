package com.arbhlabs.taprelay

import com.arbhlabs.taprelay.domain.model.ActionStep
import com.arbhlabs.taprelay.domain.model.StepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A Magic Action is stored as JSON in one column, so the two things that can silently break it are
 * an unreadable payload turning a tap into a crash, and a step count that grows without bound.
 */
class MagicActionTest {

    @Test fun round_trips_a_sequence_in_order() {
        val steps = listOf(
            ActionStep.run("tag-a", "Desk Lamp"),
            ActionStep.wait(500L),
            ActionStep.run("tag-b", "Air")
        )
        val decoded = ActionStep.decode(ActionStep.encode(steps))
        assertEquals(3, decoded.size)
        assertEquals(listOf("tag-a", null, "tag-b"), decoded.map { it.tagId })
        assertEquals(StepKind.WAIT, decoded[1].kind)
        assertEquals(500L, decoded[1].delayMs)
    }

    @Test fun unreadable_json_reads_as_no_steps_rather_than_throwing() {
        assertTrue(ActionStep.decode("not json at all").isEmpty())
        assertTrue(ActionStep.decode("").isEmpty())
        assertTrue(ActionStep.decode(null).isEmpty())
    }

    @Test fun a_sequence_cannot_grow_past_the_cap() {
        val tooMany = (1..40).map { ActionStep.run("tag-$it", "Item $it") }
        assertEquals(ActionStep.MAX_STEPS, ActionStep.decode(ActionStep.encode(tooMany)).size)
    }

    @Test fun a_delay_is_clamped_to_something_a_person_would_wait_for() {
        assertEquals(ActionStep.MAX_DELAY_MS, ActionStep.wait(10.days()).delayMs)
        assertEquals(0L, ActionStep.wait(-5_000L).delayMs)
    }

    @Test fun a_delay_reads_in_the_unit_that_suits_it() {
        assertEquals("Wait 200ms", ActionStep.describeDelay(200L))
        assertEquals("Wait 1s", ActionStep.describeDelay(1_000L))
        assertEquals("Wait 1.5s", ActionStep.describeDelay(1_500L))
    }

    /** A step whose item was deleted keeps the name it was added under, so it can be named. */
    @Test fun a_step_remembers_the_name_it_was_added_under() {
        val decoded = ActionStep.decode(ActionStep.encode(listOf(ActionStep.run("gone", "Bedside Lamp"))))
        assertEquals("Bedside Lamp", decoded.single().label)
    }

    private fun Int.days() = this * 24L * 60L * 60L * 1000L
}
