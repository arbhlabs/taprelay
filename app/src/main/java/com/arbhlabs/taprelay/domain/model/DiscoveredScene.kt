package com.arbhlabs.taprelay.domain.model

/** A smart-home scene (e.g. "Bedtime", "Movie Mode") surfaced to the user by friendly name. */
data class DiscoveredScene(
    val sceneId: String,
    val name: String,
    val providerId: String
)
