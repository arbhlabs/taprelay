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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.arbhlabs.taprelay.TapRelayApplication
import com.arbhlabs.taprelay.controller.ControllerManager
import com.arbhlabs.taprelay.data.local.entity.ControllerMappingEntity
import com.arbhlabs.taprelay.data.local.entity.TagEntity
import com.arbhlabs.taprelay.data.prefs.AodDensity
import com.arbhlabs.taprelay.di.ServiceLocator
import com.arbhlabs.taprelay.domain.model.ActivationMode
import com.arbhlabs.taprelay.execution.TapFeedback
import com.arbhlabs.taprelay.trigger.ActivationPresenter
import com.arbhlabs.taprelay.ui.MainActivity
import com.arbhlabs.taprelay.ui.components.TapRelayPill
import com.arbhlabs.taprelay.ui.iconFor
import com.arbhlabs.taprelay.ui.quick.QuickControlsContent
import com.arbhlabs.taprelay.ui.quick.QuickControlsSession
import com.arbhlabs.taprelay.ui.theme.TapRelayTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Remote Mode: an always-on remote face for TapRelay.
 *
 * Android will not let any app read a game controller in the background. Controller input is
 * delivered by `InputDispatcher` only to the focused window, so a service has nothing to listen
 * with, `MediaSession` only ever sees media keys, and a non-focusable overlay receives nothing.
 * The two ways round that — an AccessibilityService, or an overlay that steals focus from every
 * other app — are a Play policy violation and a broken phone respectively, so TapRelay does
 * neither.
 *
 * Instead this screen is built to be left on for hours: it shows over the lock screen, holds the
 * display awake, then dozes to the panel's own dimmest backlight, asks the compositor for its
 * lowest frame rate, redraws once a minute, and drifts within safe bounds so nothing burns in.
 * Waking is a deliberate slide, never a stray tap. One press on the Quick Settings tile gets here
 * from anywhere without opening the app.
 */
class RemoteModeActivity : ComponentActivity() {

    private lateinit var services: ServiceLocator
    private lateinit var controllerManager: ControllerManager
    private lateinit var quickControls: QuickControlsSession

    private val feedback = MutableStateFlow<TapFeedback?>(null)
    private val lastOutcome = MutableStateFlow<Pair<String, Long>?>(null)

