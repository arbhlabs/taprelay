package com.arbhlabs.taprelay.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.SizeF
import android.widget.RemoteViews
import com.arbhlabs.taprelay.R
import com.arbhlabs.taprelay.data.local.entity.TagEntity
import com.arbhlabs.taprelay.domain.model.ItemLabels

/**
 * Turns items into `RemoteViews`.
 *
 * Everything about how a TapRelay widget looks lives here, and nothing about how an action runs
 * does. The providers below decide *when* to draw; this decides *what*.
 *
 * Two things make these feel fast rather than merely work:
 *
 *  - **Multi-size layouts.** On Android 12 and up the launcher is handed one `RemoteViews` per
 *    breakpoint and swaps between them itself while the widget is being dragged and resized. No
 *    IPC to our process, no frame where the old layout is stretched, no clipping.
 *  - **The tap paints before the work starts.** `WidgetActionReceiver` writes the running state
 *    and redraws before it does anything asynchronous, so the tile changes within a frame of the
 *    finger landing rather than when a cloud API answers.
 */
object WidgetRenderer {

    /** The Remote widget never shows more than this, whatever the owner configured. */
    const val MAX_REMOTE_TILES = 6

    private val TILE_IDS = intArrayOf(
        R.id.tile_0, R.id.tile_1, R.id.tile_2, R.id.tile_3, R.id.tile_4, R.id.tile_5
    )
    private val TILE_ICON_IDS = intArrayOf(
        R.id.tile_icon_0, R.id.tile_icon_1, R.id.tile_icon_2,
        R.id.tile_icon_3, R.id.tile_icon_4, R.id.tile_icon_5
    )
    private val TILE_NAME_IDS = intArrayOf(
        R.id.tile_name_0, R.id.tile_name_1, R.id.tile_name_2,
        R.id.tile_name_3, R.id.tile_name_4, R.id.tile_name_5
    )

    // ---------------------------------------------------------------- Action Key

    fun actionKey(
        context: Context,
        widgetId: Int,
        tag: TagEntity?,
        run: RunSnapshot?
    ): RemoteViews {
        fun build(layout: Int, detailed: Boolean): RemoteViews =
            RemoteViews(context.packageName, layout).apply {
                if (tag == null) {
                    setTextViewText(R.id.key_name, context.getString(R.string.widget_default_name))
                    if (detailed) {
                        setTextViewText(R.id.key_detail, context.getString(R.string.widget_default_detail))
                    }
                    setImageViewResource(R.id.key_icon, R.drawable.ic_w_add)
                    setOnClickPendingIntent(R.id.widget_root, configureIntent(context, widgetId))
                    return@apply
                }

                setTextViewText(R.id.key_name, tag.friendlyName)
                setImageViewResource(R.id.key_icon, iconFor(tag))
                if (detailed) {
                    setTextViewText(R.id.key_detail, detailFor(tag, run))
                }
                if (detailed) {
                    when (run?.state) {
                        RunState.DONE -> {
                            setViewVisibility(R.id.key_status, android.view.View.VISIBLE)
                            setImageViewResource(R.id.key_status, R.drawable.ic_w_check)
                        }
                        RunState.FAILED -> {
                            setViewVisibility(R.id.key_status, android.view.View.VISIBLE)
                            setImageViewResource(R.id.key_status, R.drawable.ic_w_warning)
                        }
                        else -> setViewVisibility(R.id.key_status, android.view.View.GONE)
                    }
                }
                setInt(R.id.widget_root, "setBackgroundResource", backgroundFor(run))
                setOnClickPendingIntent(
                    R.id.widget_root,
                    runIntent(context, widgetId, tag.tagId, slot = 0)
                )
                setContentDescription(R.id.widget_root, describe(tag, run))
            }

        val compact = build(R.layout.widget_key_compact, detailed = false)
        val normal = build(R.layout.widget_key_normal, detailed = true)
        val large = build(R.layout.widget_key_large, detailed = true)

        return responsive(
            fallback = normal,
            sizes = mapOf(
                SizeF(56f, 40f) to compact,
                SizeF(110f, 40f) to normal,
                SizeF(110f, 110f) to large
            )
        )
    }

    // ---------------------------------------------------------------- Remote

