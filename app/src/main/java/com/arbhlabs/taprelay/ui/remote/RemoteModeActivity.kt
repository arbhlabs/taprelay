package com.arbhlabs.taprelay.ui.remote

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.arbhlabs.taprelay.TapRelayApplication
import com.arbhlabs.taprelay.controller.ControllerManager
import com.arbhlabs.taprelay.data.local.entity.TagEntity
import com.arbhlabs.taprelay.data.prefs.AodDensity
import com.arbhlabs.taprelay.data.prefs.AodHrColor
import com.arbhlabs.taprelay.data.prefs.AodHrStyle
import com.arbhlabs.taprelay.data.prefs.layout
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.arbhlabs.taprelay.di.ServiceLocator
import com.arbhlabs.taprelay.execution.TapFeedback
import com.arbhlabs.taprelay.execution.lastdose.LastDoseLatestEvent
import com.arbhlabs.taprelay.trigger.ActivationPresenter
import com.arbhlabs.taprelay.trigger.TriggerSource
import com.arbhlabs.taprelay.ui.MainActivity
import com.arbhlabs.taprelay.ui.quick.QuickControlsContent
import com.arbhlabs.taprelay.ui.quick.QuickControlsSession
import com.arbhlabs.taprelay.ui.theme.TapRelayTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The always-on face: TapRelay as a physical remote that happens to live on a phone.
 *
 * Two jobs, and the design follows from both.
 *
 * **It is the only place a game controller can be heard.** Android delivers controller input
 * through `InputDispatcher` to the focused window only, so a service has nothing to listen with,
 * `MediaSession` sees media keys alone, and a non-focusable overlay receives nothing. The two ways
 * round that - an AccessibilityService, or an overlay that steals focus from every other app - are
 * a Play policy violation and a broken phone respectively. So this is a real, focused activity
 * that shows over the lock screen, and that is not a workaround: it is the compliant answer.
 *
 * **It is meant to be left on for hours**, propped up on a desk. So it holds the display awake,
 * then dozes to the panel's own dimmest backlight, asks the compositor for its lowest frame rate,
 * redraws once a minute instead of once a second, hides everything but the clock while dozing,
 * and drifts within safe bounds so nothing burns in. Waking is a deliberate slide, never a stray
 * tap, and running a favourite takes two taps unless the owner has said otherwise - a sleeve
 * brushing the glass must not turn the bedroom lights off.
 *
 * Favourites run through `TriggerRouter` exactly as an NFC tap or a controller button does. There
 * is no execution code on this screen at all.
 */
class RemoteModeActivity : ComponentActivity() {

    private lateinit var services: ServiceLocator
    private lateinit var controllerManager: ControllerManager
    private lateinit var quickControls: QuickControlsSession

    private val feedback = MutableStateFlow<TapFeedback?>(null)
    private val result = MutableStateFlow<AodOutcome?>(null)

    /** Bumped when something settles, so LastDose-backed logs re-read at once instead of in 2 s. */
    private val refresh = MutableStateFlow(0)

    /** What the face shows after something ran, and when it should fade back to ambient. */
    private data class AodOutcome(
        val success: Boolean,
        val title: String,
        val detail: String,
        val at: Long = System.currentTimeMillis()
    )