    private val presenter = object : ActivationPresenter {
        override fun openItem(tagId: String) {
            startActivity(MainActivity.openItemIntent(this@RemoteModeActivity, tagId))
        }

        // Drawn in this window rather than a dialog or another activity, so the controller
        // keeps working while the surface is up.
        override fun openQuickControls(tagId: String) = quickControls.open(tagId)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        services = (application as TapRelayApplication).services
        controllerManager = services.controllerManager
        quickControls = QuickControlsSession(services, lifecycleScope)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val mappings: StateFlow<List<ControllerMappingEntity>> =
            services.controllerMappingDao.observeAll()
                .stateIn(lifecycleScope, SharingStarted.Eagerly, emptyList())
        val tags: StateFlow<List<TagEntity>> =
            services.tagRepository.getAllTags()
                .stateIn(lifecycleScope, SharingStarted.Eagerly, emptyList())
        val autoDim: StateFlow<Boolean> = services.preferences.remoteAutoDim
            .stateIn(lifecycleScope, SharingStarted.Eagerly, true)
        val densityPref: StateFlow<AodDensity> = services.preferences.aodDensity
            .stateIn(lifecycleScope, SharingStarted.Eagerly, AodDensity.NORMAL)

        setContent {
            TapRelayTheme {
                val controllers by controllerManager.connectedControllers.collectAsState()
                val rows by mappings.collectAsState()
                val items by tags.collectAsState()
                val pill by feedback.collectAsState()
                val outcome by lastOutcome.collectAsState()
                val quick by quickControls.state.collectAsState()
                val lastInput by controllerManager.lastInput.collectAsState()
                val dimWhenIdle by autoDim.collectAsState()
                val aod by densityPref.collectAsState()

                var awake by remember { mutableLongStateOf(System.currentTimeMillis()) }
                var now by remember { mutableStateOf(LocalDateTime.now()) }
                var shift by remember { mutableStateOf(0) }

                val bright = !dimWhenIdle ||
                    // Never doze underneath an open control panel.
                    quick.open ||
                    System.currentTimeMillis() - awake < BRIGHT_MS ||
                    (lastInput?.second ?: 0L) > System.currentTimeMillis() - BRIGHT_MS
                val dozing = !bright

                // Clock tick, and the burn-in drift that rides along with it. While dozing this
                // wakes only on the minute, so the panel is asked to draw about sixty times less.
                LaunchedEffect(dozing) {
                    while (true) {
                        now = LocalDateTime.now()
                        shift = (now.minute % 6)
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

                // Pixel shift inside safe bounds so a fixed face never burns into the panel.
                val dx by animateDpAsState(((shift % 3) * 4 - 4).dp, tween(2_000), label = "dx")
                val dy by animateDpAsState(((shift / 3) * 6 - 3).dp, tween(2_000), label = "dy")
                // Fewer lit pixels, and dimmer ones, is what actually saves an OLED.
                val contentAlpha by animateFloatAsState(
                    if (bright) 1f else 0.34f, tween(1_500), label = "alpha"
                )

                val accent = MaterialTheme.colorScheme.primary
                val idle = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                val pressed = lastInput
                    ?.takeIf { System.currentTimeMillis() - it.second < PRESS_FLASH_MS }
                    ?.first?.key

                Surface(
                    color = Color.Black,
                    contentColor = MaterialTheme.colorScheme.onBackground,
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
                        val gap = aod.gap
                        val padWidth = if (landscape) maxWidth * 0.42f else maxWidth

                        Box(
                            Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                                .navigationBarsPadding()
                                .offset(x = dx, y = dy)
                                .alpha(contentAlpha)
                                .padding(horizontal = aod.sidePadding, vertical = 8.dp)
                        ) {
                            if (landscape) {
                                // Wide: the always-lit half stays left, the detail scrolls right.
                                Row(Modifier.fillMaxSize()) {
                                    Column(
                                        Modifier
                                            .weight(1f)
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                        ClockBlock(now, aod) { finish() }
                                        Spacer(Modifier.height(gap))
                                        ControllerLine(
                                            name = controllers.firstOrNull()?.name,
                                            battery = controllers.firstOrNull()?.batteryPercent,
                                            connected = controllers.isNotEmpty(),
                                            accent = accent,
                                            idle = idle
                                        )
                                        if (dozing) {
                                            Spacer(Modifier.height(gap))
                                            SlideHint()
                                        }
                                    }
                                    if (!dozing) {
                                        Spacer(Modifier.width(gap))
                                        Column(
                                            Modifier
                                                .weight(1.1f)
                                                .verticalScroll(rememberScrollState())
                                        ) {
                                            Detail(
                                                rows, items, pressed, accent, idle, aod,
                                                padWidth, outcome, now
                                            )
                                        }
                                    }
                                }
                            } else {
                                Column(
                                    Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    ClockBlock(now, aod) { finish() }
                                    Spacer(Modifier.height(gap))
                                    ControllerLine(
                                        name = controllers.firstOrNull()?.name,
                                        battery = controllers.firstOrNull()?.batteryPercent,
                                        connected = controllers.isNotEmpty(),
                                        accent = accent,
                                        idle = idle
                                    )
                                    if (dozing) {
                                        Spacer(Modifier.height(gap + 12.dp))
                                        SlideHint()
                                    } else {
                                        Spacer(Modifier.height(gap))
                                        Detail(
                                            rows, items, pressed, accent, idle, aod,
                                            padWidth, outcome, now
                                        )
                                    }
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

                        TapRelayPill(
                            feedback = pill,
                            onDismiss = { feedback.value = null },
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                }
            }
        }
    }

    /**
     * Backlight and frame rate together.
     *
     * `screenBrightness = 0f` is the panel's own dimmest setting rather than off. The Pixel 7
     * only offers 60 Hz and 90 Hz as app-selectable modes, so the low render rates it really
     * supports (down to 20 Hz) are reached by asking for the low frame-rate *category* on
     * Android 15 and letting the compositor pick — which is also the only sanctioned way to do it.
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
            if (!fb.pending) lastOutcome.value = fb.title to System.currentTimeMillis()
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
        private const val PRESS_FLASH_MS = 900L

        /** How far a finger must travel before the face counts it as a deliberate wake. */
        private const val WAKE_SLIDE_PX = 90f

        fun intent(context: Context): Intent =
            Intent(context, RemoteModeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}

private object RemoteModeFormats {
    val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM")
}

@Composable
private fun ClockBlock(now: LocalDateTime, aod: AodDensity, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(
                now.format(RemoteModeFormats.TIME),
                fontSize = aod.clockSp.sp,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                now.format(RemoteModeFormats.DATE),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
        IconButton(onClick = onClose) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Leave Remote Mode",
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun ControllerLine(
    name: String?,
    battery: Int?,
    connected: Boolean,
    accent: Color,
    idle: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (connected) accent else idle)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            name ?: "Waiting for a controller…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        battery?.let { percent ->
            Spacer(Modifier.width(10.dp))
            Icon(
                Icons.Default.BatteryFull,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(3.dp))
            Text(
                "$percent%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun SlideHint() {
    Text(
        "Slide to wake",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

/** Everything hidden while dozing: the pad, the mapping list and the last outcome. */
@Composable
private fun ColumnScope.Detail(
    rows: List<ControllerMappingEntity>,
    items: List<TagEntity>,
    pressed: String?,
    accent: Color,
    idle: Color,
    aod: AodDensity,
    padWidth: Dp,
    outcome: Pair<String, Long>?,
    now: LocalDateTime
) {
    if (aod.showDiagram) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            GamepadDiagram(
                mappedKeys = rows.filter { it.enabled }.map { it.inputKey }.toSet(),
                pressedKey = pressed,
                accent = accent,
                idle = idle,
                // Never let the pad dominate a wide screen.
                modifier = Modifier.widthIn(max = if (padWidth < 420.dp) padWidth else 420.dp)
            )
        }
        Spacer(Modifier.height(aod.gap))
    }

    if (rows.isEmpty()) {
        Text(
            "No buttons mapped yet. Open TapRelay → Controllers & Remotes.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(aod.rowGap)) {
            rows.forEach { mapping ->
                MappingRow(
                    mapping = mapping,
                    item = items.firstOrNull { it.tagId == mapping.tagId },
                    highlighted = pressed == mapping.inputKey,
                    accent = accent,
                    aod = aod
                )
            }
        }
    }

    outcome?.let { (text, at) ->
        Spacer(Modifier.height(aod.gap))
        Text(
            text + " • " + agoLabel(at, now),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }

    Spacer(Modifier.height(10.dp))
    Text(
        "Screen stays on, goes dark on its own and slows the display down. Android delivers " +
            "controller buttons only to the app in front, so leave this open.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun MappingRow(
    mapping: ControllerMappingEntity,
    item: TagEntity?,
    highlighted: Boolean,
    accent: Color,
    aod: AodDensity
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (highlighted) accent.copy(alpha = 0.18f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (mapping.enabled) 0.55f else 0.2f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = aod.rowPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(7.dp),
                color = if (highlighted) accent else accent.copy(alpha = 0.22f)
            ) {
                Text(
                    shortLabel(mapping.inputLabel),
                    Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (highlighted) Color.Black else accent
                )
            }
            Spacer(Modifier.width(12.dp))
            Icon(
                iconFor(item?.iconKey ?: "lamp"),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(17.dp)
            )
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item?.friendlyName ?: "Mapped action",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    (mapping.activationMode ?: item?.activation ?: ActivationMode.EXECUTE).label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            // The state TapRelay last drove this item to.
            item?.let {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(
                            if (it.lastKnownState == 1) accent
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        )
                )
            }
        }
    }
}

/** "A Button" reads as "A" on a face this dense; chords keep both halves. */
private fun shortLabel(label: String): String =
    label.replace(" Button", "").replace("D-Pad ", "D-pad ")

private fun agoLabel(at: Long, tick: LocalDateTime): String {
    // tick is unused beyond forcing a recomposition each second.
    val seconds = ((System.currentTimeMillis() - at) / 1000L).coerceAtLeast(0L)
    return when {
        seconds < 5 -> "just now"
        seconds < 60 -> "${seconds}s ago"
        seconds < 3600 -> "${seconds / 60}m ago"
        else -> "${seconds / 3600}h ago"
    }
}
