package com.arbhlabs.taprelay.haptics

import android.content.Context

interface HapticSignatureRepository {
    fun assignedPatternId(mappingId: String, capabilityKey: String): String?
    fun assignments(capabilityKey: String): Map<String, String>
    fun save(mappingId: String, capabilityKey: String, patternId: String)
    fun reset()
}

class SharedPreferencesHapticSignatureRepository(context: Context) : HapticSignatureRepository {
    private val prefs = context.getSharedPreferences("haptic_signatures", Context.MODE_PRIVATE)

    private fun key(mappingId: String, capabilityKey: String) = "$capabilityKey|$mappingId"

    override fun assignedPatternId(mappingId: String, capabilityKey: String): String? =
        prefs.getString(key(mappingId, capabilityKey), null)

    override fun assignments(capabilityKey: String): Map<String, String> = prefs.all.mapNotNull { (key, value) ->
        val prefix = "$capabilityKey|"
        if (!key.startsWith(prefix) || value !is String) null else key.removePrefix(prefix) to value
    }.toMap()

    override fun save(mappingId: String, capabilityKey: String, patternId: String) {
        prefs.edit().putString(key(mappingId, capabilityKey), patternId).apply()
    }

    override fun reset() { prefs.edit().clear().apply() }
}
