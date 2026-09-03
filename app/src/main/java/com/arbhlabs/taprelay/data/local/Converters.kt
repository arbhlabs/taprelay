package com.arbhlabs.taprelay.data.local

import androidx.room.TypeConverter
import com.arbhlabs.taprelay.domain.model.ActionType
import com.arbhlabs.taprelay.domain.model.TargetType

class Converters {
    @TypeConverter
    fun actionTypeToString(value: ActionType): String = value.name

    @TypeConverter
    fun stringToActionType(value: String): ActionType =
        runCatching { ActionType.valueOf(value) }.getOrDefault(ActionType.TOGGLE)

    @TypeConverter
    fun targetTypeToString(value: TargetType): String = value.name

    @TypeConverter
    fun stringToTargetType(value: String): TargetType =
        runCatching { TargetType.valueOf(value) }.getOrDefault(TargetType.DEVICE)
}
