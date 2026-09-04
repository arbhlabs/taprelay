package com.arbhlabs.taprelay.trigger

import com.arbhlabs.taprelay.domain.model.ActivationMode
import com.arbhlabs.taprelay.domain.repository.TagRepository
import com.arbhlabs.taprelay.execution.ActionExecutor
import com.arbhlabs.taprelay.execution.TapFeedback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where a trigger came from. New trigger types join this list; the routing below does not change. */
enum class TriggerSource { NFC, CONTROLLER, IN_APP }

/**
 * How an item is put in front of the user when its activation mode is not EXECUTE.
 * Each surface supplies its own: the app shows a sheet in place, a background NFC tap
 * starts a small activity.
 */
interface ActivationPresenter {
    fun openItem(tagId: String)
    fun openQuickControls(tagId: String)
}

/**
 * The one place a trigger becomes an outcome:
 *
 *     trigger -> item -> activation mode -> execute / open item / Quick Controls
 *
 * Every trigger type routes through here, so an activation mode set once applies to NFC taps,
 * controller buttons, in-app taps and anything added later.
 */
class TriggerRouter(
    private val tags: TagRepository,
    private val executor: ActionExecutor,
    private val presentDebounceMs: Long = 1200L,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
) {
    private val lastPresented = HashMap<String, Long>()

    fun fire(
        tagId: String,
        override: ActivationMode? = null,
        source: TriggerSource = TriggerSource.IN_APP,
        presenter: ActivationPresenter? = null,
        onFeedback: (TapFeedback) -> Unit
    ) {
        scope.launch {
            val tag = withContext(Dispatchers.IO) { tags.getTagById(tagId) }
            // An unknown or disabled tag has one error path already: let the executor own it.
            if (tag == null || !tag.enabled) {
                executor.executeByTagId(tagId, onFeedback)
                return@launch
            }
            when (val mode = ActivationMode.resolve(override, tag.activationMode)) {
                ActivationMode.EXECUTE -> executor.executeByTagId(tagId, onFeedback)
                ActivationMode.OPEN_ITEM,
                ActivationMode.QUICK_CONTROLS -> {
                    if (presenter == null) {
                        // Nothing can show a surface here, so honour the intent as best we can.
                        executor.executeByTagId(tagId, onFeedback)
                        return@launch
                    }
                    if (!allowPresent(tagId)) return@launch
                    if (mode == ActivationMode.OPEN_ITEM) presenter.openItem(tagId)
                    else presenter.openQuickControls(tagId)
                }
            }
        }
    }

    /** The same guard the executor applies to actions, so one tap never opens two surfaces. */
    private fun allowPresent(tagId: String): Boolean {
        val now = System.currentTimeMillis()
        synchronized(lastPresented) {
            val prev = lastPresented[tagId]
            if (prev != null && now - prev < presentDebounceMs) return false
            lastPresented[tagId] = now
        }
        return true
    }
}