    private val presenter = object : ActivationPresenter {
        override fun openItem(tagId: String) {
            startActivity(MainActivity.openItemIntent(this@RemoteModeActivity, tagId))
        }

        // Drawn in this window rather than a dialog or another activity, so the controller
        // keeps working while the surface is up.
        override fun openQuickControls(tagId: String) {
            controllerManager.selectAutomaticLight(tagId)
            quickControls.open(tagId)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge to edge, then `safeDrawingPadding` on the content: that is what keeps the face
        // clear of the camera cutout and the gesture bar without a single hardcoded Pixel dimension.
        enableEdgeToEdge()
        services = (application as TapRelayApplication).services
        controllerManager = services.controllerManager
        quickControls = QuickControlsSession(services, lifecycleScope)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val tags: StateFlow<List<TagEntity>> =
            services.tagRepository.getAllTags()
                .stateIn(lifecycleScope, SharingStarted.Eagerly, emptyList())
        val favouriteIds: StateFlow<List<String>> = services.preferences.aodFavourites
            .stateIn(lifecycleScope, SharingStarted.Eagerly, emptyList())
        val autoDim: StateFlow<Boolean> = services.preferences.remoteAutoDim
            .stateIn(lifecycleScope, SharingStarted.Eagerly, true)
        val densityPref: StateFlow<AodDensity> = services.preferences.aodDensity
            .stateIn(lifecycleScope, SharingStarted.Eagerly, AodDensity.NORMAL)
        val scalePref = services.preferences.aodScale
            .stateIn(lifecycleScope, SharingStarted.Eagerly, 1f)
        val showClockPref: StateFlow<Boolean> = services.preferences.aodShowClock
            .stateIn(lifecycleScope, SharingStarted.Eagerly, true)
        val confirmPref: StateFlow<Boolean> = services.preferences.aodConfirmActions
            .stateIn(lifecycleScope, SharingStarted.Eagerly, true)
        val monoPref: StateFlow<Boolean> = services.preferences.aodMonochrome
            .stateIn(lifecycleScope, SharingStarted.Eagerly, false)
        val hrStylePref: StateFlow<AodHrStyle> = services.preferences.aodHrStyle
            .stateIn(lifecycleScope, SharingStarted.Eagerly, AodHrStyle.HERO)
        val hrColorPref: StateFlow<AodHrColor> = services.preferences.aodHrColor
            .stateIn(lifecycleScope, SharingStarted.Eagerly, AodHrColor.RED)
        val logHrPref: StateFlow<Boolean> = services.preferences.aodLogHeartRate
            .stateIn(lifecycleScope, SharingStarted.Eagerly, true)

        setContent {
            TapRelayTheme {
                val controllers by controllerManager.connectedControllers.collectAsState()
                val items by tags.collectAsState()
                val favourites by favouriteIds.collectAsState()
                val pill by feedback.collectAsState()
                val outcome by result.collectAsState()
                val quick by quickControls.state.collectAsState()
                val lastInput by controllerManager.lastInput.collectAsState()
                val automaticControls by controllerManager.automaticLightControlState.collectAsState()
                val dimWhenIdle by autoDim.collectAsState()
                val aod by densityPref.collectAsState()
                val aodScale by scalePref.collectAsState()
                val showClock by showClockPref.collectAsState()
                val confirmFirst by confirmPref.collectAsState()
                val monochrome by monoPref.collectAsState()
                val hrStyle by hrStylePref.collectAsState()
                val hrColorChoice by hrColorPref.collectAsState()
                val logHeartRate by logHrPref.collectAsState()
                val refreshTick by refresh.collectAsState()

                var awake by remember { mutableLongStateOf(System.currentTimeMillis()) }
                var now by remember { mutableStateOf(LocalDateTime.now()) }
                var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
                var shift by remember { mutableStateOf(0) }
                var armedId by remember { mutableStateOf<String?>(null) }
                var runningId by remember { mutableStateOf<String?>(null) }
                var heartRate by remember { mutableStateOf<Int?>(null) }
                var latestLastDose by remember { mutableStateOf<Map<Long, LastDoseLatestEvent>>(emptyMap()) }

                val bright = !dimWhenIdle ||
                    quick.open ||
                    runningId != null ||
                    System.currentTimeMillis() - awake < BRIGHT_MS ||
                    (lastInput?.second ?: 0L) > System.currentTimeMillis() - BRIGHT_MS
                val dozing = !bright

                // TapRelay's own history for every favourite: a Room flow, so a run from NFC, a
                // controller, a widget or this face shows up here the moment its row is written.
                // Re-keyed at midnight so "today" counts reset without leaving the face.
                val today = now.toLocalDate()
                val tapSummaries by remember(favourites, today) {
                    services.tapLogDao.summaries(
                        favourites,
                        today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    )
                }.collectAsState(initial = emptyList())

                // LastDose owns its own logs (they can be made inside LastDose too), and the live
                // pulse comes from it as well. Read-only IPC: every 2 s while awake, every 30 s
                // dozing, and immediately after something on this face ran.
                LaunchedEffect(items, favourites, dozing, refreshTick) {
                    while (true) {
                        val (pulse, events) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            val pulse = services.lastDoseClient.liveHeartRate()
                            val current = mutableMapOf<Long, LastDoseLatestEvent>()
                            items.asSequence()
                                .filter { it.enabled && it.isLastDose && it.tagId in favourites }
                                .mapNotNull { it.lastDoseItemId }
                                .distinct()
                                .forEach { itemId ->
                                    services.lastDoseClient.latestEvent(itemId)?.let { current[itemId] = it }
                                }
                            pulse to current
                        }
                        heartRate = pulse
                        latestLastDose = events
                        delay(if (dozing) 30_000L else 2_000L)
                    }
                }

                // Clock tick, and the burn-in drift that rides along with it. While dozing this
                // wakes only on the minute, so the panel is asked to draw about sixty times less.
                LaunchedEffect(dozing) {
                    while (true) {
                        now = LocalDateTime.now()
                        nowMillis = System.currentTimeMillis()
                        shift = now.minute % 6
                        delay(
                            if (dozing) 60_000L - (System.currentTimeMillis() % 60_000L)
                            else 1_000L
                        )
                    }
                }

                LaunchedEffect(bright, dimWhenIdle) {
                    applyDisplay(low = dozing)
                    if (bright && dimWhenIdle) {
                        delay(BRIGHT_MS)
                        awake = 0L
                    }
                }

                // An armed tile disarms itself, so a half-press never sits waiting indefinitely.
                LaunchedEffect(armedId) {
                    if (armedId != null) {
                        delay(ARM_MS)
                        armedId = null
                    }
                }

                // The result is a moment, not a state: it clears itself and the face returns.
                LaunchedEffect(outcome?.at) {
                    if (outcome != null) {
                        delay(RESULT_MS)
                        result.value = null
                    }
                }

                // Pixel shift inside safe bounds so a fixed face never burns into the panel.
                val dx by animateDpAsState(((shift % 3) * 4 - 4).dp, tween(2_000), label = "dx")
                val dy by animateDpAsState(((shift / 3) * 6 - 3).dp, tween(2_000), label = "dy")
                // Fewer lit pixels, and dimmer ones, is what actually saves an OLED.
                val contentAlpha by animateFloatAsState(
                    if (bright) 1f else 0.34f, tween(1_500), label = "alpha"
                )

                val accent = if (monochrome) Color.White else MaterialTheme.colorScheme.primary
                val heartColor = when {
                    monochrome || hrColorChoice == AodHrColor.WHITE -> Color.White
                    hrColorChoice == AodHrColor.ACCENT -> accent
                    else -> Color(hrColorChoice.argb)
                }
                val resolved = remember(items, favourites) { resolveFavourites(items, favourites) }
                val logs = remember(resolved, tapSummaries, latestLastDose) {
                    val byTag = tapSummaries.associateBy { it.tagId }
                    resolved.associate { tag ->
                        tag.tagId to AodLogState.resolve(
                            tag,
                            byTag[tag.tagId],
                            tag.lastDoseItemId?.let { latestLastDose[it] }
                        )
                    }
                }
                val pulse = heartRate.takeIf { hrStyle != AodHrStyle.OFF }
                val layout = remember(aod, aodScale) { aod.layout(aodScale) }

                Surface(
                    color = Color.Black,
                    contentColor = Color.White,
                    modifier = Modifier.fillMaxSize()
                ) {
                    var dragged by remember { mutableFloatStateOf(0f) }
                    BoxWithConstraints(
                        Modifier
                            .fillMaxSize()
                            // Slide, never tap: a pocket or a sleeve brushing the glass must not
                            // light the panel back up.
                            .pointerInput(dozing) {
                                detectVerticalDragGestures(
                                    onDragStart = { dragged = 0f },
                                    onDragEnd = {
                                        if (kotlin.math.abs(dragged) > WAKE_SLIDE_PX) {
                                            awake = System.currentTimeMillis()
                                        }
                                        dragged = 0f
                                    },
                                    onDragCancel = { dragged = 0f }
                                ) { _, delta -> dragged += delta }
                            }
                    ) {
                        val landscape = maxWidth > maxHeight
                        val sheetMax = maxHeight * 0.92f

                        Column(
                            Modifier
                                .fillMaxSize()
                                .safeDrawingPadding()
                                .offset(x = dx, y = dy)
                                .alpha(contentAlpha)
                                .padding(horizontal = layout.sidePadding, vertical = 10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.Top) {
                                if (showClock) {
                                    AodClock(now, if (landscape) layout.clockSp * 3 / 4 else layout.clockSp, dozing)
                                }
                                Spacer(Modifier.weight(1f))
                                if (!dozing) {
                                    IconButton(onClick = { finish() }) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Leave the AOD",
                                            tint = Color.White.copy(alpha = 0.35f)
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(if (dozing) 14.dp else layout.gap))

                            if (hrStyle == AodHrStyle.HERO && pulse != null) {
                                AodHeartRate(
                                    bpm = pulse,
                                    color = heartColor,
                                    dim = dozing,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                            }

                            AodStatusLine(
                                controllerName = controllers.firstOrNull()?.name,
                                battery = controllers.firstOrNull()?.batteryPercent,
                                connected = controllers.isNotEmpty(),
                                accent = accent,
                                automaticControls = automaticControls.description,
                                compactHeartRate = pulse.takeIf { hrStyle == AodHrStyle.COMPACT },
                                heartColor = heartColor
                            )

                            if (dozing) {
                                // The logs stay: how long since the last one is the reason to
                                // glance at an always-on face at all. Timers only, no controls.
                                Spacer(Modifier.height(16.dp))
                                if (resolved.isNotEmpty()) {
                                    Favourites(
                                        favourites = resolved,
                                        logs = logs,
                                        armedId = null,
                                        runningId = null,
                                        nowMillis = nowMillis,
                                        accent = accent,
                                        heartColor = heartColor,
                                        showHeartRate = logHeartRate,
                                        dozing = true,
                                        landscape = landscape,
                                        onPress = {},
                                        modifier = Modifier.weight(1f)
                                    )
                                } else {
                                    Spacer(Modifier.weight(1f))
                                }
                                Text(
                                    "Slide to wake",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.28f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            } else {
                                Spacer(Modifier.height(layout.gap + 4.dp))

                                val current = outcome
                                if (current != null) {
                                    Spacer(Modifier.weight(0.6f))
                                    AodResult(
                                        success = current.success,
                                        title = current.title,
                                        detail = current.detail,
                                        accent = accent
                                    )
                                    Spacer(Modifier.weight(1f))
                                } else if (resolved.isEmpty()) {
                                    AodEmpty(onOpenSettings = {
                                        startActivity(
                                            Intent(this@RemoteModeActivity, MainActivity::class.java)
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    })
                                    Spacer(Modifier.weight(1f))
                                } else {
                                    Favourites(
                                        favourites = resolved,
                                        logs = logs,
                                        armedId = armedId,
                                        runningId = runningId,
                                        nowMillis = nowMillis,
                                        accent = accent,
                                        heartColor = heartColor,
                                        showHeartRate = logHeartRate,
                                        dozing = false,
                                        landscape = landscape,
                                        modifier = Modifier.weight(1f),
                                        onPress = { tag ->
                                            awake = System.currentTimeMillis()
                                            when {
                                                !confirmFirst || armedId == tag.tagId -> {
                                                    armedId = null
                                                    runningId = tag.tagId
                                                    fire(tag) { runningId = null }
                                                }
                                                else -> armedId = tag.tagId
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        // Quick Controls, in this window so the controller keeps being heard.
                        if (quick.open) {
                            BackHandler { quickControls.close() }
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.72f))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { quickControls.close() },
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                AnimatedVisibility(
                                    visible = true,
                                    enter = slideInVertically { it } + fadeIn(),
                                    exit = slideOutVertically { it } + fadeOut()
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        tonalElevation = 3.dp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {}
                                    ) {
                                        Column(
                                            Modifier
                                                .fillMaxWidth()
                                                // Landscape leaves little height; the panel
                                                // scrolls inside this cap.
                                                .heightIn(max = sheetMax)
                                        ) {
                                            Spacer(Modifier.height(18.dp))
                                            QuickControlsContent(quickControls)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Runs a favourite the same way every other trigger does.
     *
     * The pill is suppressed here: the face has its own, larger result, and two messages about one
     * press is exactly the clutter this screen is meant not to have.
     */
    private fun fire(tag: TagEntity, onSettled: () -> Unit) {
        services.triggerRouter.fire(
            tagId = tag.tagId,
            source = TriggerSource.IN_APP,
            presenter = presenter
        ) { fb ->
            if (fb.pending) return@fire
            onSettled()
            refresh.value++
            result.value = AodOutcome(
                success = !fb.isError,
                title = tag.friendlyName,
                detail = summaryOf(fb, tag)
            )
        }
    }

    /** "4 actions completed", "3/4 · Air purifier unavailable", "On". */
    private fun summaryOf(fb: TapFeedback, tag: TagEntity): String {
        val progress = fb.progress
        if (progress != null) {
            return if (fb.isError) {
                "${progress.completed}/${progress.total} · " + fb.title.substringAfterLast(" • ")
            } else {
                val word = if (progress.total == 1) "action" else "actions"
                "${progress.completed} $word completed"
            }
        }
        // Everything else already reads well; strip the item name the face is showing anyway.
        return fb.title.removePrefix("${tag.friendlyName} • ")
    }

    /** Favourites in the owner's order, silently dropping any that were deleted. */
    private fun resolveFavourites(items: List<TagEntity>, ids: List<String>): List<TagEntity> =
        ids.mapNotNull { id -> items.firstOrNull { it.tagId == id && it.enabled } }

    /**
     * Backlight and frame rate together.
     *
     * `screenBrightness = 0f` is the panel's own dimmest setting rather than off. The Pixel 7
     * only offers 60 Hz and 90 Hz as app-selectable modes, so the low render rates it really
     * supports (down to 20 Hz) are reached by asking for the low frame-rate *category* on
     * Android 15 and letting the compositor pick - which is also the only sanctioned way to do it.
     */
    private fun applyDisplay(low: Boolean) {
        val lp = window.attributes
        lp.screenBrightness = if (low) 0f else 0.55f
        val d = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display else null
        if (d != null) {
            if (low) {
                val current = d.mode
                val slowest = d.supportedModes
                    .filter {
                        it.physicalWidth == current.physicalWidth &&
                            it.physicalHeight == current.physicalHeight
                    }
                    .minByOrNull { it.refreshRate }
                if (slowest != null) {
                    lp.preferredDisplayModeId = slowest.modeId
                    lp.preferredRefreshRate = slowest.refreshRate
                }
            } else {
                lp.preferredDisplayModeId = 0
                lp.preferredRefreshRate = 0f
            }
        }
        window.attributes = lp
        if (Build.VERSION.SDK_INT >= 35) {
            runCatching {
                window.decorView.requestedFrameRate =
                    if (low) View.REQUESTED_FRAME_RATE_CATEGORY_LOW
                    else View.REQUESTED_FRAME_RATE_CATEGORY_DEFAULT
            }
        }
    }

    override fun onResume() {
        super.onResume()
        controllerManager.onFeedback = { fb ->
            feedback.value = fb
            if (!fb.pending) {
                refresh.value++
                result.value = AodOutcome(
                    success = !fb.isError,
                    title = fb.title.substringBefore(" • ").ifBlank { "Done" },
                    detail = fb.title.substringAfter(" • ", "")
                )
            }
        }
        controllerManager.presenter = presenter
        controllerManager.startListening()
    }

    override fun onPause() {
        super.onPause()
        controllerManager.stopListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        quickControls.close()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        controllerManager.handleKeyEvent(event) || super.dispatchKeyEvent(event)

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        controllerManager.handleGenericMotionEvent(event) || super.dispatchGenericMotionEvent(event)

    companion object {
        private const val BRIGHT_MS = 18_000L

        /** How long an armed favourite waits for its second tap. */
        private const val ARM_MS = 3_500L

        /** How long the result stays before the face returns to ambient. */
        private const val RESULT_MS = 4_000L

        /** How far a finger must travel before the face counts it as a deliberate wake. */
        private const val WAKE_SLIDE_PX = 90f

        fun intent(context: Context): Intent =
            Intent(context, RemoteModeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}

/**
 * The favourites themselves: the first one large, the rest two to a row.
 *
 * The hierarchy is the point. Somebody glancing at a phone propped on a desk should find the one
 * thing they press most without reading anything.
 */
@Composable
private fun Favourites(
    favourites: List<TagEntity>,
    logs: Map<String, AodLogState>,
    armedId: String?,
    runningId: String?,
    nowMillis: Long,
    accent: Color,
    heartColor: Color,
    showHeartRate: Boolean,
    dozing: Boolean,
    landscape: Boolean,
    onPress: (TagEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    fun stateOf(tag: TagEntity) = when (tag.tagId) {
        runningId -> TileState.RUNNING
        armedId -> TileState.ARMED
        else -> TileState.IDLE
    }
    fun labelOf(tag: TagEntity) = tag.friendlyName.ifBlank { tag.lastDoseItemName.orEmpty() }

    // Scrolls inside its own space, so many favourites never push the clock off a short screen.
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(if (dozing) 4.dp else 10.dp)
    ) {
        val primary = favourites.first()
        // Landscape has too little height for the large card, so everything becomes a row.
        if (!landscape) {
            AodLogCard(
                tag = primary,
                label = labelOf(primary),
                log = logs[primary.tagId] ?: AodLogState.NEVER,
                nowMillis = nowMillis,
                state = stateOf(primary),
                accent = accent,
                heartColor = heartColor,
                showHeartRate = showHeartRate,
                dozing = dozing,
                onClick = { onPress(primary) }
            )
        }

        val rest = if (landscape) favourites else favourites.drop(1)
        rest.forEach { tag ->
            AodLogRow(
                tag = tag,
                label = labelOf(tag),
                log = logs[tag.tagId] ?: AodLogState.NEVER,
                nowMillis = nowMillis,
                state = stateOf(tag),
                accent = accent,
                heartColor = heartColor,
                showHeartRate = showHeartRate,
                dozing = dozing,
                onClick = { onPress(tag) }
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}
