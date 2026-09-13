package com.arbhlabs.taprelay.band

import com.arbhlabs.taprelay.data.local.entity.TagEntity
import com.arbhlabs.taprelay.di.ServiceLocator
import com.arbhlabs.taprelay.domain.model.ColorMath
import com.arbhlabs.taprelay.domain.model.DeviceCapabilities
import com.arbhlabs.taprelay.domain.model.DeviceKind
import com.arbhlabs.taprelay.domain.model.DeviceLabels
import com.arbhlabs.taprelay.domain.model.PhoneAction
import com.arbhlabs.taprelay.domain.model.TagTarget
import com.arbhlabs.taprelay.domain.model.TapError
import com.arbhlabs.taprelay.domain.model.TapException
import com.arbhlabs.taprelay.domain.model.TargetType
import com.arbhlabs.taprelay.domain.provider.SmartHomeProvider
import com.arbhlabs.taprelay.execution.phone.PhoneResult
import com.arbhlabs.taprelay.pc.PcRelayAction
import com.arbhlabs.taprelay.pc.PcRelayResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Long-press on a band tile: the controls that make sense for that one item, built from what TapRelay
 * already knows - device capabilities from its provider, the PC helper's keys, the phone's actions.
 * Every op runs through the same provider / relay / controller code the app uses; the band only draws it.
 *
 * Detail JSON: {type:detail, id, label, icon, sub, groups:[{title, cols, actions:[{id, label, icon, active, swatch}]}]}
 * Action ids are "<control id>|<op>"; only ops sent in the last detail for that control are accepted.
 */
internal object BandDetail {
    class Built(val json: JSONObject, val ops: Set<String>)

    private class Builder(private val controlId: String) {
        val ops = LinkedHashSet<String>()
        val groups = JSONArray()

        fun group(title: String, cols: Int, fill: Group.() -> Unit) {
            val g = Group().apply(fill)
            if (g.actions.length() > 0) {
                groups.put(JSONObject().put("title", title).put("cols", cols).put("actions", g.actions))
            }
        }

        inner class Group {
            val actions = JSONArray()
            fun add(op: String, label: String, icon: String? = null, active: Boolean = false, swatch: String? = null) {
                ops += op
                actions.put(
                    JSONObject().put("id", "$controlId|$op").put("label", label).put("icon", icon ?: JSONObject.NULL)
                        .put("active", active).put("swatch", swatch ?: JSONObject.NULL)
                )
            }
        }
    }

    // ---- Windows PC: the video in front, then Windows itself. All keys the helper already presses. ----

    private class PcOp(val op: String, val label: String, val icon: String, val action: String, val value: String? = null)

