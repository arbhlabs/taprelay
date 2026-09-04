package com.arbhlabs.taprelay.data.local

import androidx.room.TypeConverter
import com.arbhlabs.taprelay.domain.model.ActionType
import com.arbhlabs.taprelay.domain.model.TagTarget
import com.arbhlabs.taprelay.domain.model.TargetType
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

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

    @TypeConverter
    fun targetsToString(value: List<TagTarget>): String =
        json.encodeToString(ListSerializer(TagTarget.serializer()), value)

    @TypeConverter
    fun stringToTargets(value: String?): List<TagTarget> {
        if (value.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(TagTarget.serializer()), value)
        }.getOrDefault(emptyList())
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
