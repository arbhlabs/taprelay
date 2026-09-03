package com.arbhlabs.taprelay.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.arbhlabs.taprelay.domain.model.ActionType
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
    val enabled: Boolean = true,
    val lastKnownState: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val lastTriggeredAt: Long? = null
)