    private val PC_MEDIA = listOf(
        PcOp("pc:back", "Back 5 s", "replay_5", PcRelayAction.MEDIA_SEEK_BACK),
        PcOp("pc:fwd", "Forward 5 s", "forward_5", PcRelayAction.MEDIA_SEEK_FORWARD),
        PcOp("pc:play", "Play / pause", "play_pause", PcRelayAction.MEDIA_PLAY_PAUSE),
        PcOp("pc:mute", "Mute", "volume_off", PcRelayAction.MEDIA_MUTE),
        PcOp("pc:voldn", "Volume -", "vol_minus", PcRelayAction.MEDIA_VOLUME_DOWN),
        PcOp("pc:volup", "Volume +", "vol_plus", PcRelayAction.MEDIA_VOLUME_UP),
        PcOp("pc:prev", "Previous", "skip_previous", PcRelayAction.MEDIA_PREVIOUS),
        PcOp("pc:next", "Next", "skip_next", PcRelayAction.MEDIA_NEXT)
    )
    private val PC_PLAYER = listOf(
        PcOp("pc:full", "Fullscreen", "fullscreen", PcRelayAction.KEYS_SEND, "f"),
        PcOp("pc:esc", "Exit / Esc", "close_fullscreen", PcRelayAction.KEYS_SEND, "esc"),
        PcOp("pc:captions", "Captions", "subtitles", PcRelayAction.KEYS_SEND, "c"),
        PcOp("pc:theater", "Theater", "crop_16_9", PcRelayAction.KEYS_SEND, "t"),
        PcOp("pc:slower", "Slower", "slow_motion_video", PcRelayAction.KEYS_SEND, "shift+,"),
        PcOp("pc:faster", "Faster", "speed", PcRelayAction.KEYS_SEND, "shift+."),
        PcOp("pc:nextvideo", "Next video", "switch_video", PcRelayAction.KEYS_SEND, "shift+n"),
        PcOp("pc:miniplayer", "Mini player", "picture_in_picture_alt", PcRelayAction.KEYS_SEND, "i")
    )
    private val PC_WINDOWS = listOf(
        PcOp("pc:gamebar", "Game Bar", "videogame_asset", PcRelayAction.KEYS_SEND, "win+g"),
        PcOp("pc:clip", "Clip 30 s", "fiber_manual_record", PcRelayAction.KEYS_SEND, "win+alt+g"),
        PcOp("pc:shot", "Screenshot", "photo_camera", PcRelayAction.KEYS_SEND, "win+alt+prtsc"),
        PcOp("pc:alttab", "Switch app", "flip_to_front", PcRelayAction.KEYS_SEND, "alt+tab"),
        PcOp("pc:desktop", "Desktop", "desktop_windows", PcRelayAction.KEYS_SEND, "win+d"),
        PcOp("pc:tasks", "Task view", "view_quilt", PcRelayAction.KEYS_SEND, "win+tab"),
        PcOp("pc:lock", "Lock PC", "lock", PcRelayAction.SYSTEM_LOCK),
        PcOp("pc:screenoff", "Screen off", "desktop_access_disabled", PcRelayAction.DISPLAY_OFF)
    )
    private val PC_ALL = PC_MEDIA + PC_PLAYER + PC_WINDOWS

    private val PHONE_OPS = listOf(
        Triple("ph:${PhoneAction.VOLUME_DOWN}", "Volume -", "vol_minus"),
        Triple("ph:${PhoneAction.VOLUME_UP}", "Volume +", "vol_plus"),
        Triple("ph:${PhoneAction.MEDIA_PREVIOUS}", "Previous", "skip_previous"),
        Triple("ph:${PhoneAction.MEDIA_NEXT}", "Next", "skip_next"),
        Triple("ph:${PhoneAction.MEDIA_PLAY_PAUSE}", "Play / pause", "play_pause"),
        Triple("ph:${PhoneAction.VOLUME_MUTE_TOGGLE}", "Mute", "volume_off"),
        Triple("ph:${PhoneAction.FLASHLIGHT_TOGGLE}", "Torch", "flashlight_on"),
        Triple("ph:${PhoneAction.DND_TOGGLE}", "Do not disturb", "do_not_disturb_on")
    )

    // ---- lights ----

    private val BRIGHTNESS = listOf(5, 15, 30, 50, 75, 100)
    private val WHITES = listOf(Triple("candle", "Candle", 2000), Triple("warm", "Warm", 2700), Triple("cool", "Cool", 6500))
    private val COLORS = listOf(
        Triple("red", "Red", 0xFF1414), Triple("orange", "Orange", 0xFF7A00), Triple("yellow", "Yellow", 0xFFE500),
        Triple("green", "Green", 0x28D828), Triple("teal", "Teal", 0x00C7B1), Triple("sky", "Sky", 0x3AA0FF),
        Triple("blue", "Blue", 0x1E3AFF), Triple("purple", "Purple", 0xA93BE0), Triple("pink", "Pink", 0xFF74C4)
    )

    /** Provider capability reads can be a cloud round trip; a band held open re-reads at most every 10 minutes. */
    private val capsCache = ConcurrentHashMap<String, Pair<Long, DeviceCapabilities>>()

