package com.arbhlabs.taprelay.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.arbhlabs.taprelay.domain.model.ActionStep
import com.arbhlabs.taprelay.domain.model.ActionType
import com.arbhlabs.taprelay.domain.model.ActivationMode
import com.arbhlabs.taprelay.domain.model.TagTarget
import com.arbhlabs.taprelay.domain.model.TargetType

/**
 * Local mapping between an opaque physical tag identifier and a smart-home action.
 * The physical NFC tag only ever stores the [tagId]; everything else lives here on-device
 * so the target device or action can be changed without rewriting the sticker.
 */
@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey val tagId: String,
    val friendlyName: String,
    val iconKey: String,
    val providerId: String,
    val deviceId: String,
    val deviceSku: String,
    val actionType: ActionType,
    val targetType: TargetType = TargetType.DEVICE,
    /** Only set when [actionType] is SET_BRIGHTNESS. 1-100. */
    val brightnessPercent: Int? = null,
    /** Only set when [actionType] is SET_COLOR. Packed 0xRRGGBB. */
    val colorRgb: Int? = null,
    /**
     * Further lights this one tag also drives, beyond the primary target above.
     * They all follow the primary's state so the group moves together.
     */
    val additionalTargets: List<TagTarget> = emptyList(),
    val enabled: Boolean = true,
    val lastKnownState: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val lastTriggeredAt: Long? = null,
    /** Pro: Context-aware time-of-day condition window. */
    val timeConditionEnabled: Boolean = false,
    val startHour: Int? = null,
    val startMinute: Int? = null,
    val endHour: Int? = null,
    val endMinute: Int? = null,
    val offActionType: ActionType? = null,
    val fanLevel: String? = null,
    /**
     * What a trigger does when it resolves to this item. Null reads as
     * [ActivationMode.EXECUTE], which is what every tag written before 0.1.1 meant.
     */
    val activationMode: ActivationMode? = null,
    /**
     * Set only when [targetType] is [TargetType.LASTDOSE_LOG]: which LastDose log this item writes
     * to and how much. Nullable so every tag saved before 0.1.4 keeps behaving exactly as it did.
     */
    val lastDoseItemId: Long? = null,
    val lastDoseItemName: String? = null,
    val lastDoseAmount: String? = null,
    val lastDoseUnit: String? = null,
    /**
     * Set only when [targetType] is [TargetType.WEBHOOK]. The credential itself is *not* here:
     * [webhookSecretHeader] names the header whose value is stored encrypted in
     * `SecureKeyStorage`, so a tags row that is backed up, logged or read out of the database
     * never carries somebody's key.
     */
    val webhookUrl: String? = null,
    val webhookMethod: String? = null,
    val webhookHeadersJson: String? = null,
    val webhookBody: String? = null,
    val webhookSecretHeader: String? = null,
    /**
     * Set only when [targetType] is [TargetType.MAGIC_ACTION]: a JSON array of
     * [com.arbhlabs.taprelay.domain.model.ActionStep], each one referencing another item by id.
     * The column keeps its 0.2.0 name so a database written by an early build still reads.
     */
    val actionChainJson: String? = null
) {
    /** True when this item logs to LastDose rather than driving a device. */
    val isLastDose: Boolean
        get() = targetType == TargetType.LASTDOSE_LOG

    /** True when this item sends a web request rather than driving a device. */
    val isWebhook: Boolean
        get() = targetType == TargetType.WEBHOOK

    /** True when this item opens an app or a link. */
    val isLaunch: Boolean
        get() = targetType == TargetType.LAUNCH

    /** True when this item changes something about the phone itself. */
    val isPhone: Boolean
        get() = targetType == TargetType.PHONE

    val isPcRelay: Boolean
        get() = targetType == TargetType.PC_RELAY

    /** True when this item is a Magic Action: several other items, in order. */
    val isMagicAction: Boolean
        get() = targetType == TargetType.MAGIC_ACTION

    /**
     * Items that are not a smart-home device: no provider, no power state, no colour, no Quick
     * Controls. Screens that list lamps use this to leave them out; the executor uses it to route.
     */
    val isNonDevice: Boolean
        get() = isLastDose || isWebhook || isLaunch || isPhone || isPcRelay || isMagicAction

    /** The steps of a Magic Action, in order. Empty for anything that is not one. */
    val magicSteps: List<ActionStep>
        get() = ActionStep.decode(actionChainJson)

    /** What a trigger does with this item, with the pre-0.1.1 default filled in. */
    val activation: ActivationMode
        get() = activationMode ?: ActivationMode.EXECUTE

    /** Every light this tag drives, primary first. */
    val allTargets: List<TagTarget>
        get() = listOf(TagTarget(providerId, deviceId, deviceSku, friendlyName)) + additionalTargets
}
