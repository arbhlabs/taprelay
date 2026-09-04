package com.arbhlabs.taprelay.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.arbhlabs.taprelay.domain.model.ActivationMode

/**
 * Local mapping between a game controller input (or chord) and a TapRelay smart-home action (tagId).
 * [controllerDescriptor] can be a specific hardware descriptor or "*" for universal mappings across any gamepad.
 */
@Entity(
    tableName = "controller_mappings",
    indices = [
        Index(value = ["controllerDescriptor", "inputKey"], unique = true)
    ]
)
data class ControllerMappingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val controllerDescriptor: String,
    val controllerName: String,
    val inputKey: String,
    val inputLabel: String,
    val tagId: String,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    /**
     * Per-trigger override of the item's own activation mode. Null means "whatever the
     * item is set to", so one button can open Quick Controls for a lamp another button executes.
     */
    val activationMode: ActivationMode? = null
)
