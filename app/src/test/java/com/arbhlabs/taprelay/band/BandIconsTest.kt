package com.arbhlabs.taprelay.band

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BandIconsTest {

    @Test
    fun catalogueIsLargeAndUnique() {
        assertTrue(BandIcons.KEYS.size >= 200)
        assertEquals(BandIcons.KEYS.size, BandIcons.KEYS.toSet().size)
    }

    @Test
    fun ownersLampsLookDifferent() {
        val names = listOf("Corner Lamp", "TV Lamp", "Vent Lamp", "Desk Lamp", "Bathroom Light ")
        val icons = names.map { BandIcons.forItem("DEVICE", "dev", it, "lamp") }
        assertEquals(names.size, icons.toSet().size)
        assertEquals("hvac", icons[2])
        assertEquals("desk", icons[3])
    }

    @Test
    fun volumeAndMediaSayWhatTheyDo() {
        assertEquals("vol_plus", BandIcons.forItem("PC_RELAY", "media.volume_up", "Volume up", ""))
        assertEquals("vol_minus", BandIcons.forItem("PC_RELAY", "media.volume_down", "Volume down", ""))
        assertEquals("play_pause", BandIcons.forItem("PC_RELAY", "media.play_pause", "Play / Pause", ""))
        assertEquals("forward_5", BandIcons.forAction("media.seek_forward"))
        assertEquals("replay_5", BandIcons.forAction("media.seek_back"))
        assertEquals("vol_minus", BandIcons.forItem("PHONE", "volume_down", "Quieter", ""))
    }

    @Test
    fun shortKeywordsNeedWholeWords() {
        assertNotEquals("ac_unit", BandIcons.matchName("Action"))
        assertEquals("ac_unit", BandIcons.matchName("Aircon"))
        assertEquals("tv", BandIcons.matchName("TV"))
    }

    @Test
    fun namesAndKinds() {
        assertEquals("smoking_rooms", BandIcons.forItem("LASTDOSE_LOG", "", "Bowl", "lastdose"))
        assertEquals("medication", BandIcons.forItem("LASTDOSE_LOG", "", "-----", "lastdose"))
        assertEquals("air", BandIcons.forItem("DEVICE", "d", "Air Purifier", "air"))
        assertEquals("house", BandIcons.forItem("DEVICE", "d", "All ON/OFF", "room"))
        assertEquals("brightness_auto", BandIcons.matchName("AOD auto-dim"))
    }

    @Test
    fun repeatsBecomeDistinctButChosenIconsStay() {
        val out = BandIcons.distinct(
            listOf("a" to "medication", "b" to "medication", "c" to "lightbulb", "d" to "lightbulb", "e" to "lightbulb"),
            fixed = setOf("e")
        )
        assertEquals(5, out.values.toSet().size)
        assertEquals("lightbulb", out["e"])
        assertNotEquals(out["a"], out["b"])
        assertEquals("light", BandIcons.category(out["d"]!!))
    }
}
