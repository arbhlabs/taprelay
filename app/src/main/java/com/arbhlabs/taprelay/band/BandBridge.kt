package com.arbhlabs.taprelay.band

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.arbhlabs.taprelay.TapRelayApplication
import com.arbhlabs.taprelay.data.prefs.AppPreferences
import com.arbhlabs.taprelay.ui.remote.RemoteModeActivity
import com.xiaomi.xms.wearable.Wearable
import com.xiaomi.xms.wearable.auth.Permission
import com.xiaomi.xms.wearable.service.OnServiceConnectionListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference
import com.arbhlabs.taprelay.pc.PcRelayAction
import java.util.concurrent.ConcurrentHashMap

/**
 * Xiaomi Smart Band 9 wrist remote (TapRelay_Band9 quick app).
 *
 * The band is a thin renderer: it shows whatever [Control]s the phone sends and asks the phone to
 * run one by id. Every control maps onto something TapRelay already does - an item through
 * [com.arbhlabs.taprelay.execution.ActionExecutor], Remote Mode, or an existing preference - so no
 * logic lives on the wrist. Which controls appear, and in what order, is chosen in the app
 * (AOD settings -> watch icon); only those can be run from the band.
 *
 * Transport is the Xiaomi wearable interconnect, brokered by Notify for Xiaomi. Inert when no
 * Xiaomi band app is installed.
 *
 * Protocol (JSON): band {type:hello} | {type:cmd, seq, id}
 *                  phone {type:state, hr, remote, home, controls:[{id,label,kind,on,sub}]} | {type:ack, seq} | {type:result, seq, ok, text}
 */
object BandBridge {
    private const val TAG = "TapRelayBand"
    private const val RETRY_MS = 60_000L
    private const val KEEPALIVE_MS = 8_000L
    private const val HOME_COUNT = 4
    private const val MAX_CONTROLS = 24

    data class Control(val id: String, val label: String, val toggle: Boolean, val on: Boolean?, val sub: String = "", val icon: String = "bolt")

    /** Tile icons bundled in the band app: the generated catalogue, see [BandIcons]. */
    val ICON_KEYS: List<String> get() = BandIcons.KEYS

    /** [all] with automatic icons made distinct in band order ([ids]). Icons the owner chose are left alone. */
    fun withDistinctIcons(all: List<Control>, ids: List<String>): List<Control> {
        val ordered = ids.mapNotNull { id -> all.firstOrNull { it.id == id } }
        val fixed = ordered.filter { iconOverride(it.id) != null }.map { it.id }.toSet()
        val icons = BandIcons.distinct(ordered.map { it.id to it.icon }, fixed)
        return all.map { c -> icons[c.id]?.let { c.copy(icon = it) } ?: c }
    }

    private const val SEEK_BACK = "pc:" + PcRelayAction.MEDIA_SEEK_BACK
    private const val SEEK_FORWARD = "pc:" + PcRelayAction.MEDIA_SEEK_FORWARD

    /** One-tap PC media tiles, offered once a Windows helper is paired. */
    private val PC_PAD = listOf(
        PcRelayAction.MEDIA_SEEK_BACK to "Back 5 s", PcRelayAction.MEDIA_SEEK_FORWARD to "Forward 5 s",
        PcRelayAction.MEDIA_PLAY_PAUSE to "PC play / pause", PcRelayAction.MEDIA_MUTE to "PC mute",
        PcRelayAction.MEDIA_VOLUME_DOWN to "PC volume -", PcRelayAction.MEDIA_VOLUME_UP to "PC volume +",
        PcRelayAction.MEDIA_PREVIOUS to "PC previous", PcRelayAction.MEDIA_NEXT to "PC next"
    )

    fun iconOverride(id: String): String? = BandIcons.normalize(prefs().getString("icon:$id", null))

    fun setIcon(id: String, key: String) {
        prefs().edit().putString("icon:$id", key).apply()
        pushState()
    }

    fun clearIcon(id: String) {
        prefs().edit().remove("icon:$id").apply()
        pushState()
    }

    private class PrefToggle(
        val id: String,
        val label: String,
        val get: (AppPreferences) -> Flow<Boolean>,
        val set: suspend (AppPreferences, Boolean) -> Unit
    )

