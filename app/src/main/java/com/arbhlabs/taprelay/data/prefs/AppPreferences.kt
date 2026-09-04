package com.arbhlabs.taprelay.data.prefs

import android.content.Context
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appPrefs by preferencesDataStore(name = "app_prefs")

/**
 * How much room the Remote Mode face gives itself. The same three-step idea as the LastDose AOD:
 * one dial the owner can turn rather than a page of individual spacing controls.
 */
enum class AodDensity(
    val label: String,
    val clockSp: Int,
    val gap: Dp,
    val rowGap: Dp,
    val rowPadding: Dp,
    val sidePadding: Dp,
    val showDiagram: Boolean
) {
    COMPACT("Compact", 46, 10.dp, 6.dp, 7.dp, 18.dp, false),
    NORMAL("Normal", 68, 16.dp, 8.dp, 10.dp, 26.dp, true),
    LARGE("Large", 92, 24.dp, 12.dp, 15.dp, 30.dp, true);

    companion object {
        fun fromName(value: String?): AodDensity =
            runCatching { value?.let { valueOf(it) } }.getOrNull() ?: NORMAL
    }
}

class AppPreferences(private val context: Context) {
    private val onboardedKey = booleanPreferencesKey("onboarding_complete")
    private val controllerRumbleKey = booleanPreferencesKey("controller_rumble")
    private val phoneHapticsKey = booleanPreferencesKey("phone_haptics")
    private val remoteAutoDimKey = booleanPreferencesKey("remote_auto_dim")
    private val aodDensityKey = stringPreferencesKey("aod_density")

    val onboardingComplete: Flow<Boolean> =
        context.appPrefs.data.map { it[onboardedKey] ?: false }

    /** Rumble the pad itself when a button fires. On by default; pads without a motor ignore it. */
    val controllerRumble: Flow<Boolean> =
        context.appPrefs.data.map { it[controllerRumbleKey] ?: true }

    val phoneHaptics: Flow<Boolean> =
        context.appPrefs.data.map { it[phoneHapticsKey] ?: true }

    /** Whether Remote Mode fades to near-black when left alone. */
    val remoteAutoDim: Flow<Boolean> =
        context.appPrefs.data.map { it[remoteAutoDimKey] ?: true }

    val aodDensity: Flow<AodDensity> =
        context.appPrefs.data.map { AodDensity.fromName(it[aodDensityKey]) }

    suspend fun setAodDensity(value: AodDensity) {
        context.appPrefs.edit { it[aodDensityKey] = value.name }
    }

    suspend fun setOnboardingComplete(value: Boolean) {
        context.appPrefs.edit { it[onboardedKey] = value }
    }

    suspend fun setControllerRumble(value: Boolean) {
        context.appPrefs.edit { it[controllerRumbleKey] = value }
    }

    suspend fun setPhoneHaptics(value: Boolean) {
        context.appPrefs.edit { it[phoneHapticsKey] = value }
    }

    suspend fun setRemoteAutoDim(value: Boolean) {
        context.appPrefs.edit { it[remoteAutoDimKey] = value }
    }
}
