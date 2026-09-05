package com.arbhlabs.taprelay.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** What one step of a Magic Action does. */
enum class StepKind {
    /** Runs another TapRelay item - a lamp, a scene, a LastDose log, a web request, anything. */
    RUN_ITEM,

    /** Does nothing for a moment, so a bulb has settled before the next step touches it. */
    WAIT
}

/**
 * One step of a Magic Action.
 *
 * A RUN_ITEM step is a *reference to an item the owner already made*, not a second copy of an
 * item's configuration. That is the whole design: the Magic Action engine has no idea what a Govee
 * bulb, a Sensibo unit, a LastDose log, a Home Assistant light or a web request is, because every
 * step re-enters the same `ActionExecutor.run()` a single tap would. Adding a target type makes
 * Magic Actions support it the same day, and there is exactly one place an action can go wrong.
 *
 * [label] is a display cache so a sequence reads sensibly before the referenced items load, and so
 * a step whose item was deleted can still say which one it was.
 */
@Serializable
data class ActionStep(
    val kind: StepKind = StepKind.RUN_ITEM,
    /** The item this step runs. Null for a [StepKind.WAIT]. */
    val tagId: String? = null,
    val label: String = "",
    /** How long a [StepKind.WAIT] pauses for. Ignored by every other kind. */
    val delayMs: Long = 0L
) {
    val isWait: Boolean get() = kind == StepKind.WAIT

    companion object {
        /** Beyond this a Magic Action stops being "physical input -> action -> done". */
        const val MAX_STEPS = 12

        /** Long enough for a bulb or an air conditioner to settle; short enough to still feel instant. */
        const val MAX_DELAY_MS = 10_000L

        /** The pauses worth offering as one tap each, in milliseconds. */
        val DELAY_PRESETS = listOf(200L, 500L, 1_000L, 2_000L, 5_000L)

        fun wait(delayMs: Long): ActionStep {
            val clamped = delayMs.coerceIn(0L, MAX_DELAY_MS)
            return ActionStep(kind = StepKind.WAIT, delayMs = clamped, label = describeDelay(clamped))
        }

        fun run(tagId: String, label: String) =
            ActionStep(kind = StepKind.RUN_ITEM, tagId = tagId, label = label)

        fun describeDelay(delayMs: Long): String =
            if (delayMs < 1000L) "Wait ${delayMs}ms" else "Wait ${trimZero(delayMs / 1000.0)}s"

        private fun trimZero(value: Double): String =
            if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

        private val json = Json { ignoreUnknownKeys = true }

        /** Unreadable or absent JSON reads as no steps, never as a crash on a tap. */
        fun decode(raw: String?): List<ActionStep> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                json.decodeFromString(ListSerializer(serializer()), raw)
            }.getOrDefault(emptyList())
        }

        fun encode(steps: List<ActionStep>): String =
            json.encodeToString(ListSerializer(serializer()), steps.take(MAX_STEPS))
    }
}