    private val PREFS = listOf(
        PrefToggle("pref:autodim", "AOD auto-dim", { it.remoteAutoDim }, { p, v -> p.setRemoteAutoDim(v) }),
        PrefToggle("pref:aodclock", "AOD clock", { it.aodShowClock }, { p, v -> p.setAodShowClock(v) }),
        PrefToggle("pref:aodconfirm", "AOD confirm taps", { it.aodConfirmActions }, { p, v -> p.setAodConfirmActions(v) }),
        PrefToggle("pref:aodhr", "AOD log HR", { it.aodLogHeartRate }, { p, v -> p.setAodLogHeartRate(v) }),
        PrefToggle("pref:aodmono", "AOD monochrome", { it.aodMonochrome }, { p, v -> p.setAodMonochrome(v) }),
        PrefToggle("pref:haptics", "Phone haptics", { it.phoneHaptics }, { p, v -> p.setPhoneHaptics(v) }),
        PrefToggle("pref:rumble", "Controller rumble", { it.controllerRumble }, { p, v -> p.setControllerRumble(v) }),
        PrefToggle("pref:signatures", "Haptic signatures", { it.automaticHapticSignatures }, { p, v -> p.setAutomaticHapticSignatures(v) })
    )

    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var app: TapRelayApplication
    private val listening = HashSet<String>()
    private var permissionAsked = false

    @Volatile private var remoteActivity: WeakReference<Activity>? = null
    @Volatile private var bandIds: Set<String> = emptySet()
    @Volatile private var lastControls: Map<String, Control> = emptyMap()
    private val detailOps = ConcurrentHashMap<String, Set<String>>()

    /**
     * The context every xms-wearable-lib call gets. The library binds Notify's service through
     * `context.applicationContext` and its ServiceConnection.onServiceConnected makes a remote call
     * that throws (NullPointerException "com.mc.xiaomi1.bluetooth.BaseService", device 2026-09-13)
     * while Notify's Bluetooth service is restarting. That runs on the main thread and used to take
     * the whole TapRelay app down on launch, in a loop via [BandLinkService]. Here the connection is
     * wrapped: a failed delivery is logged and re-delivered with the same binder a little later.
     */
    private class SdkContext(base: Context) : android.content.ContextWrapper(base) {
        private val guards = java.util.WeakHashMap<android.content.ServiceConnection, GuardedConnection>()

        override fun getApplicationContext(): Context = this

        override fun bindService(service: android.content.Intent, conn: android.content.ServiceConnection, flags: Int): Boolean =
            super.bindService(service, synchronized(guards) { guards.getOrPut(conn) { GuardedConnection(conn) } }, flags)

        override fun unbindService(conn: android.content.ServiceConnection) {
            super.unbindService(synchronized(guards) { guards[conn] } ?: conn)
        }
    }

    private class GuardedConnection(private val inner: android.content.ServiceConnection) : android.content.ServiceConnection {
        private val handler = Handler(Looper.getMainLooper())
        @Volatile private var connected = false

        override fun onServiceConnected(name: android.content.ComponentName, binder: android.os.IBinder) {
            connected = true
            handler.removeCallbacksAndMessages(null)
            deliver(name, binder)
        }

        private fun deliver(name: android.content.ComponentName, binder: android.os.IBinder) {
            try {
                inner.onServiceConnected(name, binder)
            } catch (e: RuntimeException) {
                Log.w(TAG, "band broker not ready, retrying in ${BROKER_RETRY_MS / 1000}s: $e")
                handler.postDelayed({ if (connected && binder.isBinderAlive) deliver(name, binder) }, BROKER_RETRY_MS)
            }
        }

        override fun onServiceDisconnected(name: android.content.ComponentName) {
            connected = false
            handler.removeCallbacksAndMessages(null)
            runCatching { inner.onServiceDisconnected(name) }
        }

        override fun onBindingDied(name: android.content.ComponentName) {
            connected = false
            handler.removeCallbacksAndMessages(null)
            runCatching { inner.onBindingDied(name) }
        }

        override fun onNullBinding(name: android.content.ComponentName) {
            runCatching { inner.onNullBinding(name) }
        }
    }

    private const val BROKER_RETRY_MS = 15_000L
    private lateinit var sdk: Context

