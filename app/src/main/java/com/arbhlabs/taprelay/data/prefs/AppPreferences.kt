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
import com.arbhlabs.taprelay.controller.AdjustmentConfig
import com.arbhlabs.taprelay.controller.AdjustmentStick

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
    private val automaticHapticSignaturesKey = booleanPreferencesKey("automatic_haptic_signatures")
    private val remoteAutoDimKey = booleanPreferencesKey("remote_auto_dim")
    private val aodDensityKey = stringPreferencesKey("aod_density")
    private val aodFavouritesKey = stringPreferencesKey("aod_favourites")
    private val aodShowClockKey = booleanPreferencesKey("aod_show_clock")
    private val aodConfirmKey = booleanPreferencesKey("aod_confirm_actions")
    private val aodMonochromeKey = booleanPreferencesKey("aod_monochrome")
    private val adjustmentEnabledKey = booleanPreferencesKey("post_adjustment_enabled")
    private val adjustmentStickKey = stringPreferencesKey("post_adjustment_stick")
    private val adjustmentDurationKey = stringPreferencesKey("post_adjustment_duration_ms")
    private val adjustmentDeadZoneKey = stringPreferencesKey("post_adjustment_dead_zone")

    val postActivationAdjustment: Flow<AdjustmentConfig> =
        context.appPrefs.data.map { prefs ->
            AdjustmentConfig(
                enabled = prefs[adjustmentEnabledKey] ?: false,
                stick = runCatching { AdjustmentStick.valueOf(prefs[adjustmentStickKey] ?: "RIGHT") }
                    .getOrDefault(AdjustmentStick.RIGHT),
                durationMs = prefs[adjustmentDurationKey]?.toLongOrNull() ?: 8_000L,
                deadZone = prefs[adjustmentDeadZoneKey]?.toFloatOrNull() ?: 0.22f
            )
        }

    suspend fun setPostActivationAdjustment(config: AdjustmentConfig) {
        context.appPrefs.edit {
            it[adjustmentEnabledKey] = config.enabled
            it[adjustmentStickKey] = config.stick.name
            it[adjustmentDurationKey] = config.safeDurationMs.toString()
            it[adjustmentDeadZoneKey] = config.safeDeadZone.toString()
        }
    }

    val onboardingComplete: Flow<Boolean> =
        context.appPrefs.data.map { it[onboardedKey] ?: false }

    /** Rumble the pad itself when a button fires. On by default; pads without a motor ignore it. */
    val controllerRumble: Flow<Boolean> =
        context.appPrefs.data.map { it[controllerRumbleKey] ?: true }

    val phoneHaptics: Flow<Boolean> =
        context.appPrefs.data.map { it[phoneHapticsKey] ?: true }

    /** ARBH Labs' semantic, persistent action feedback. Enabled by default. */
    val automaticHapticSignatures: Flow<Boolean> =
        context.appPrefs.data.map { it[automaticHapticSignaturesKey] ?: true }

    /** Whether Remote Mode fades to near-black when left alone. */
    val remoteAutoDim: Flow<Boolean> =
        context.appPrefs.data.map { it[remoteAutoDimKey] ?: true }

    val aodDensity: Flow<AodDensity> =
        context.appPrefs.data.map { AodDensity.fromName(it[aodDensityKey]) }

    suspend fun setAodDensity(value: AodDensity) {
        context.appPrefs.edit { it[aodDensityKey] = value.name }
    }

    /**
     * The items the always-on face offers, in the order they are shown. Stored as ids rather than
     * a flag on the item, so reordering never rewrites the tags table and a deleted item simply
     * stops appearing.
     */
    val aodFavourites: Flow<List<String>> =
        context.appPrefs.data.map { prefs ->
            prefs[aodFavouritesKey]?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()
        }

    suspend fun setAodFavourites(ids: List<String>) {
        context.appPrefs.edit { it[aodFavouritesKey] = ids.joinToString("\n") }
    }

    val aodShowClock: Flow<Boolean> =
        context.appPrefs.data.map { it[aodShowClockKey] ?: true }

    suspend fun setAodShowClock(value: Boolean) {
        context.appPrefs.edit { it[aodShowClockKey] = value }
    }

    /**
     * Whether a tap on the always-on face asks before it runs. On by default: the face is designed
     * to sit propped up on a desk, where a sleeve brushing the glass must not turn the lights off.
     */
    val aodConfirmActions: Flow<Boolean> =
        context.appPrefs.data.map { it[aodConfirmKey] ?: true }

    suspend fun setAodConfirmActions(value: Boolean) {
        context.appPrefs.edit { it[aodConfirmKey] = value }
    }

    /** Drops the accent on the always-on face for a pure white-on-black look. */
    val aodMonochrome: Flow<Boolean> =
        context.appPrefs.data.map { it[aodMonochromeKey] ?: false }

    suspend fun setAodMonochrome(value: Boolean) {
        context.appPrefs.edit { it[aodMonochromeKey] = value }
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

    suspend fun setAutomaticHapticSignatures(value: Boolean) {
        context.appPrefs.edit { it[automaticHapticSignaturesKey] = value }
    }

    suspend fun setRemoteAutoDim(value: Boolean) {
        context.appPrefs.edit { it[remoteAutoDimKey] = value }
    }
}
