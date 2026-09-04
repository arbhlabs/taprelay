package com.arbhlabs.taprelay.execution

import android.util.Log
import com.arbhlabs.taprelay.data.local.entity.TagEntity
import com.arbhlabs.taprelay.domain.model.ActionType
import com.arbhlabs.taprelay.domain.model.Brightness
import com.arbhlabs.taprelay.domain.model.LightPresets
import com.arbhlabs.taprelay.domain.model.TargetType
import com.arbhlabs.taprelay.domain.model.TapError
import com.arbhlabs.taprelay.domain.model.Toggle
import com.arbhlabs.taprelay.domain.model.TapException
import com.arbhlabs.taprelay.domain.provider.SmartHomeProvider
import com.arbhlabs.taprelay.domain.repository.TagRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val lastFired = HashMap<String, Long>()

    private companion object {
        /** Outcome logging for field diagnostics. Never records tag names or credentials. */
        const val TAG = "TapRelayExec"
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

    private suspend fun run(tagId: String, onFeedback: (TapFeedback) -> Unit) {
        val tag = tags.getTagById(tagId)
        if (tag == null) {
            haptics.vibrateError()
            onFeedback(TapFeedback(TapError.TAG_UNREGISTERED.message, isError = true))
            return
        }
        if (!tag.enabled) {
            haptics.vibrateError()
            onFeedback(TapFeedback("${tag.friendlyName} is turned off in the app.", isError = true))
            return
        }
        haptics.vibrateClick()

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
                withContext(Dispatchers.Main) {
                    haptics.vibrateSuccess()
                    onFeedback(TapFeedback("${tag.friendlyName} • Scene started", isError = false))
                }
            } catch (e: TapException) {
                haptics.vibrateError()
                onFeedback(TapFeedback(e.error.message, isError = true))
            } catch (e: Exception) {
                haptics.vibrateError()
                onFeedback(TapFeedback(TapError.UNKNOWN.message, isError = true))
            }
            return
        }

        val finalTarget: Int
        try {
            if (tag.actionType == ActionType.SET_BRIGHTNESS ||
                tag.actionType == ActionType.SET_COLOR ||
                tag.actionType == ActionType.SET_SCENE
            ) {
                finalTarget = Toggle.target(tag.actionType, tag.lastKnownState)
                onFeedback(TapFeedback("${tag.friendlyName} • ${settingLabel(tag)}", isError = false, pending = true))
            } else if (tag.actionType == ActionType.TOGGLE) {
                onFeedback(TapFeedback("${tag.friendlyName} • Updating…", isError = false, pending = true))
                val real = provider.getPowerState(tag.deviceId, tag.deviceSku)
                finalTarget = if (real != null) Toggle.inverseOf(real) else Toggle.target(tag.actionType, tag.lastKnownState)
                Log.i(TAG, "toggle provider=${tag.providerId} read=${real ?: "unknown"} sending=$finalTarget")
            } else {
                finalTarget = Toggle.target(tag.actionType, tag.lastKnownState)
                onFeedback(TapFeedback("${tag.friendlyName} • ${label(finalTarget)}", isError = false, pending = true))
            }

            // Every light in the group follows the primary's decision, so they move together
            // instead of drifting apart when one of them was changed elsewhere.
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
                    p.executeAction(
                        targetId = target.deviceId,
                        targetType = TargetType.DEVICE,
                        sku = target.sku,
                        action = tag.actionType,
                        targetState = finalTarget,
                        brightnessPercent = tag.brightnessPercent,
                        colorRgb = tag.colorRgb
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
            Log.i(
                TAG,
                "ok provider=${tag.providerId} action=${tag.actionType} state=$finalTarget " +
                    "lights=$succeeded/${targets.size}"
            )
            withContext(Dispatchers.Main) {
                haptics.vibrateSuccess()
                val outcome = outcomeLabel(tag, finalTarget)
                val text = if (succeeded < targets.size) {
                    "${tag.friendlyName} • $outcome ($succeeded of ${targets.size})"
                } else {
                    "${tag.friendlyName} • $outcome"
                }
                onFeedback(TapFeedback(text, isError = false))
            }
        } catch (e: TapException) {
            Log.w(TAG, "failed provider=${tag.providerId} error=${e.error.name}")
            revert(tag.tagId, tag.lastKnownState)
            haptics.vibrateError()
            onFeedback(TapFeedback(e.error.message, isError = true))
        } catch (e: Exception) {
            Log.w(TAG, "failed provider=${tag.providerId} error=unexpected")
            revert(tag.tagId, tag.lastKnownState)
            haptics.vibrateError()
            onFeedback(TapFeedback(TapError.UNKNOWN.message, isError = true))
        }
    }

    private suspend fun revert(tagId: String, previous: Int) =
        tags.updateStateAndTimestamp(tagId, previous, System.currentTimeMillis())

    private fun label(state: Int) = if (state == 1) "On" else "Off"

    /** What the tag is about to do, shown while the request is in flight. */
    private fun settingLabel(tag: TagEntity) = when (tag.actionType) {
        ActionType.SET_BRIGHTNESS ->
            "Brightness ${Brightness.clampPercent(tag.brightnessPercent ?: Brightness.DEFAULT_PERCENT)}%…"
        ActionType.SET_COLOR -> "${LightPresets.nameFor(tag.colorRgb ?: 0)}…"
        ActionType.SET_SCENE ->
            "${LightPresets.nameFor(tag.colorRgb ?: 0)} " +
                "${Brightness.clampPercent(tag.brightnessPercent ?: Brightness.DEFAULT_PERCENT)}%…"
        else -> "Updating…"
    }

    private fun outcomeLabel(tag: TagEntity, state: Int) = when (tag.actionType) {
        ActionType.SET_BRIGHTNESS ->
            "Brightness ${Brightness.clampPercent(tag.brightnessPercent ?: Brightness.DEFAULT_PERCENT)}%"
        ActionType.SET_COLOR -> LightPresets.nameFor(tag.colorRgb ?: 0)
        ActionType.SET_SCENE ->
            "${LightPresets.nameFor(tag.colorRgb ?: 0)} " +
                "${Brightness.clampPercent(tag.brightnessPercent ?: Brightness.DEFAULT_PERCENT)}%"
        else -> label(state)
    }
}
