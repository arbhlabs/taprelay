package com.arbhlabs.taprelay.execution

import android.util.Log
import com.arbhlabs.taprelay.data.local.entity.TagEntity
import com.arbhlabs.taprelay.domain.model.ActionType
import com.arbhlabs.taprelay.domain.model.Brightness
import com.arbhlabs.taprelay.domain.model.powerIntent
import com.arbhlabs.taprelay.domain.model.LightPresets
import com.arbhlabs.taprelay.domain.model.TargetType
import com.arbhlabs.taprelay.domain.model.TapError
import com.arbhlabs.taprelay.domain.model.Toggle
import com.arbhlabs.taprelay.domain.model.TapException
import com.arbhlabs.taprelay.domain.provider.SmartHomeProvider
import com.arbhlabs.taprelay.domain.repository.TagRepository
import com.arbhlabs.taprelay.data.local.dao.TapLogDao
import com.arbhlabs.taprelay.data.local.entity.TapLogEntity
import com.arbhlabs.taprelay.execution.lastdose.LastDoseClient
import com.arbhlabs.taprelay.execution.lastdose.LastDoseResult
import com.arbhlabs.taprelay.monetization.EntitlementRepository
import com.arbhlabs.taprelay.monetization.TapRelayProFeature
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime

data class TapFeedback(
    val title: String,
    val isError: Boolean,
    val pending: Boolean = false
)

/**
 * Resolves a scanned tag id to a mapping and executes its action with optimistic feedback.
 * Duplicate scans of the same tag within [debounceMs] are ignored.
 */
