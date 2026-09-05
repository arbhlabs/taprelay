package com.arbhlabs.taprelay.ui.actions

import com.arbhlabs.taprelay.data.local.entity.TagEntity
import com.arbhlabs.taprelay.domain.model.ActionStep
import com.arbhlabs.taprelay.domain.model.ActionType
import com.arbhlabs.taprelay.domain.model.PhoneAction
import com.arbhlabs.taprelay.domain.model.powerIntent

/**
 * Starting points for a Magic Action.
 *
 * A template is a name, an icon and a *rule for picking from the items the owner already has* -
 * never a set of invented steps. If somebody has no air conditioner, the bedtime template does not
 * pretend to turn one off; it fills in the lights it can actually see and leaves the rest to them.
 * What comes out is an ordinary editable sequence, not a second kind of automation.
 */
data class MagicTemplate(
    val id: String,
    val name: String,
    val iconKey: String,
    val blurb: String,
    /** Picks the steps this template can honestly build from what the owner has. */
    val build: (List<TagEntity>) -> List<ActionStep>
)

object MagicActionTemplates {

    val ALL: List<MagicTemplate> = listOf(
        MagicTemplate(
            id = "bedtime",
            name = "Bedtime",
            iconKey = "room",
            blurb = "Everything off, phone quiet"
        ) { items ->
            buildList {
                addAll(items.turnOffCandidates().map { it.asStep() })
                items.phoneAction(PhoneAction.DND_ON)?.let { add(it.asStep()) }
            }
        },
        MagicTemplate(
            id = "gaming",
            name = "Gaming",
            iconKey = "switch",
            blurb = "Lights and gear for a session"
        ) { items ->
            items.deviceCandidates().take(3).map { it.asStep() }
        },
        MagicTemplate(
            id = "desk",
            name = "Desk Mode",
            iconKey = "lamp",
            blurb = "Desk light and focus"
        ) { items ->
            buildList {
                items.deviceCandidates().firstOrNull()?.let { add(it.asStep()) }
                items.phoneAction(PhoneAction.DND_ON)?.let { add(it.asStep()) }
            }
        },
        MagicTemplate(
            id = "movie",
            name = "Movie Time",
            iconKey = "scene",
            blurb = "Dim the room, then start"
        ) { items ->
            buildList {
                addAll(items.turnOffCandidates().take(2).map { it.asStep() })
                add(ActionStep.wait(500L))
                items.firstOrNull { it.isLaunch }?.let { add(it.asStep()) }
            }
        },
        MagicTemplate(
            id = "leaving",
            name = "Leaving Home",
            iconKey = "plug",
            blurb = "Shut everything down on the way out"
        ) { items ->
            items.turnOffCandidates().map { it.asStep() }
        },
        MagicTemplate(
            id = "lastdose",
            name = "Quick Log",
            iconKey = "lastdose",
            blurb = "Log to LastDose and confirm"
        ) { items ->
            items.filter { it.isLastDose }.take(2).map { it.asStep() }
        }
    )

    // ---------------------------------------------------------------- selection rules

    private fun List<TagEntity>.deviceCandidates(): List<TagEntity> =
        filter { !it.isNonDevice && it.enabled }

    /** Items that already end with something switched off - the honest half of a bedtime routine. */
    private fun List<TagEntity>.turnOffCandidates(): List<TagEntity> =
        deviceCandidates().filter {
            it.actionType.powerIntent() == ActionType.TURN_OFF || it.actionType == ActionType.TOGGLE
        }

    private fun List<TagEntity>.phoneAction(key: String): TagEntity? =
        firstOrNull { it.isPhone && it.deviceId == key }

    private fun TagEntity.asStep() = ActionStep.run(tagId, friendlyName)
}