    suspend fun build(s: ServiceLocator, c: BandBridge.Control): Built {
        val b = Builder(c.id)
        var sub = c.sub
        when {
            c.id == "remote" || c.id.startsWith("pref:") -> {
                b.group(c.label, 2) {
                    add("on", "On", "toggle_on", active = c.on == true)
                    add("off", "Off", "power_settings_new", active = c.on == false)
                }
                sub = if (c.on == true) "On" else "Off"
            }
            c.id == "hr" -> b.group("Heart rate", 2) { add("refresh", "Refresh", "monitor_heart") }
            c.id == "last" -> b.group("Repeat", 2) { add("run", "Run again", "replay") }
            c.id.startsWith("pc:") -> pcGroups(b)
            c.id.startsWith("item:") -> {
                val tag = withContext(Dispatchers.IO) { s.tagRepository.getTagById(c.id.removePrefix("item:")) }
                when {
                    tag == null -> b.group("Item", 2) { add("run", "Run", "play_arrow") }
                    tag.isPcRelay -> {
                        b.group("This tile", 2) { add("run", tag.friendlyName, c.icon) }
                        pcGroups(b)
                    }
                    tag.isPhone -> {
                        b.group("This tile", 2) { add("run", tag.friendlyName, c.icon) }
                        b.group("Phone", 2) { PHONE_OPS.forEach { (op, label, icon) -> add(op, label, icon) } }
                    }
                    tag.targetType == TargetType.DEVICE -> sub = deviceGroups(s, b, tag)
                    tag.targetType == TargetType.SCENE -> b.group("Scene", 2) { add("run", "Start", "auto_awesome") }
                    else -> b.group("Run", 2) { add("run", tag.friendlyName, c.icon) }
                }
            }
        }
        val json = JSONObject().put("type", "detail").put("id", c.id).put("label", c.label.take(28))
            .put("icon", c.icon).put("sub", sub.take(28)).put("groups", b.groups)
        return Built(json, b.ops)
    }

    private fun pcGroups(b: Builder) {
        b.group("Video", 2) { PC_MEDIA.forEach { add(it.op, it.label, it.icon) } }
        b.group("Player", 2) { PC_PLAYER.forEach { add(it.op, it.label, it.icon) } }
        b.group("Windows", 2) { PC_WINDOWS.forEach { add(it.op, it.label, it.icon) } }
    }

    private suspend fun deviceGroups(s: ServiceLocator, b: Builder, tag: TagEntity): String {
        val caps = capabilities(s, tag)
        val snap = withContext(Dispatchers.IO) {
            val p = tag.allTargets.first()
            runCatching { s.providers[p.providerId]?.getSnapshot(p.deviceId, p.sku) }.getOrNull()
        }
        val on = (snap?.power ?: tag.lastKnownState) == 1
        if (caps.supportsPower) {
            b.group("Power", 2) {
                add("on", "On", "toggle_on", active = on)
                add("off", "Off", "power_settings_new", active = !on)
            }
        }
        if (caps.supportsBrightness) b.group("Brightness", 3) { BRIGHTNESS.forEach { add("b:$it", "$it%") } }
        if (caps.supportsColorTemperature) b.group("White", 3) { WHITES.forEach { (key, label, _) -> add("w:$key", label, swatch = key) } }
        if (caps.supportsColor) b.group("Colour", 3) { COLORS.forEach { (key, label, _) -> add("c:$key", label, swatch = key) } }
        if (caps.supportsFanSpeed) {
            b.group("Fan", 2) { caps.fanLevels.forEach { add("fan:$it", DeviceLabels.fanLevel(it), "mode_fan_off", active = snap?.fanLevel == it) } }
        }
        if (caps.supportsModes) {
            b.group("Mode", 2) { caps.modes.forEach { add("mode:$it", DeviceLabels.mode(it), "tune", active = snap?.mode == it) } }
        }
        if (!caps.supportsPower) b.group("Run", 2) { add("run", tag.friendlyName, "play_arrow") }
        return if (on) "On" else "Off"
    }

    private suspend fun capabilities(s: ServiceLocator, tag: TagEntity): DeviceCapabilities {
        capsCache[tag.tagId]?.let { (at, caps) -> if (System.currentTimeMillis() - at < 10 * 60_000L) return caps }
        val caps = withContext(Dispatchers.IO) {
            runCatching {
                tag.allTargets.groupBy { it.providerId }
                    .flatMap { (providerId, targets) ->
                        s.providers[providerId]?.getCapabilities(targets.map { it.deviceId to it.sku }).orEmpty()
                    }
                    .reduceOrNull { a, o ->
                        DeviceCapabilities(
                            kind = if (a.kind == o.kind) a.kind else DeviceKind.SWITCH,
                            supportsPower = a.supportsPower && o.supportsPower,
                            supportsBrightness = a.supportsBrightness && o.supportsBrightness,
                            supportsColor = a.supportsColor && o.supportsColor,
                            supportsColorTemperature = a.supportsColorTemperature && o.supportsColorTemperature,
                            fanLevels = a.fanLevels.filter { it in o.fanLevels },
                            modes = a.modes.filter { it in o.modes }
                        )
                    }
            }.getOrNull()
        } ?: return DeviceCapabilities.UNKNOWN
        capsCache[tag.tagId] = System.currentTimeMillis() to caps
        return caps
    }

