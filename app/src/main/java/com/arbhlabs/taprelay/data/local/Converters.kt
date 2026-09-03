package com.arbhlabs.taprelay.data.local

import androidx.room.TypeConverter
import com.arbhlabs.taprelay.domain.model.ActionType

class Converters {
    @TypeConverter
    fun actionTypeToString(value: ActionType): String = value.name

    @TypeConverter
    fun stringToActionType(value: String): ActionType =
        runCatching { ActionType.valueOf(value) }.getOrDefault(ActionType.TOGGLE)
}