class ActionExecutor(
    private val tags: TagRepository,
    private val providers: () -> Map<String, SmartHomeProvider>,
    private val haptics: HapticsManager,
    private val debounceMs: Long = 1500L,
    private val tapLogDao: TapLogDao? = null,
    private val entitlements: EntitlementRepository? = null,
    /** Present only where LastDose logging is wired; null in tests and on the smart-home-only path. */
    private val lastDose: LastDoseClient? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val lastFired = HashMap<String, Long>()

    private companion object {
        /** Outcome logging for field diagnostics. Never records tag names or credentials. */
        const val TAG = "TapRelayExec"

        /** Not a SmartHomeProvider - just how a LastDose row is labelled in tap history. */
        const val LASTDOSE_PROVIDER_ID = "lastdose"
    }

    fun executeByTagId(tagId: String, onFeedback: (TapFeedback) -> Unit) {
        val now = System.currentTimeMillis()
        synchronized(lastFired) {
            val prev = lastFired[tagId]
            if (prev != null && now - prev < debounceMs) return
            lastFired[tagId] = now
        }
        scope.launch { run(tagId, onFeedback) }
    }

    /** 1-tap instant replay from diagnostics log (bypasses NFC debounce). */
    fun replay(tagId: String, onFeedback: (TapFeedback) -> Unit) {
        scope.launch { run(tagId, onFeedback) }
    }

    private suspend fun run(tagId: String, onFeedback: (TapFeedback) -> Unit) {
        val startTime = System.currentTimeMillis()
        val rawTag = tags.getTagById(tagId)
        if (rawTag == null) {
            haptics.vibrateError()
            onFeedback(TapFeedback(TapError.TAG_UNREGISTERED.message, isError = true))
            return
        }
        if (!rawTag.enabled) {
            haptics.vibrateError()
            onFeedback(TapFeedback("${rawTag.friendlyName} is turned off in the app.", isError = true))
            return
        }
        haptics.vibrateClick()

        // A LastDose item is not a device: no provider, no power state, no toggle. It leaves the
        // shared route here and rejoins it at the same feedback and tap-history the lamps use.
        if (rawTag.isLastDose) {
            runLastDoseLog(rawTag, startTime, onFeedback)
            return
        }

        // Pro: Context-aware time-of-day condition evaluation
        val tag = if (rawTag.timeConditionEnabled && (entitlements == null || entitlements.canAccess(TapRelayProFeature.TIME_OF_DAY_CONDITIONS))) {
            val localTime = LocalTime.now()
            val currentMinutes = localTime.hour * 60 + localTime.minute
            val startMinutes = (rawTag.startHour ?: 0) * 60 + (rawTag.startMinute ?: 0)
            val endMinutes = (rawTag.endHour ?: 24) * 60 + (rawTag.endMinute ?: 0)
            val inWindow = if (startMinutes <= endMinutes) {
                currentMinutes in startMinutes until endMinutes
            } else {
                currentMinutes >= startMinutes || currentMinutes < endMinutes
            }
            if (inWindow) {
                rawTag
            } else {
                val alt = rawTag.offActionType ?: ActionType.TURN_OFF
                rawTag.copy(actionType = alt, brightnessPercent = if (alt == ActionType.TURN_OFF) null else rawTag.brightnessPercent)
            }
        } else {
            rawTag
        }

        val provider = providers()[tag.providerId]
        if (provider == null) {
            haptics.vibrateError()
            onFeedback(TapFeedback(TapError.PROVIDER_UNAVAILABLE.message, isError = true))
            return
        }

        if (tag.targetType == TargetType.SCENE || tag.actionType == ActionType.RUN_SCENE) {
            onFeedback(TapFeedback("${tag.friendlyName} • Running scene…", isError = false, pending = true))
            try {
                provider.executeAction(
                    targetId = tag.deviceId,
                    targetType = TargetType.SCENE,
                    sku = tag.deviceSku,
                    action = ActionType.RUN_SCENE,
                    targetState = 1
                )
                tags.updateStateAndTimestamp(tag.tagId, 1, System.currentTimeMillis())
                val duration = System.currentTimeMillis() - startTime
                tapLogDao?.insert(
                    TapLogEntity(
                        tagId = tag.tagId,
                        tagName = tag.friendlyName,
                        providerId = tag.providerId,
                        actionDescription = "Scene started",
                        success = true,
                        durationMs = duration
                    )
                )
                withContext(Dispatchers.Main) {
                    haptics.vibrateSuccess()
                    onFeedback(TapFeedback("${tag.friendlyName} • Scene started", isError = false))
                }
            } catch (e: TapException) {
                val duration = System.currentTimeMillis() - startTime
                tapLogDao?.insert(
                    TapLogEntity(
                        tagId = tag.tagId,
                        tagName = tag.friendlyName,
                        providerId = tag.providerId,
                        actionDescription = "Scene failed",
                        success = false,
                        errorMessage = e.error.message,
                        durationMs = duration
                    )
                )
                haptics.vibrateError()
                onFeedback(TapFeedback(e.error.message, isError = true))
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime
                tapLogDao?.insert(
                    TapLogEntity(
                        tagId = tag.tagId,
                        tagName = tag.friendlyName,
                        providerId = tag.providerId,
                        actionDescription = "Scene failed",
                        success = false,
                        errorMessage = TapError.UNKNOWN.message,
                        durationMs = duration
                    )
                )
                haptics.vibrateError()
                onFeedback(TapFeedback(TapError.UNKNOWN.message, isError = true))
            }
            return
        }

        if (tag.actionType == ActionType.TOGGLE_FAN_SPEED) {
            onFeedback(TapFeedback("${tag.friendlyName} • Toggling fan…", isError = false, pending = true))
            try {
                val p = providers()[tag.providerId]
                val level = if (p is com.arbhlabs.taprelay.domain.provider.SensiboProvider) {
                    p.toggleFanSpeed(tag.deviceId)
                } else {
                    p?.setPower(tag.deviceId, tag.deviceSku, true)
                    "High"
                }
                val levelName = level.replaceFirstChar { it.uppercase() }
                val outcome = "Fan $levelName"
                tags.updateStateAndTimestamp(tag.tagId, 1, System.currentTimeMillis())
                val duration = System.currentTimeMillis() - startTime
                tapLogDao?.insert(
                    TapLogEntity(
                        tagId = tag.tagId,
                        tagName = tag.friendlyName,
                        providerId = tag.providerId,
                        actionDescription = outcome,
                        success = true,
                        durationMs = duration
                    )
                )
                withContext(Dispatchers.Main) {
                    haptics.vibrateSuccess()
                    onFeedback(TapFeedback("${tag.friendlyName} • $outcome", isError = false))
                }
            } catch (e: TapException) {
                val duration = System.currentTimeMillis() - startTime
                tapLogDao?.insert(
                    TapLogEntity(
                        tagId = tag.tagId,
                        tagName = tag.friendlyName,
                        providerId = tag.providerId,
                        actionDescription = "Fan toggle failed",
                        success = false,
                        errorMessage = e.error.message,
                        durationMs = duration
                    )
                )
                haptics.vibrateError()
                onFeedback(TapFeedback(e.error.message, isError = true))
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime
                tapLogDao?.insert(
                    TapLogEntity(
                        tagId = tag.tagId,
                        tagName = tag.friendlyName,
                        providerId = tag.providerId,
                        actionDescription = "Fan toggle failed",
                        success = false,
                        errorMessage = TapError.UNKNOWN.message,
                        durationMs = duration
                    )
                )
                haptics.vibrateError()
                onFeedback(TapFeedback(TapError.UNKNOWN.message, isError = true))
            }
            return
        }

        val finalTarget: Int
        try {
            val intent = tag.actionType.powerIntent()
            if (intent == ActionType.TOGGLE) {
                onFeedback(TapFeedback("${tag.friendlyName} • Updating…", isError = false, pending = true))
                val real = provider.getPowerState(tag.deviceId, tag.deviceSku)
                finalTarget = if (real != null) Toggle.inverseOf(real) else Toggle.target(intent, tag.lastKnownState)
                Log.i(TAG, "toggle provider=${tag.providerId} read=${real ?: "unknown"} sending=$finalTarget")
            } else {
                finalTarget = Toggle.target(intent, tag.lastKnownState)
                onFeedback(TapFeedback("${tag.friendlyName} • ${settingLabel(tag, finalTarget)}", isError = false, pending = true))
            }

            // Pro: Multi-target routine execution (or unified group)
            val targets = tag.allTargets
            var succeeded = 0
            var firstFailure: TapException? = null
            for (target in targets) {
                val p = providers()[target.providerId]
                if (p == null) {
                    firstFailure = firstFailure ?: TapException(TapError.PROVIDER_UNAVAILABLE)
                    continue
                }
                try {
                    val isRoutineStep = target.actionType != null &&
                        (entitlements == null || entitlements.canAccess(TapRelayProFeature.MULTI_DEVICE_ROUTINES))

                    val stepAction = if (isRoutineStep) target.actionType!! else tag.actionType
                    val stepBrightness = if (isRoutineStep) target.brightnessPercent else tag.brightnessPercent
                    val stepColor = if (isRoutineStep) target.colorRgb else tag.colorRgb
                    val stepFanLevel = if (isRoutineStep) target.fanLevel else tag.fanLevel
                    val stepState = if (isRoutineStep) {
                        Toggle.target(stepAction.powerIntent(), finalTarget)
                    } else {
                        finalTarget
                    }

                    p.executeAction(
                        targetId = target.deviceId,
                        targetType = TargetType.DEVICE,
                        sku = target.sku,
                        action = stepAction,
                        targetState = stepState,
                        brightnessPercent = stepBrightness,
                        colorRgb = stepColor,
                        fanLevel = stepFanLevel
                    )
                    succeeded++
                } catch (e: TapException) {
                    firstFailure = firstFailure ?: e
                } catch (e: Exception) {
                    firstFailure = firstFailure ?: TapException(TapError.UNKNOWN)
                }
            }

            if (succeeded == 0) throw firstFailure ?: TapException(TapError.UNKNOWN)

            tags.updateStateAndTimestamp(tag.tagId, finalTarget, System.currentTimeMillis())
            val duration = System.currentTimeMillis() - startTime
            val outcome = outcomeLabel(tag, finalTarget)
            tapLogDao?.insert(
                TapLogEntity(
                    tagId = tag.tagId,
                    tagName = tag.friendlyName,
                    providerId = tag.providerId,
                    actionDescription = outcome,
                    success = true,
                    durationMs = duration
                )
            )

            Log.i(
                TAG,
                "ok provider=${tag.providerId} action=${tag.actionType} state=$finalTarget " +
                    "targets=$succeeded/${targets.size} (${duration}ms)"
            )
            withContext(Dispatchers.Main) {
                haptics.vibrateSuccess()
                val text = if (succeeded < targets.size) {
                    "${tag.friendlyName} • $outcome ($succeeded of ${targets.size})"
                } else {
                    "${tag.friendlyName} • $outcome"
                }
                onFeedback(TapFeedback(text, isError = false))
            }
        } catch (e: TapException) {
            val duration = System.currentTimeMillis() - startTime
            tapLogDao?.insert(
                TapLogEntity(
                    tagId = tag.tagId,
                    tagName = tag.friendlyName,
                    providerId = tag.providerId,
                    actionDescription = "Failed",
                    success = false,
                    errorMessage = e.error.message,
                    durationMs = duration
                )
            )
            Log.w(TAG, "failed provider=${tag.providerId} error=${e.error.name}")
            revert(tag.tagId, tag.lastKnownState)
            haptics.vibrateError()
            onFeedback(TapFeedback(e.error.message, isError = true))
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            tapLogDao?.insert(
                TapLogEntity(
                    tagId = tag.tagId,
                    tagName = tag.friendlyName,
                    providerId = tag.providerId,
                    actionDescription = "Failed",
                    success = false,
                    errorMessage = TapError.UNKNOWN.message,
                    durationMs = duration
                )
            )
            Log.w(TAG, "failed provider=${tag.providerId} error=unexpected")
            revert(tag.tagId, tag.lastKnownState)
            haptics.vibrateError()
            onFeedback(TapFeedback(TapError.UNKNOWN.message, isError = true))
        }
    }

    /**
     * Writes one log into LastDose without LastDose ever appearing. The request id is derived from
     * the item and a coarse time slice, so a duplicated delivery of the same physical press is
     * recognised on the LastDose side and cannot produce a second entry.
     */
    private suspend fun runLastDoseLog(tag: TagEntity, startTime: Long, onFeedback: (TapFeedback) -> Unit) {
        val client = lastDose
        val itemId = tag.lastDoseItemId ?: 0L
        val display = tag.lastDoseItemName?.takeIf { it.isNotBlank() } ?: tag.friendlyName
        if (client == null || itemId <= 0L) {
            haptics.vibrateError()
            onFeedback(TapFeedback("That LastDose log is no longer set up.", isError = true))
            return
        }
        onFeedback(TapFeedback("$display • Logging…", isError = false, pending = true))

        val requestId = "${tag.tagId}:${startTime / 1000L}"
        val result = client.log(itemId, tag.lastDoseAmount, tag.lastDoseUnit, requestId)
        val duration = System.currentTimeMillis() - startTime

        val (text, isError) = when (result) {
            is LastDoseResult.Logged -> logSummary(display, result.amount, result.unit) to false
            // The log the owner asked for exists; a repeated delivery is still that one success.
            is LastDoseResult.Duplicate -> logSummary(display, tag.lastDoseAmount.orEmpty(), tag.lastDoseUnit.orEmpty()) to false
            is LastDoseResult.Failed -> result.message to true
        }

        tapLogDao?.insert(
            TapLogEntity(
                tagId = tag.tagId,
                tagName = tag.friendlyName,
                providerId = LASTDOSE_PROVIDER_ID,
                actionDescription = if (isError) "LastDose log failed" else "Logged to LastDose",
                success = !isError,
                errorMessage = if (isError) text else null,
                durationMs = duration
            )
        )
        if (!isError) tags.updateStateAndTimestamp(tag.tagId, 1, System.currentTimeMillis())
        Log.i(TAG, "lastdose ok=${!isError} (${duration}ms)")

        withContext(Dispatchers.Main) {
            if (isError) haptics.vibrateError() else haptics.vibrateSuccess()
            onFeedback(TapFeedback(text, isError = isError))
        }
    }

    /** "Bowl +1 logged" - the amount only when there is one, never the raw contract. */
    private fun logSummary(name: String, amount: String, unit: String): String {
        val qualifier = listOf(amount, unit).filter { it.isNotBlank() }.joinToString(" ")
        return if (qualifier.isBlank()) "$name logged" else "$name $qualifier logged"
    }

    private suspend fun revert(tagId: String, previous: Int) =
        tags.updateStateAndTimestamp(tagId, previous, System.currentTimeMillis())

    private fun label(state: Int) = if (state == 1) "On" else "Off"

    /** Describes whatever combination the tag carries, not a single fixed action. */
    private fun settingLabel(tag: TagEntity, state: Int) = outcomeLabel(tag, state) + "…"

    private fun outcomeLabel(tag: TagEntity, state: Int): String {
        if (tag.actionType == ActionType.TOGGLE_FAN_SPEED) return "Fan Low ↔ High"
        if (state != 1) return "Off"
        val parts = buildList {
            tag.fanLevel?.let { add("Fan ${it.replaceFirstChar { c -> c.uppercase() }}") }
            tag.colorRgb?.let { add(LightPresets.nameFor(it)) }
            tag.brightnessPercent?.let { add("${Brightness.clampPercent(it)}%") }
        }
        return if (parts.isEmpty()) "On" else "On • " + parts.joinToString(" ")
    }
}
