package com.arbhlabs.taprelay.domain.model

import kotlinx.serialization.Serializable

/**
 * One light a tag drives. A tag always has a primary target stored in its own columns;
 * any further lights it also controls are stored as a list of these.
 */
@Serializable
data class TagTarget(
    val providerId: String,
    val deviceId: String,
    val sku: String,
    val name: String = ""
)
