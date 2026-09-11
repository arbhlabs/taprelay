package com.arbhlabs.taprelay.ui.remote

import com.arbhlabs.taprelay.data.local.dao.TapLogSummary
import com.arbhlabs.taprelay.data.local.entity.TagEntity
import com.arbhlabs.taprelay.execution.lastdose.LastDoseLatestEvent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * What the always-on face knows about one favourite's history: when it last happened, the pulse
 * recorded with it, and how often today. Every field comes from a stored row - TapRelay's own tap
 * history, or LastDose's event table for items that log there - and elapsed time is derived from
 * [lastAt] at draw time, never stored.
 */
data class AodLogState(
    val lastAt: Long?,
    val heartRate: Int?,
    val countToday: Int?,
    /** "5 mg" for a LastDose log with a unit; null when there is nothing worth adding. */
    val detail: String? = null
) {
    companion object {
        val NEVER = AodLogState(lastAt = null, heartRate = null, countToday = 0)

        /**
         * LastDose is the source of truth for a LastDose item: it also counts logs made inside
         * LastDose itself. TapRelay's own history is the fallback when LastDose can't be reached.
         */
        fun resolve(
            tag: TagEntity,
            tapSummary: TapLogSummary?,
            lastDose: LastDoseLatestEvent?
        ): AodLogState {
            if (tag.isLastDose && lastDose != null) {
                val detail = lastDose.unit.takeIf { it.isNotBlank() }
                    ?.let { unit -> listOf(lastDose.amount, unit).filter { it.isNotBlank() }.joinToString(" ") }
                return AodLogState(
                    lastAt = lastDose.eventTime,
                    heartRate = lastDose.lastHeartRate,
                    countToday = lastDose.countToday,
                    detail = detail
                )
            }
            if (tapSummary == null) return NEVER
            return AodLogState(
                lastAt = tapSummary.lastAt,
                heartRate = tapSummary.lastHeartRate,
                countToday = tapSummary.countSince
            )
        }
    }
}

/** Every string the face derives from a timestamp. Pure, so it is unit-tested rather than eyeballed. */
object AodLogFormat {
    private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
    private val WEEKDAY = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
    private val DATE = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

    /**
     * "42s", "7m 57s", "2h 14m", "3d 4h". Seconds only while the face ticks every second;
     * a dozing face redraws once a minute and must not show a seconds figure that sits still.
     */
    fun elapsed(lastAt: Long, now: Long, withSeconds: Boolean): String {
        val totalSeconds = ((now - lastAt).coerceAtLeast(0L)) / 1_000L
        val days = totalSeconds / 86_400L
        val hours = (totalSeconds % 86_400L) / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L
        return when {
            days > 0 -> if (hours > 0) "${days}d ${hours}h" else "${days}d"
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> if (withSeconds) "${minutes}m ${seconds}s" else "${minutes}m"
            withSeconds -> "${seconds}s"
            else -> "Just now"
        }
    }

    /** "Today · 21:42", "Yesterday · 23:10", "Tue · 08:05", "28 Aug · 08:05". */
    fun whenLogged(lastAt: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val at = Instant.ofEpochMilli(lastAt).atZone(zone)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val days = java.time.temporal.ChronoUnit.DAYS.between(at.toLocalDate(), today)
        val day = when {
            days <= 0L -> "Today"
            days == 1L -> "Yesterday"
            days < 7L -> at.format(WEEKDAY)
            else -> at.format(DATE)
        }
        return "$day · ${at.format(TIME)}"
    }

    /** "3 today", or null when there is nothing to count. */
    fun count(countToday: Int?): String? = countToday?.takeIf { it > 0 }?.let { "$it today" }

    /** "♥ 106 BPM", or null without a recorded pulse. */
    fun heartRate(bpm: Int?): String? = bpm?.takeIf { it > 0 }?.let { "♥ $it BPM" }
}
