package com.arbhlabs.taprelay

import com.arbhlabs.taprelay.data.local.dao.TapLogSummary
import com.arbhlabs.taprelay.data.local.entity.TagEntity
import com.arbhlabs.taprelay.domain.model.TargetType
import com.arbhlabs.taprelay.execution.lastdose.LastDoseLatestEvent
import com.arbhlabs.taprelay.ui.remote.AodLogFormat
import com.arbhlabs.taprelay.ui.remote.AodLogState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class AodLogTest {
    private val zone = ZoneId.of("Europe/Amsterdam")
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int = 0) =
        LocalDateTime.of(y, mo, d, h, mi, s).atZone(zone).toInstant().toEpochMilli()

    private val now = at(2026, 9, 11, 22, 31, 40)

    @Test fun elapsed_seconds_minutes_hours_days() {
        assertEquals("40s", AodLogFormat.elapsed(now - 40_000, now, withSeconds = true))
        assertEquals("Just now", AodLogFormat.elapsed(now - 40_000, now, withSeconds = false))
        assertEquals("31m 40s", AodLogFormat.elapsed(at(2026, 9, 11, 22, 0), now, true))
        assertEquals("31m", AodLogFormat.elapsed(at(2026, 9, 11, 22, 0), now, false))
        assertEquals("2h 14m", AodLogFormat.elapsed(at(2026, 9, 11, 20, 17, 40), now, true))
        assertEquals("3d 4h", AodLogFormat.elapsed(at(2026, 9, 8, 18, 31, 40), now, true))
        assertEquals("2d", AodLogFormat.elapsed(at(2026, 9, 9, 22, 31, 40), now, true))
    }

    @Test fun elapsed_never_goes_negative_on_a_clock_skew() {
        assertEquals("0s", AodLogFormat.elapsed(now + 5_000, now, true))
    }

    @Test fun when_logged_today_yesterday_week_older() {
        assertEquals("Today · 21:42", AodLogFormat.whenLogged(at(2026, 9, 11, 21, 42), now, zone))
        assertEquals("Today · 00:01", AodLogFormat.whenLogged(at(2026, 9, 11, 0, 1), now, zone))
        assertEquals("Yesterday · 23:59", AodLogFormat.whenLogged(at(2026, 9, 10, 23, 59), now, zone))
        assertEquals("28 Aug · 08:05", AodLogFormat.whenLogged(at(2026, 8, 28, 8, 5), now, zone))
    }

    @Test fun count_and_heart_rate_hide_when_absent() {
        assertNull(AodLogFormat.count(0))
        assertNull(AodLogFormat.count(null))
        assertEquals("1 today", AodLogFormat.count(1))
        assertNull(AodLogFormat.heartRate(null))
        assertNull(AodLogFormat.heartRate(0))
        assertEquals("♥ 106 BPM", AodLogFormat.heartRate(106))
    }

    private val lamp = TagEntity(
        tagId = "lamp", friendlyName = "Lamp", iconKey = "bulb", providerId = "govee",
        deviceId = "d", deviceSku = "s", actionType = com.arbhlabs.taprelay.domain.model.ActionType.TOGGLE
    )
    private val dose = lamp.copy(tagId = "dose", targetType = TargetType.LASTDOSE_LOG, lastDoseItemId = 7L)

    @Test fun never_run_item_is_never() {
        assertEquals(AodLogState.NEVER, AodLogState.resolve(lamp, null, null))
    }

    @Test fun taprelay_item_uses_its_own_history() {
        val s = AodLogState.resolve(lamp, TapLogSummary("lamp", 1_000L, 98, 3), null)
        assertEquals(1_000L, s.lastAt)
        assertEquals(98, s.heartRate)
        assertEquals(3, s.countToday)
    }

    @Test fun lastdose_item_prefers_lastdose_and_falls_back_to_history() {
        val event = LastDoseLatestEvent(1, 5_000L, "5", "mg", "Dose", 106, 0, 2)
        val fromLastDose = AodLogState.resolve(dose, TapLogSummary("dose", 1_000L, 90, 1), event)
        assertEquals(5_000L, fromLastDose.lastAt)
        assertEquals(106, fromLastDose.heartRate)
        assertEquals(2, fromLastDose.countToday)
        assertEquals("5 mg", fromLastDose.detail)

        val unreachable = AodLogState.resolve(dose, TapLogSummary("dose", 1_000L, 90, 1), null)
        assertEquals(1_000L, unreachable.lastAt)
    }

    @Test fun unitless_amount_is_not_shown_as_detail() {
        val event = LastDoseLatestEvent(1, 5_000L, "1", "", "Bowl", null, 0, null)
        assertNull(AodLogState.resolve(dose, null, event).detail)
    }
}
