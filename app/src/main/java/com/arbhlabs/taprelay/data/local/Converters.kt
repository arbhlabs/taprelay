package com.arbhlabs.taprelay.data.local

import androidx.room.TypeConverter
import com.arbhlabs.taprelay.domain.model.ActionType
import com.arbhlabs.taprelay.data.local.entity.PlaceTransition
import com.arbhlabs.taprelay.domain.model.ActivationMode
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
    fun activationModeToString(value: ActivationMode?): String? = value?.name

    @TypeConverter
    fun stringToActivationMode(value: String?): ActivationMode? =
        value?.let { runCatching { ActivationMode.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun targetTypeToString(value: TargetType): String = value.name

    @TypeConverter
    fun stringToTargetType(value: String): TargetType =
        runCatching { TargetType.valueOf(value) }.getOrDefault(TargetType.DEVICE)

    @TypeConverter
    fun placeTransitionToString(value: PlaceTransition): String = value.name

    @TypeConverter
    fun stringToPlaceTransition(value: String): PlaceTransition =
        runCatching { PlaceTransition.valueOf(value) }.getOrDefault(PlaceTransition.ARRIVE)

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
