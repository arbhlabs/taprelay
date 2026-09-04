package com.arbhlabs.taprelay.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
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
    val activationMode: ActivationMode? = null
) {
    /** What a trigger does with this item, with the pre-0.1.1 default filled in. */
    val activation: ActivationMode
        get() = activationMode ?: ActivationMode.EXECUTE

    /** Every light this tag drives, primary first. */
    val allTargets: List<TagTarget>
        get() = listOf(TagTarget(providerId, deviceId, deviceSku, friendlyName)) + additionalTargets
}