    // ---- running an op ----

    suspend fun pc(s: ServiceLocator, action: String, value: String?, label: String): Pair<Boolean, String> =
        when (val r = s.pcRelay.send(action, value, "band:${UUID.randomUUID()}")) {
            is PcRelayResult.Accepted -> true to label
            is PcRelayResult.Failed -> false to r.message
        }

    suspend fun run(s: ServiceLocator, controlId: String, op: String): Pair<Boolean, String> {
        PC_ALL.firstOrNull { it.op == op }?.let { return pc(s, it.action, it.value, it.label) }
        if (op.startsWith("ph:")) {
            return when (val r = withContext(Dispatchers.Main) { s.phoneController.run(op.removePrefix("ph:")) }) {
                is PhoneResult.Done -> true to r.summary
                is PhoneResult.Failed -> false to r.message
                else -> false to "Allow it in TapRelay first"
            }
        }
        val tagId = controlId.removePrefix("item:")
        if (op == "run") return s.actionExecutor.executeAndAwait(tagId, debounce = false).let { it.success to it.summary }
        val tag = withContext(Dispatchers.IO) { s.tagRepository.getTagById(tagId) } ?: return false to TapError.TAG_UNREGISTERED.message
        val arg = op.substringAfter(':')
        val unknown = false to "Unknown control"
        return when {
            op == "on" -> device(s, tag, "On", 1) { p, t -> p.setPower(t.deviceId, t.sku, true) }
            op == "off" -> device(s, tag, "Off", 0) { p, t -> p.setPower(t.deviceId, t.sku, false) }
            op.startsWith("b:") -> arg.toIntOrNull()?.let { pct ->
                device(s, tag, "$pct%", 1) { p, t -> p.setBrightness(t.deviceId, t.sku, pct) }
            } ?: unknown
            op.startsWith("w:") -> WHITES.firstOrNull { it.first == arg }?.let { (_, label, kelvin) ->
                val rgb = ColorMath.kelvinToRgb(kelvin)
                device(s, tag, label, 1) { p, t -> p.setColor(t.deviceId, t.sku, rgb) }
            } ?: unknown
            op.startsWith("c:") -> COLORS.firstOrNull { it.first == arg }?.let { (_, label, rgb) ->
                device(s, tag, label, 1) { p, t -> p.setColor(t.deviceId, t.sku, rgb) }
            } ?: unknown
            op.startsWith("fan:") -> device(s, tag, "Fan " + DeviceLabels.fanLevel(arg), 1) { p, t -> p.setFanLevel(t.deviceId, t.sku, arg) }
            op.startsWith("mode:") -> device(s, tag, DeviceLabels.mode(arg), 1) { p, t -> p.setMode(t.deviceId, t.sku, arg) }
            else -> unknown
        }
    }

    /** Same shape as Quick Controls: every device the item drives, then the tag records the new state. */
    private suspend fun device(
        s: ServiceLocator,
        tag: TagEntity,
        label: String,
        powerAfter: Int,
        block: suspend (SmartHomeProvider, TagTarget) -> Unit
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        var succeeded = 0
        var failure: String? = null
        for (target in tag.allTargets) {
            val provider = s.providers[target.providerId]
            if (provider == null) {
                failure = failure ?: TapError.PROVIDER_UNAVAILABLE.message
                continue
            }
            try {
                block(provider, target)
                succeeded++
            } catch (e: TapException) {
                failure = failure ?: e.error.message
            } catch (e: Exception) {
                failure = failure ?: TapError.UNKNOWN.message
            }
        }
        if (succeeded > 0) {
            s.tagRepository.updateStateAndTimestamp(tag.tagId, powerAfter, System.currentTimeMillis())
            true to "${tag.friendlyName}: $label"
        } else {
            false to (failure ?: TapError.UNKNOWN.message)
        }
    }
}
