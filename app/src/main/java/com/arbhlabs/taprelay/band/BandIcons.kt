package com.arbhlabs.taprelay.band

/**
 * Picks band tile icons from the generated catalogue ([BandIconData], 240+ Material icons): first from
 * what an item does (its action), then from its name (English and German keywords), then from its
 * kind - and never shows two tiles with the same icon. Pure Kotlin so it is unit-tested.
 */
object BandIcons {
    private class Entry(val key: String, val category: String, val keywords: List<Pair<String, Boolean>>)

    private val entries: List<Entry> = BandIconData.ROWS.lineSequence().filter { it.isNotBlank() }.map { line ->
        val parts = line.split('\t')
        Entry(
            key = parts[0],
            category = parts.getOrElse(1) { "generic" },
            keywords = parts.getOrElse(2) { "" }.split('|').filter { it.isNotBlank() }
                .map { kw -> kw.removePrefix("!").lowercase() to kw.startsWith("!") }
        )
    }.toList()

    val KEYS: List<String> = entries.map { it.key }
    private val index: Map<String, Int> = KEYS.withIndex().associate { it.value to it.index }
    private val categoryOf: Map<String, String> = entries.associate { it.key to it.category }
    private val byCategory: Map<String, List<String>> = entries.groupBy({ it.category }, { it.key })

    fun category(key: String): String? = categoryOf[key]

    /** Icon keys saved by earlier builds (before the catalogue) that are not catalogue keys any more. */
    private val ALIASES = mapOf(
        "lamp" to "emoji_objects", "room" to "weekend", "plug" to "power", "scene" to "auto_awesome", "fan" to "mode_fan_off",
        "heat" to "thermostat", "pc" to "computer", "media" to "play_pause", "music" to "music_note", "volume" to "vol_plus",
        "moon" to "bedtime", "sun" to "light_mode", "door" to "door_front", "remote" to "settings_remote", "heart" to "monitor_heart",
        "aod" to "visibility", "haptic" to "vibration", "game" to "sports_esports", "pill" to "medication", "phone" to "smartphone",
        "bell" to "notifications", "ceiling" to "light", "tube" to "fluorescent", "spot" to "highlight", "glow" to "wb_iridescent",
        "night" to "nightlight", "bulb" to "wb_incandescent", "vent" to "hvac", "ac" to "ac_unit", "humid" to "water_drop",
        "plant" to "local_florist", "voldown" to "vol_minus", "mute" to "volume_off", "next" to "skip_next", "prev" to "skip_previous"
    )

    fun normalize(key: String?): String? = when {
        key == null -> null
        key in categoryOf -> key
        else -> ALIASES[key]?.takeIf { it in categoryOf }
    }

    /** The best catalogue icon for a name, or null. Longer keyword wins; "!" keywords only when nothing else matched. */
    fun matchName(name: String): String? {
        val s = name.lowercase()
        var best: String? = null
        var bestScore = 0
        for (e in entries) for ((kw, weak) in e.keywords) {
            if (!containsWord(s, kw)) continue
            val score = if (weak) 1 else kw.length + 1
            if (score > bestScore) {
                bestScore = score
                best = e.key
            }
        }
        return best
    }

    /** [kw] at a word start; keywords of 3 letters or fewer ("tv", "ac", "fan") must be whole words. */
    private fun containsWord(s: String, kw: String): Boolean {
        var i = s.indexOf(kw)
        while (i >= 0) {
            val startOk = i == 0 || !s[i - 1].isLetterOrDigit()
            val end = i + kw.length
            val endOk = kw.length > 3 || end >= s.length || !s[end].isLetterOrDigit()
            if (startOk && endOk) return true
            i = s.indexOf(kw, i + 1)
        }
        return false
    }

    private val ACTION_ICONS = mapOf(
        // Windows PC relay
        "media.play_pause" to "play_pause", "media.next" to "skip_next", "media.previous" to "skip_previous",
        "media.stop" to "stop", "media.volume_up" to "vol_plus", "media.volume_down" to "vol_minus", "media.mute" to "volume_off",
        "media.seek_forward" to "forward_5", "media.seek_back" to "replay_5", "system.lock" to "lock",
        "display.off" to "desktop_access_disabled", "system.sleep" to "nights_stay", "keys.send" to "keyboard",
        "open.target" to "open_in_new",
        // This phone
        "media_play_pause" to "play_pause", "media_next" to "skip_next", "media_previous" to "skip_previous",
        "volume_up" to "vol_plus", "volume_down" to "vol_minus", "volume_mute_toggle" to "volume_off",
        "flashlight_toggle" to "flashlight_on", "flashlight_on" to "flashlight_on", "flashlight_off" to "flashlight_off",
        "dnd_on" to "do_not_disturb_on", "dnd_off" to "notifications", "dnd_toggle" to "do_not_disturb_on"
    )

    fun forAction(action: String): String? = ACTION_ICONS[action]

    private val KIND_ICONS = mapOf(
        "lamp" to "lightbulb", "room" to "weekend", "plug" to "power", "switch" to "toggle_on", "scene" to "auto_awesome",
        "air" to "air", "lastdose" to "medication"
    )

    /** The automatic icon for a TapRelay item. [action] is the item's device id, which holds the action for PC and phone items. */
    fun forItem(targetType: String, action: String, name: String, kindKey: String): String {
        val byName = matchName(name)
        val byAction = ACTION_ICONS[action]
        return when (targetType) {
            // A shortcut or "open" is only as specific as its name ("YouTube", "Discord mute").
            "PC_RELAY" -> if (action == "keys.send" || action == "open.target") byName ?: byAction ?: "computer" else byAction ?: byName ?: "computer"
            "PHONE" -> byAction ?: byName ?: "smartphone"
            "LASTDOSE_LOG" -> byName ?: "medication"
            "SCENE" -> byName ?: "auto_awesome"
            "MAGIC_ACTION" -> byName ?: "auto_fix_high"
            "WEBHOOK" -> byName ?: "webhook"
            "LAUNCH" -> byName ?: "open_in_new"
            else -> when (kindKey) {
                "air" -> byName?.takeIf { categoryOf[it] == "climate" } ?: "air"
                else -> byName ?: KIND_ICONS[kindKey] ?: "toggle_on"
            }
        }
    }

    /**
     * Makes icons distinct in band order. A repeat takes the nearest free icon of the same category
     * (catalogue neighbours are closest in meaning), then a numbered one. Ids in [fixed] (chosen by the
     * owner) are never changed.
     */
    fun distinct(items: List<Pair<String, String>>, fixed: Set<String> = emptySet()): Map<String, String> {
        val used = HashSet<String>()
        items.filter { it.first in fixed }.forEach { used += it.second }
        val out = LinkedHashMap<String, String>()
        for ((id, icon) in items) {
            if (id in fixed) { out[id] = icon; continue }
            var chosen = icon
            if (chosen in used) {
                val family = byCategory[categoryOf[icon]].orEmpty()
                val start = family.indexOf(icon).coerceAtLeast(0)
                chosen = (family.drop(start + 1) + family.take(start)).firstOrNull { it !in used }
                    ?: byCategory["generic"].orEmpty().firstOrNull { it !in used }
                    ?: icon
            }
            used += chosen
            out[id] = chosen
        }
        return out
    }

    /** Catalogue order, for the picker. */
    fun order(key: String): Int = index[key] ?: Int.MAX_VALUE
}
