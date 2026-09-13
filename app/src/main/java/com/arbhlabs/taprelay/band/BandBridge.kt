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
    private const val MAX_CONTROLS = 16

    data class Control(val id: String, val label: String, val toggle: Boolean, val on: Boolean?, val sub: String = "", val icon: String = "bolt")

    /** Tile icons bundled in the band app (TapRelay_Band9/tools/make-icons.mjs). Keep in sync. */
    val ICON_KEYS = listOf(
        "bolt", "light", "lamp", "room", "plug", "scene", "air", "fan", "heat", "tv", "pc", "media", "music", "volume",
        "moon", "sun", "door", "lock", "remote", "heart", "repeat", "aod", "haptic", "game", "pill", "phone", "bell", "tune"
    )

    private val ITEM_ICON = mapOf(
        "lamp" to "light", "room" to "room", "plug" to "plug", "switch" to "bolt", "scene" to "scene", "air" to "air", "lastdose" to "pill"
    )

    fun iconOverride(id: String): String? = prefs().getString("icon:$id", null)?.takeIf { it in ICON_KEYS }

    fun setIcon(id: String, key: String) {
        prefs().edit().putString("icon:$id", key).apply()
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
        out += Control("remote", "Remote Mode", toggle = true, on = remoteActivity?.get() != null, icon = "remote")
        runCatching { s.tagRepository.getTagsOnce() }.getOrDefault(emptyList()).filter { it.enabled }.forEach { t ->
            val power = t.targetType.name == "DEVICE" && t.actionType.name in setOf("TURN_ON", "TURN_OFF", "TOGGLE")
            val icon = when (t.targetType.name) {
                "PC_RELAY" -> "pc"
                "LASTDOSE_LOG" -> "pill"
                "SCENE" -> "scene"
                "DEVICE" -> ITEM_ICON[t.iconKey] ?: "bolt"
                else -> ITEM_ICON[t.iconKey] ?: "phone"
            }
            out += Control("item:${t.tagId}", t.friendlyName, toggle = power, on = if (power) t.lastKnownState == 1 else null, icon = icon)
        }
        val bpm = runCatching { s.lastDoseClient.liveHeartRate() }.getOrNull()
        out += Control("hr", "Heart rate", toggle = false, on = null, sub = bpm?.let { "$it BPM" } ?: "No live HR", icon = "heart")
        val last = runCatching { s.tapLogDao.getRecentLogs(1).first().firstOrNull() }.getOrNull()
        out += Control("last", "Repeat last", toggle = false, on = null, sub = last?.tagName ?: "Nothing yet", icon = "repeat")
        PREFS.forEach { p ->
            val icon = when {
                p.id.startsWith("pref:aod") || p.id == "pref:autodim" -> "aod"
                p.id == "pref:rumble" -> "game"
                else -> "haptic"
            }
            out += Control(p.id, p.label, toggle = true, on = runCatching { p.get(s.preferences).first() }.getOrNull(), icon = icon)
        }
        out.map { c -> iconOverride(c.id)?.let { c.copy(icon = it) } ?: c }
    }

    /** The owner's chosen order, or a sensible default: Remote Mode, AOD favourites, HR, repeat, then settings. */
    suspend fun order(all: List<Control>): List<String> {
        prefs().getString("order", null)?.let { saved -> return saved.split('\n').filter { id -> all.any { it.id == id } } }
        val favourites = runCatching { app.services.preferences.aodFavourites.first() }.getOrDefault(emptyList())
            .map { "item:$it" }.filter { id -> all.any { it.id == id } }
        return (listOf("remote") + favourites + listOf("hr", "last") + PREFS.map { it.id }).distinct()
    }

    fun saveOrder(ids: List<String>) {
        prefs().edit().putString("order", ids.joinToString("\n")).apply()
        pushState()
    }

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
            "hello" -> sendState(nodeId)
            "ping" -> send(nodeId, JSONObject().put("type", "pong").put("seq", seq))
            "cmd" -> command(nodeId, seq, msg.optString("id"))
            "run" -> command(nodeId, seq, "item:" + msg.optString("id"))
        }
    }

    private fun command(nodeId: String, seq: Int, id: String) {
        send(nodeId, JSONObject().put("type", "ack").put("seq", seq))
        scope.launch {
            val all = catalogue()
            val (ok, text) = if (id in order(all)) {
                runCatching { execute(id) }.getOrElse { false to "Failed" }
            } else false to "Not on the band list"
            Log.i(TAG, "cmd ${id.substringBefore(':')} ok=$ok")
            send(nodeId, JSONObject().put("type", "result").put("seq", seq).put("ok", ok).put("text", text))
            sendState(nodeId)
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
            id.startsWith("item:") -> s.actionExecutor.executeAndAwait(id.removePrefix("item:")).let { it.success to it.summary }
            id == "hr" -> s.lastDoseClient.liveHeartRate().let { (it != null) to (it?.let { b -> "$b BPM" } ?: "No live HR") }
            id == "last" -> {
                val log = s.tapLogDao.getRecentLogs(1).first().firstOrNull() ?: return false to "Nothing to repeat"
                s.actionExecutor.executeAndAwait(log.tagId).let { it.success to it.summary }
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
        scope.launch {
            val all = catalogue()
            val controls = JSONArray()
            order(all).mapNotNull { id -> all.firstOrNull { it.id == id } }.take(MAX_CONTROLS).forEach { c ->
                controls.put(
                    JSONObject().put("id", c.id).put("label", c.label.take(24)).put("icon", c.icon)
                        .put("kind", if (c.toggle) "toggle" else "action")
                        .put("on", c.on ?: JSONObject.NULL).put("sub", c.sub.take(24))
                )
            }
            val hr = all.firstOrNull { it.id == "hr" }?.sub?.removeSuffix(" BPM")?.toIntOrNull() ?: 0
            send(
                nodeId,
                JSONObject().put("type", "state").put("remote", remoteActivity?.get() != null).put("hr", hr)
                    .put("home", HOME_COUNT).put("controls", controls)
            )
        }
    }

    private fun send(nodeId: String, json: JSONObject) {
        runCatching {
            Wearable.getMessageApi(sdk).sendMessage(nodeId, json.toString().toByteArray(Charsets.UTF_8))
                .addOnSuccessListener { Log.i(TAG, "sent ${json.optString("type")}") }
                .addOnFailureListener { Log.w(TAG, "send ${json.optString("type")} failed: $it") }
        }.onFailure { Log.w(TAG, "send threw: $it") }
    }
}
