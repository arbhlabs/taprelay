package com.arbhlabs.taprelay.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import com.arbhlabs.taprelay.TapRelayApplication
import com.arbhlabs.taprelay.data.local.entity.TagEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Draws whichever TapRelay widgets are on the home screen.
 *
 * There is no polling and no `updatePeriodMillis`: a widget is redrawn when something actually
 * happened to it - it was placed or resized, an action ran, the app was replaced, the phone
 * rebooted - and otherwise the process stays asleep. A TapRelay widget shows what the owner
 * configured and what it last did, and neither of those changes on a timer.
 */
abstract class TapRelayWidget : AppWidgetProvider() {

    /** Off the main thread: a redraw reads the items out of Room. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    protected abstract fun render(
        context: Context,
        widgetId: Int,
        store: WidgetStore,
        tags: List<TagEntity?>
    ): android.widget.RemoteViews

    protected abstract val slotCount: Int

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        redraw(context, manager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        // Nothing to do beyond a redraw: the launcher swaps between our size variants itself,
        // so resizing costs no work in this process at all on Android 12 and up.
        redraw(context, manager, intArrayOf(appWidgetId))
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val store = WidgetStore(context)
        appWidgetIds.forEach { store.clear(it) }
    }

    private fun redraw(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        val appContext = context.applicationContext
        val pending = goAsync()
        scope.launch {
            try {
                val store = WidgetStore(appContext)
                val services = (appContext as TapRelayApplication).services
                val all = services.tagRepository.getTagsOnce()
                for (id in ids) {
                    val targets = store.targets(id)
                    val tags = (0 until slotCount).map { slot ->
                        targets.getOrNull(slot)?.let { tagId -> all.firstOrNull { it.tagId == tagId } }
                    }
                    val views = render(appContext, id, store, tags)
                    withContext(Dispatchers.Main) { manager.updateAppWidget(id, views) }
                }
            } catch (e: Exception) {
                // A widget that cannot be drawn must never take the launcher's process down.
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** Redraws every TapRelay widget of both kinds. Safe to call from any thread. */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            listOf(ActionKeyWidget::class.java, RemoteWidget::class.java).forEach { provider ->
                val ids = manager.getAppWidgetIds(
                    android.content.ComponentName(context.applicationContext, provider)
                )
                if (ids.isNotEmpty()) {
                    context.sendBroadcast(
                        Intent(context, provider).apply {
                            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                        }
                    )
                }
            }
        }

        /** Redraws just the one widget a tap came from, which is the common case. */
        fun refreshOne(context: Context, widgetId: Int) {
            val manager = AppWidgetManager.getInstance(context)
            listOf(ActionKeyWidget::class.java, RemoteWidget::class.java).forEach { provider ->
                val ids = manager.getAppWidgetIds(
                    android.content.ComponentName(context.applicationContext, provider)
                )
                if (ids.contains(widgetId)) {
                    context.sendBroadcast(
                        Intent(context, provider).apply {
                            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(widgetId))
                        }
                    )
                }
            }
        }
    }
}

/** One action, one tap. */
class ActionKeyWidget : TapRelayWidget() {
    override val slotCount = 1

    override fun render(
        context: Context,
        widgetId: Int,
        store: WidgetStore,
        tags: List<TagEntity?>
    ) = WidgetRenderer.actionKey(context, widgetId, tags.firstOrNull(), store.runState(widgetId))
}

/** Up to six actions, laid out for whatever size the widget has been dragged to. */
class RemoteWidget : TapRelayWidget() {
    override val slotCount = WidgetRenderer.MAX_REMOTE_TILES

    override fun render(
        context: Context,
        widgetId: Int,
        store: WidgetStore,
        tags: List<TagEntity?>
    ) = WidgetRenderer.remote(context, widgetId, tags, store.runState(widgetId))
}
