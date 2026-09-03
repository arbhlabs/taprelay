package com.arbhlabs.taprelay.execution

import com.arbhlabs.taprelay.domain.model.ActionType
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

        val optimisticTarget = Toggle.target(tag.actionType, tag.lastKnownState)
        onFeedback(TapFeedback("${tag.friendlyName} • ${label(optimisticTarget)}", isError = false, pending = true))
        tags.updateStateAndTimestamp(tag.tagId, optimisticTarget, System.currentTimeMillis())

        val provider = providers()[tag.providerId]
        if (provider == null) {
            revert(tag.tagId, tag.lastKnownState)
            haptics.vibrateError()
            onFeedback(TapFeedback(TapError.PROVIDER_UNAVAILABLE.message, isError = true))
            return
        }

        try {
            val finalTarget = if (tag.actionType == ActionType.TOGGLE) {
                val real = runCatching { provider.getPowerState(tag.deviceId, tag.deviceSku) }.getOrNull()
                if (real != null) Toggle.inverseOf(real) else optimisticTarget
            } else optimisticTarget

            provider.setPower(tag.deviceId, tag.deviceSku, on = finalTarget == 1)
            tags.updateStateAndTimestamp(tag.tagId, finalTarget, System.currentTimeMillis())
            withContext(Dispatchers.Main) {
                haptics.vibrateSuccess()
                onFeedback(TapFeedback("${tag.friendlyName} • ${label(finalTarget)}", isError = false))
            }
        } catch (e: TapException) {
            revert(tag.tagId, tag.lastKnownState)
            haptics.vibrateError()
            onFeedback(TapFeedback(e.error.message, isError = true))
        } catch (e: Exception) {
            revert(tag.tagId, tag.lastKnownState)
            haptics.vibrateError()
            onFeedback(TapFeedback(TapError.UNKNOWN.message, isError = true))
        }
    }

    private suspend fun revert(tagId: String, previous: Int) =
        tags.updateStateAndTimestamp(tagId, previous, System.currentTimeMillis())

    private fun label(state: Int) = if (state == 1) "On" else "Off"
}
