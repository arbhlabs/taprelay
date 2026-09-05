package com.arbhlabs.taprelay.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.arbhlabs.taprelay.TapRelayApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A tap on a widget tile.
 *
 * Not exported: only TapRelay's own `PendingIntent`s reach it, so nothing on the phone can ask
 * TapRelay to run somebody's routine.
 *
 * The order of work here is the whole reason a widget feels immediate:
 *
 *  1. write the running state and redraw - the tile changes within a frame of the finger landing;
 *  2. `goAsync()` and run the action through the same `ActionExecutor` an NFC tap uses;
 *  3. write the outcome and redraw again.
 *
 * The action is *not* run on a foreground service. A widget tap is short, user-initiated work
 * that completes in well under the window a manifest-declared receiver is given, and the executor
 * caps every step at twelve seconds and the whole thing at [OVERALL_TIMEOUT_MS] - so there is no
 * case where this receiver sits waiting on a dead socket. Keeping a service alive for a light
 * switch would be exactly the battery-vampire pattern the rest of TapRelay avoids.
 */
class WidgetActionReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RUN) return
        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        )
        val tagId = intent.getStringExtra(EXTRA_TAG_ID) ?: return
        val slot = intent.getIntExtra(EXTRA_SLOT, 0)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

        val appContext = context.applicationContext
        val store = WidgetStore(appContext)

        // Paint first. This is a synchronous SharedPreferences write and a broadcast, both of
        // which land long before any network call would have.
        store.setRunState(widgetId, slot, RunState.RUNNING, "")
        TapRelayWidget.refreshOne(appContext, widgetId)

        val pending = goAsync()
        scope.launch {
            try {
                val services = (appContext as TapRelayApplication).services
                val outcome = withTimeoutOrNull(OVERALL_TIMEOUT_MS) {
                    services.actionExecutor.executeAndAwait(tagId)
                }
                when {
                    outcome == null ->
                        store.setRunState(widgetId, slot, RunState.FAILED, "Took too long")
                    outcome.success ->
                        store.setRunState(widgetId, slot, RunState.DONE, shorten(outcome.summary))
                    else ->
                        store.setRunState(widgetId, slot, RunState.FAILED, shorten(outcome.summary))
                }
            } catch (e: Exception) {
                store.setRunState(widgetId, slot, RunState.FAILED, "Didn't work")
            } finally {
                TapRelayWidget.refreshOne(appContext, widgetId)
                pending.finish()
            }
        }
    }

    /**
     * A tile has room for a few words. The item's own name is already on the tile, so this keeps
     * only what happened to it.
     */
    private fun shorten(summary: String): String {
        val tail = summary.substringAfterLast(" • ", summary.substringAfter(": ", summary))
        return if (tail.length <= 34) tail else tail.take(33).trimEnd() + "…"
    }

    companion object {
        const val ACTION_RUN = "com.arbhlabs.taprelay.widget.RUN"
        const val EXTRA_TAG_ID = "tagId"
        const val EXTRA_SLOT = "slot"

        /**
         * Long enough for a full Magic Action whose every step has to time out, short enough that
         * the receiver always finishes well inside the window Android gives it.
         */
        private const val OVERALL_TIMEOUT_MS = 40_000L
    }
}
