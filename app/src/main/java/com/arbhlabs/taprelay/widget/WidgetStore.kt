package com.arbhlabs.taprelay.widget

import android.content.Context
import android.content.SharedPreferences

/**
 * What each placed widget is pointed at, and what it last did.
 *
 * Plain `SharedPreferences` rather than DataStore on purpose: a widget is rendered from a
 * `BroadcastReceiver` that may be the only thing alive in the process, and this has to be readable
 * synchronously without starting a coroutine or waiting on a flow. It holds no user content -
 * only widget ids and the item ids they point at.
 */
class WidgetStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("taprelay_widget_prefs", Context.MODE_PRIVATE)

    /** The item a single-action widget runs, or the ordered items a Remote widget shows. */
    fun setTargets(widgetId: Int, tagIds: List<String>) {
        prefs.edit().putString(key(widgetId), tagIds.joinToString(SEPARATOR)).apply()
    }

    fun targets(widgetId: Int): List<String> =
        prefs.getString(key(widgetId), null)
            ?.split(SEPARATOR)
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    fun clear(widgetId: Int) {
        prefs.edit()
            .remove(key(widgetId))
            .remove(stateKey(widgetId))
            .remove(stateAtKey(widgetId))
            .remove(stateSlotKey(widgetId))
            .remove(stateTextKey(widgetId))
            .apply()
    }

    /**
     * The transient "just ran" state a widget shows for a few seconds.
     *
     * Persisted rather than kept in memory because the process that ran the action is very often
     * killed before the launcher next asks for a redraw - which is exactly the case where a widget
     * would otherwise snap back to looking untouched.
     */
    fun setRunState(widgetId: Int, slot: Int, state: RunState, text: String) {
        prefs.edit()
            .putString(stateKey(widgetId), state.name)
            .putLong(stateAtKey(widgetId), System.currentTimeMillis())
            .putInt(stateSlotKey(widgetId), slot)
            .putString(stateTextKey(widgetId), text)
            .apply()
    }

    fun runState(widgetId: Int): RunSnapshot? {
        val name = prefs.getString(stateKey(widgetId), null) ?: return null
        val state = runCatching { RunState.valueOf(name) }.getOrNull() ?: return null
        val at = prefs.getLong(stateAtKey(widgetId), 0L)
        // A stale result must not sit on the home screen for the rest of the day.
        if (state != RunState.RUNNING && System.currentTimeMillis() - at > RESULT_TTL_MS) return null
        return RunSnapshot(
            state = state,
            slot = prefs.getInt(stateSlotKey(widgetId), 0),
            text = prefs.getString(stateTextKey(widgetId), null).orEmpty(),
            at = at
        )
    }

    fun clearRunState(widgetId: Int) {
        prefs.edit()
            .remove(stateKey(widgetId))
            .remove(stateAtKey(widgetId))
            .remove(stateSlotKey(widgetId))
            .remove(stateTextKey(widgetId))
            .apply()
    }

    private fun key(id: Int) = "targets_$id"
    private fun stateKey(id: Int) = "state_$id"
    private fun stateAtKey(id: Int) = "state_at_$id"
    private fun stateSlotKey(id: Int) = "state_slot_$id"
    private fun stateTextKey(id: Int) = "state_text_$id"

    companion object {
        private const val SEPARATOR = "|"

        /** How long a tick or a warning stays on the tile before it goes back to normal. */
        const val RESULT_TTL_MS = 6_000L
    }
}

enum class RunState { RUNNING, DONE, FAILED }

data class RunSnapshot(
    val state: RunState,
    val slot: Int,
    val text: String,
    val at: Long
)