    fun remote(
        context: Context,
        widgetId: Int,
        tags: List<TagEntity?>,
        run: RunSnapshot?
    ): RemoteViews {
        fun build(layout: Int, slots: Int): RemoteViews =
            RemoteViews(context.packageName, layout).apply {
                setTextViewText(R.id.remote_status, statusFor(run, context))
                for (slot in 0 until slots) {
                    val tag = tags.getOrNull(slot)
                    val tileId = TILE_IDS[slot]
                    if (tag == null) {
                        setTextViewText(TILE_NAME_IDS[slot], context.getString(R.string.widget_default_detail))
                        setImageViewResource(TILE_ICON_IDS[slot], R.drawable.ic_w_add)
                        setInt(tileId, "setBackgroundResource", R.drawable.widget_tile)
                        setOnClickPendingIntent(tileId, configureIntent(context, widgetId))
                        setContentDescription(tileId, "Empty. Tap to choose an action.")
                        continue
                    }
                    setTextViewText(TILE_NAME_IDS[slot], tag.friendlyName)
                    setImageViewResource(TILE_ICON_IDS[slot], iconFor(tag))
                    val slotRun = run?.takeIf { it.slot == slot }
                    setInt(tileId, "setBackgroundResource", backgroundFor(slotRun))
                    setOnClickPendingIntent(tileId, runIntent(context, widgetId, tag.tagId, slot))
                    setContentDescription(tileId, describe(tag, slotRun))
                }
            }

        val short = build(R.layout.widget_remote_short, slots = 2)
        val normal = build(R.layout.widget_remote_normal, slots = 4)
        val tall = build(R.layout.widget_remote_tall, slots = 6)

        return responsive(
            fallback = normal,
            sizes = mapOf(
                SizeF(250f, 55f) to short,
                SizeF(250f, 110f) to normal,
                SizeF(250f, 200f) to tall
            )
        )
    }

    // ---------------------------------------------------------------- shared

    /**
     * Android 12 and up hands the launcher every breakpoint at once. Below that there is only one
     * layout to give, so the default size is the honest choice - it is the size the widget is
     * placed at.
     */
    private fun responsive(fallback: RemoteViews, sizes: Map<SizeF, RemoteViews>): RemoteViews =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) RemoteViews(sizes) else fallback

    private fun backgroundFor(run: RunSnapshot?): Int = when (run?.state) {
        RunState.RUNNING, RunState.DONE -> R.drawable.widget_tile_active
        RunState.FAILED -> R.drawable.widget_tile_error
        else -> R.drawable.widget_tile
    }

    private fun statusFor(run: RunSnapshot?, context: Context): CharSequence = when (run?.state) {
        RunState.RUNNING -> "Running…"
        RunState.DONE -> run.text.ifBlank { "Done" }
        RunState.FAILED -> run.text.ifBlank { "Didn't work" }
        else -> context.getString(R.string.widget_remote_ready)
    }

    private fun detailFor(tag: TagEntity, run: RunSnapshot?): CharSequence = when (run?.state) {
        RunState.RUNNING -> "Running…"
        RunState.DONE -> run.text.ifBlank { "Done" }
        RunState.FAILED -> run.text.ifBlank { "Didn't work" }
        else -> subtitleFor(tag)
    }

    private fun describe(tag: TagEntity, run: RunSnapshot?): String = when (run?.state) {
        RunState.RUNNING -> "${tag.friendlyName}. Running."
        RunState.DONE -> "${tag.friendlyName}. ${run.text.ifBlank { "Done" }}."
        RunState.FAILED -> "${tag.friendlyName}. ${run.text.ifBlank { "Did not work" }}."
        else -> "${tag.friendlyName}. ${subtitleFor(tag)}. Tap to run."
    }

    /** Same wording as the always-on face, so one item reads the same everywhere. */
    fun subtitleFor(tag: TagEntity): String = ItemLabels.summary(tag)

    private fun iconFor(tag: TagEntity): Int = when (tag.iconKey) {
        "room" -> R.drawable.ic_w_room
        "plug" -> R.drawable.ic_w_plug
        "switch" -> R.drawable.ic_w_switch
        "scene" -> R.drawable.ic_w_scene
        "air" -> R.drawable.ic_w_air
        "lastdose" -> R.drawable.ic_w_lastdose
        else -> R.drawable.ic_w_lamp
    }

    private fun runIntent(context: Context, widgetId: Int, tagId: String, slot: Int): PendingIntent {
        val intent = Intent(context, WidgetActionReceiver::class.java).apply {
            action = WidgetActionReceiver.ACTION_RUN
            // A distinct data URI per tile, because PendingIntent equality ignores extras and
            // every tile would otherwise collide onto whichever one was created last.
            data = android.net.Uri.parse("taprelay://widget/$widgetId/$slot")
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            putExtra(WidgetActionReceiver.EXTRA_TAG_ID, tagId)
            putExtra(WidgetActionReceiver.EXTRA_SLOT, slot)
        }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun configureIntent(context: Context, widgetId: Int): PendingIntent {
        val intent = Intent(context, WidgetConfigActivity::class.java).apply {
            data = android.net.Uri.parse("taprelay://widget-config/$widgetId")
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