    fun start(application: TapRelayApplication) {
        app = application
        // 0.6.7: icons chosen in the old 28-icon picker would hide the new automatic picks; start from the automatic set once.
        prefs().let { p -> if (!p.getBoolean("migrated.icons259", false)) p.edit().apply { p.all.keys.filter { it.startsWith("icon:") }.forEach { remove(it) } }.putBoolean("migrated.icons259", true).apply() }
        sdk = SdkContext(application)
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (activity is RemoteModeActivity) { remoteActivity = WeakReference(activity); pushState() }
            }
            override fun onActivityDestroyed(activity: Activity) {
                if (activity is RemoteModeActivity && remoteActivity?.get() === activity) { remoteActivity = null; pushState() }
            }
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        })
        runCatching {
            Wearable.getServiceApi(sdk).registerServiceConnectionListener(object : OnServiceConnectionListener {
                override fun onServiceConnected() = connect()
                override fun onServiceDisconnected() { synchronized(listening) { listening.clear() } }
            })
        }
        connect()
        // adb-only diagnostics (DUMP is held by the shell, never by apps):
        //   adb shell am broadcast -a com.arbhlabs.taprelay.BAND_DEBUG --es cmd launch|state|reconnect
        runCatching {
            androidx.core.content.ContextCompat.registerReceiver(app, object : android.content.BroadcastReceiver() {
                override fun onReceive(c: Context, i: android.content.Intent) {
                    val nodes = synchronized(listening) { listening.toList() }
                    Log.i(TAG, "debug ${i.getStringExtra("cmd")} nodes=${nodes.size}")
                    when (i.getStringExtra("cmd")) {
                        "launch" -> nodes.forEach { n ->
                            Wearable.getNodeApi(sdk).launchWearApp(n, "pages/index")
                                .addOnSuccessListener { Log.i(TAG, "launched band app") }
                                .addOnFailureListener { Log.w(TAG, "launch failed: $it") }
                        }
                        "reconnect" -> { synchronized(listening) { listening.clear() }; connect() }
                        else -> pushState()
                    }
                }
            }, android.content.IntentFilter("com.arbhlabs.taprelay.BAND_DEBUG"), "android.permission.DUMP", null,
                androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
        }
        // On Band 9 2.3.98 the band app cannot open the link itself: its sends fail until the phone
        // has messaged it (confirmed on device - no band hello ever arrives). So the phone keeps
        // the link warm; a band app that just opened goes "Linked" within one tick.
        scope.launch {
            while (true) {
                delay(KEEPALIVE_MS)
                pushState()
            }
        }
        // Anything run from another surface (NFC, controller, AOD) shows up on the wrist too.
        scope.launch { runCatching { app.services.actionExecutor.executions.collect { pushState() } } }
    }

    // ---- configuration (used by BandControlsScreen) ----

    private fun prefs() = app.getSharedPreferences("band9_controls", Context.MODE_PRIVATE)

    /** Every control TapRelay can offer the band right now. */
    suspend fun catalogue(): List<Control> = withContext(Dispatchers.IO) {
        val s = app.services
        val out = ArrayList<Control>()
        out += Control("remote", "Remote Mode", toggle = true, on = remoteActivity?.get() != null, icon = "settings_remote")
        runCatching { s.tagRepository.getTagsOnce() }.getOrDefault(emptyList()).filter { it.enabled }.forEach { t ->
            val power = t.targetType.name == "DEVICE" && t.actionType.name in setOf("TURN_ON", "TURN_OFF", "TOGGLE")
            out += Control(
                "item:${t.tagId}", t.friendlyName, toggle = power, on = if (power) t.lastKnownState == 1 else null,
                icon = BandIcons.forItem(t.targetType.name, t.deviceId, t.friendlyName, t.iconKey)
            )
        }
        val bpm = runCatching { s.lastDoseClient.liveHeartRate() }.getOrNull()
        out += Control("hr", "Heart rate", toggle = false, on = null, sub = bpm?.let { "$it BPM" } ?: "No live HR", icon = "monitor_heart")
        val last = runCatching { s.tapLogDao.getRecentLogs(1).first().firstOrNull() }.getOrNull()
        out += Control("last", "Repeat last", toggle = false, on = null, sub = last?.tagName ?: "Nothing yet", icon = "replay")
        if (s.pcRelay.isPaired()) PC_PAD.forEach { (action, label) ->
            out += Control("pc:$action", label, toggle = false, on = null, icon = BandIcons.forAction(action) ?: "computer")
        }
        PREFS.forEach { p ->
            out += Control(
                p.id, p.label, toggle = true, on = runCatching { p.get(s.preferences).first() }.getOrNull(),
                icon = BandIcons.matchName(p.label) ?: "tune"
            )
        }
        out.map { c -> iconOverride(c.id)?.let { c.copy(icon = it) } ?: c }
    }

    /** The owner's chosen order, or a sensible default: Remote Mode, AOD favourites, HR, repeat, then settings. */
    suspend fun order(all: List<Control>): List<String> {
        prefs().getString("order", null)?.let { saved ->
            var ids = saved.split('\n')
            // 0.6.7: Back / Forward 5 s joined the band. Put them right after the PC media tiles the owner already uses.
            if (!prefs().getBoolean("migrated.seek", false) && all.any { it.id == SEEK_BACK }) {
                val media = setOf("play_pause", "vol_plus", "vol_minus", "volume_off")
                val at = ids.indexOfLast { id -> all.firstOrNull { it.id == id }?.icon in media }
                ids = ids.toMutableList().apply {
                    addAll(if (at >= 0) at + 1 else size, listOf(SEEK_BACK, SEEK_FORWARD).filter { it !in this })
                }
                prefs().edit().putString("order", ids.joinToString("\n")).putBoolean("migrated.seek", true).apply()
            }
            return ids.filter { id -> all.any { it.id == id } }
        }
        val favourites = runCatching { app.services.preferences.aodFavourites.first() }.getOrDefault(emptyList())
            .map { "item:$it" }.filter { id -> all.any { it.id == id } }
        val seek = listOf(SEEK_BACK, SEEK_FORWARD).filter { id -> all.any { it.id == id } }
        return (listOf("remote") + favourites + seek + listOf("hr", "last") + PREFS.map { it.id }).distinct()
    }

    fun saveOrder(ids: List<String>) {
        prefs().edit().putString("order", ids.joinToString("\n")).apply()
        pushState()
    }

    // ---- band look: sent inside every state; band 1.5+ applies it, older band apps ignore it ----

    object Look {
        const val LAYOUT = "look.layout"
        const val ACCENT = "look.accent"
        const val HR = "look.hr"
        const val LABELS = "look.labels"
        const val AWAKE = "look.awake"
        const val HAPTICS = "look.haptics"
        const val SIZE = "look.size"
        val SIZES = listOf("standard", "large", "xl")
        val LAYOUTS = listOf("grid", "compact", "list")
        val HR_SIZES = listOf("large", "small", "hidden")
        /** Same keys and colours as the band's accent classes (TapRelay_Band9 index.ux). */
        val ACCENTS = linkedMapOf(
            "teal" to 0xFF5BE7D6, "blue" to 0xFF5AA9FF, "purple" to 0xFFB18CFF, "pink" to 0xFFFF7AC6,
            "red" to 0xFFFF5A5F, "orange" to 0xFFFFA24C, "green" to 0xFF7BE36B, "white" to 0xFFF2F5F7
        )
    }

    fun look(key: String, default: String): String = prefs().getString(key, null) ?: default
    fun lookFlag(key: String): Boolean = prefs().getBoolean(key, true)

    fun setLook(key: String, value: String) { prefs().edit().putString(key, value).apply(); pushState() }
    fun setLookFlag(key: String, value: Boolean) { prefs().edit().putBoolean(key, value).apply(); pushState() }

    private fun lookJson() = JSONObject()
        .put("layout", look(Look.LAYOUT, "grid").takeIf { it in Look.LAYOUTS } ?: "grid")
        .put("accent", look(Look.ACCENT, "teal").takeIf { it in Look.ACCENTS } ?: "teal")
        .put("hr", look(Look.HR, "large").takeIf { it in Look.HR_SIZES } ?: "large")
        .put("labels", lookFlag(Look.LABELS))
        .put("awake", lookFlag(Look.AWAKE))
        .put("haptics", lookFlag(Look.HAPTICS))
        .put("size", look(Look.SIZE, "large").takeIf { it in Look.SIZES } ?: "large")

    // ---- transport ----

    private fun connect() {
        main.removeCallbacksAndMessages(null)
        runCatching {
            Wearable.getNodeApi(sdk).connectedNodes
                .addOnSuccessListener { nodes ->
                    if (nodes.isNullOrEmpty()) { Log.i(TAG, "no band connected"); retry() } else nodes.forEach { listen(it.id) }
                }
                .addOnFailureListener { Log.w(TAG, "nodes failed: $it"); retry() }
        }.onFailure { retry() }
    }

    private fun retry() {
        main.removeCallbacksAndMessages(null)
        main.postDelayed({ connect() }, RETRY_MS)
    }

    private fun listen(nodeId: String) {
        if (synchronized(listening) { !listening.add(nodeId) }) return
        val auth = Wearable.getAuthApi(sdk)
        auth.checkPermission(nodeId, Permission.DEVICE_MANAGER)
            .addOnSuccessListener { granted ->
                Log.i(TAG, "device permission granted=$granted")
                if (granted != true && !permissionAsked) {
                    permissionAsked = true
                    auth.requestPermission(nodeId, Permission.DEVICE_MANAGER)
                }
            }
            .addOnFailureListener { Log.w(TAG, "permission check failed: $it") }
        Wearable.getMessageApi(sdk).addListener(nodeId) { did, bytes -> handle(did, bytes) }
            .addOnSuccessListener {
                Log.i(TAG, "listening to band")
                Wearable.getNodeApi(sdk).isWearAppInstalled(nodeId)
                    .addOnSuccessListener { Log.i(TAG, "band app installed=$it") }
                    .addOnFailureListener { Log.w(TAG, "band app check failed: $it") }
                sendState(nodeId)
            }
            .addOnFailureListener {
                // "you have registered": the SDK still holds our listener (e.g. after a reconnect), so the
                // link is fine - keep the node and keep talking instead of going silent until a restart.
                if (it.message?.contains("registered", ignoreCase = true) == true) {
                    Log.i(TAG, "listener already registered")
                    sendState(nodeId)
                    return@addOnFailureListener
                }
                Log.w(TAG, "listener failed: $it")
                synchronized(listening) { listening.remove(nodeId) }
                retry()
            }
    }

    private fun pushState() {
        if (!::app.isInitialized) return
        synchronized(listening) { listening.toList() }.forEach { sendState(it) }
    }

    private fun handle(nodeId: String, bytes: ByteArray) {
        val msg = runCatching { JSONObject(String(bytes, Charsets.UTF_8)) }.getOrNull() ?: return
        val seq = msg.optInt("seq", 0)
        val type = msg.optString("type")
        Log.i(TAG, "band -> $type")
        when (type) {
            "hello" -> { lastStateText.remove(nodeId); sendState(nodeId) }
            "ping" -> send(nodeId, JSONObject().put("type", "pong").put("seq", seq))
            "cmd" -> command(nodeId, seq, msg.optString("id"))
            "detail" -> scope.launch { detailJson(msg.optString("id"))?.let { send(nodeId, it) } }
            "run" -> command(nodeId, seq, "item:" + msg.optString("id"))
        }
    }

    private fun command(nodeId: String, seq: Int, id: String) {
        send(nodeId, JSONObject().put("type", "ack").put("seq", seq))
        scope.launch {
            val base = id.substringBefore('|')
            val op = id.substringAfter('|', "").ifEmpty { null }
            // Authorise against the list last sent to the band (refreshed every 8 s) so a tap runs at
            // once; only an id that is not on it pays for a fresh catalogue.
            val allowed = base in bandIds || catalogue().let { all -> base in order(all) }
            val (ok, text) = runCatching {
                when {
                    !allowed -> false to "Not on the band list"
                    op == null -> execute(base)
                    op !in detailOps[base].orEmpty() -> false to "Hold the tile again"
                    base == "remote" || base.startsWith("pref:") ->
                        if (lastControls[base]?.on == (op == "on")) true to "Already $op" else execute(base)
                    base == "hr" || base == "last" -> execute(base)
                    else -> BandDetail.run(app.services, base, op)
                }
            }.getOrElse { false to "Failed" }
            Log.i(TAG, "cmd ${base.substringBefore(':')}${if (op != null) " detail" else ""} ok=$ok")
            send(nodeId, JSONObject().put("type", "result").put("seq", seq).put("ok", ok).put("text", text))
            sendStateNow(nodeId)
            if (op != null) detailJson(base)?.let { send(nodeId, it) }
        }
    }

    private suspend fun execute(id: String): Pair<Boolean, String> {
        val s = app.services
        return when {
            id == "remote" -> {
                val open = remoteActivity?.get()
                if (open != null) {
                    withContext(Dispatchers.Main) { open.finish() }
                    repeat(20) { if (remoteActivity?.get() == null) return true to "Remote Mode off"; delay(100) }
                    false to "Remote Mode still open"
                } else {
                    withContext(Dispatchers.Main) { app.startActivity(RemoteModeActivity.intent(app)) }
                    repeat(20) { if (remoteActivity?.get() != null) return true to "Remote Mode on"; delay(100) }
                    false to "Phone blocked opening"
                }
            }
            id.startsWith("item:") -> s.actionExecutor.executeAndAwait(id.removePrefix("item:"), debounce = false).let { it.success to it.summary }
            id.startsWith("pc:") -> id.removePrefix("pc:").let { a -> BandDetail.pc(s, a, null, PcRelayAction.label(a)) }
            id == "hr" -> s.lastDoseClient.liveHeartRate().let { (it != null) to (it?.let { b -> "$b BPM" } ?: "No live HR") }
            id == "last" -> {
                val log = s.tapLogDao.getRecentLogs(1).first().firstOrNull() ?: return false to "Nothing to repeat"
                s.actionExecutor.executeAndAwait(log.tagId, debounce = false).let { it.success to it.summary }
            }
            else -> {
                val p = PREFS.firstOrNull { it.id == id } ?: return false to "Unknown control"
                val value = !p.get(s.preferences).first()
                p.set(s.preferences, value)
                true to "${p.label} ${if (value) "on" else "off"}"
            }
        }
    }

    private fun sendState(nodeId: String) {
        scope.launch { sendStateNow(nodeId) }
    }

    private val lastStateText = ConcurrentHashMap<String, String>()
    private val lastHr = ConcurrentHashMap<String, Int>()
    private val quietTicks = ConcurrentHashMap<String, Int>()

    /**
     * Keeps the Bluetooth link free for taps: the full ~3 KB state only goes out when something on it
     * changed (or every 6th tick as a safety net); a new pulse alone is a tiny {type:hr}; otherwise a
     * {type:pong} keeps the link warm. Band 1.7.2+ answers a pong with hello when it has no tiles yet.
     */
    private suspend fun sendStateNow(nodeId: String) {
        val json = stateJson()
        val hr = json.optInt("hr", 0)
        json.remove("hr")
        val text = json.toString()
        val ticks = (quietTicks[nodeId] ?: 0) + 1
        when {
            lastStateText[nodeId] != text || ticks >= 6 -> {
                lastStateText[nodeId] = text
                lastHr[nodeId] = hr
                quietTicks[nodeId] = 0
                send(nodeId, json.put("hr", hr))
            }
            lastHr[nodeId] != hr -> {
                lastHr[nodeId] = hr
                quietTicks[nodeId] = ticks
                send(nodeId, JSONObject().put("type", "hr").put("hr", hr))
            }
            else -> {
                quietTicks[nodeId] = ticks
                send(nodeId, JSONObject().put("type", "pong"))
            }
        }
    }

    private suspend fun stateJson(): JSONObject {
        val raw = catalogue()
        val ids = order(raw)
        val all = withDistinctIcons(raw, ids)
        bandIds = ids.toSet()
        lastControls = all.associateBy { it.id }
        val controls = JSONArray()
        ids.mapNotNull { id -> lastControls[id] }.take(MAX_CONTROLS).forEach { c ->
            controls.put(
                JSONObject().put("id", c.id).put("label", c.label.take(24)).put("icon", c.icon)
                    .put("kind", if (c.toggle) "toggle" else "action")
                    .put("on", c.on ?: JSONObject.NULL).put("sub", c.sub.take(24))
            )
        }
        val hr = all.firstOrNull { it.id == "hr" }?.sub?.removeSuffix(" BPM")?.toIntOrNull() ?: 0
        return JSONObject().put("type", "state").put("remote", remoteActivity?.get() != null).put("hr", hr)
            .put("home", HOME_COUNT).put("controls", controls).put("look", lookJson())
    }

    /** The long-press controls for one tile, and the ops the band may send back for it. */
    private suspend fun detailJson(id: String): JSONObject? {
        if (id !in bandIds) return null
        val control = lastControls[id] ?: return null
        val built = runCatching { BandDetail.build(app.services, control) }
            .onFailure { Log.w(TAG, "detail failed: $it") }.getOrNull() ?: return null
        detailOps[id] = built.ops
        return built.json
    }

    private fun send(nodeId: String, json: JSONObject) {
        runCatching {
            Wearable.getMessageApi(sdk).sendMessage(nodeId, json.toString().toByteArray(Charsets.UTF_8))
                .addOnSuccessListener { Log.i(TAG, "sent ${json.optString("type")}") }
                .addOnFailureListener { Log.w(TAG, "send ${json.optString("type")} failed: $it") }
        }.onFailure { Log.w(TAG, "send threw: $it") }
    }
}
