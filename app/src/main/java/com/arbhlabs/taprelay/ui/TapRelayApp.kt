package com.arbhlabs.taprelay.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Weekend
import androidx.compose.material3.*
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arbhlabs.taprelay.data.local.entity.TagEntity
import com.arbhlabs.taprelay.domain.model.ActionType
import com.arbhlabs.taprelay.domain.model.ActivationMode
import com.arbhlabs.taprelay.domain.model.Brightness
import com.arbhlabs.taprelay.domain.model.ColorMath
import com.arbhlabs.taprelay.domain.model.DeviceLabels
import com.arbhlabs.taprelay.domain.model.DiscoveredDevice
import com.arbhlabs.taprelay.domain.model.DiscoveredScene
import com.arbhlabs.taprelay.domain.model.ItemLabels
import com.arbhlabs.taprelay.domain.model.LightPresets
import com.arbhlabs.taprelay.domain.model.powerIntent
import com.arbhlabs.taprelay.domain.provider.GOVEE_PROVIDER_ID
import com.arbhlabs.taprelay.domain.provider.HOME_ASSISTANT_PROVIDER_ID
import com.arbhlabs.taprelay.domain.provider.SENSIBO_PROVIDER_ID
import com.arbhlabs.taprelay.domain.provider.TUYA_PROVIDER_ID
import com.arbhlabs.taprelay.ui.components.TapRelayPill
import com.arbhlabs.taprelay.ui.actions.ActionsScreen
import com.arbhlabs.taprelay.ui.lastdose.LastDoseScreen
import androidx.compose.material.icons.filled.Computer
import com.arbhlabs.taprelay.ui.remote.AodSettingsScreen
import com.arbhlabs.taprelay.ui.places.PlacesScreen
import com.arbhlabs.taprelay.ui.quick.QuickControlsSheet
import kotlin.math.roundToInt

val ICONS: Map<String, ImageVector> = mapOf(
    "lamp" to Icons.Default.Lightbulb,
    "room" to Icons.Default.Weekend,
    "plug" to Icons.Default.Power,
    "switch" to Icons.Default.Bolt,
    "scene" to Icons.Default.AutoAwesome,
    "air" to Icons.Default.Air,
    "lastdose" to Icons.Default.Bolt
)

fun iconFor(key: String) = ICONS[key] ?: Icons.Default.Lightbulb

@Composable
fun TapRelayApp(vm: TapRelayViewModel) {
    val ui by vm.ui.collectAsState()
    val pill by vm.pill.collectAsState()
    val wizard by vm.wizard.collectAsState()
    val setupPrompt by vm.setupPrompt.collectAsState()
    val nfcReady by vm.nfcReady.collectAsState()
    val openItemId by vm.openItemTagId.collectAsState()

    var startedOnboarding by rememberSaveable { mutableStateOf(false) }
    var showConnections by rememberSaveable { mutableStateOf(false) }
    var showProDialog by rememberSaveable { mutableStateOf(false) }
    var showDiagnostics by rememberSaveable { mutableStateOf(false) }
    var showNfcStore by rememberSaveable { mutableStateOf(false) }
    var showControllers by rememberSaveable { mutableStateOf(false) }
    var showPlaces by rememberSaveable { mutableStateOf(false) }
    var showLastDose by rememberSaveable { mutableStateOf(false) }
    var showPc by rememberSaveable { mutableStateOf(false) }
    var showActions by rememberSaveable { mutableStateOf(false) }
    var showAodSettings by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        when {
            !ui.onboardingComplete && !startedOnboarding ->
                OnboardingScreen(onGetStarted = { startedOnboarding = true })

            !ui.onboardingComplete -> {
                if (startedOnboarding) {
                    BackHandler { startedOnboarding = false }
                }
                ConnectionsScreen(
                    vm = vm,
                    isOnboarding = true,
                    onDone = { vm.completeOnboarding() }
                )
            }

            showConnections -> {
                BackHandler { showConnections = false }
                ConnectionsScreen(
                    vm = vm,
                    isOnboarding = false,
                    onDone = { showConnections = false }
                )
            }

            showControllers -> {
                BackHandler { showControllers = false }
                ControllersScreen(
                    vm = vm,
                    onDone = { showControllers = false }
                )
            }

            showLastDose -> {
                BackHandler { showLastDose = false }
                LastDoseScreen(
                    vm = vm,
                    onDone = { showLastDose = false }
                )
            }

            showPc -> {
                BackHandler { showPc = false }
                com.arbhlabs.taprelay.ui.pc.PcRelayScreen(vm = vm, onDone = { showPc = false })
            }

            showActions -> {
                // The screen owns its own Back, including the editors nested inside it.
                ActionsScreen(vm = vm, onDone = { showActions = false })
            }

            showAodSettings -> {
                AodSettingsScreen(vm = vm, onDone = { showAodSettings = false })
            }

            showPlaces -> {
                BackHandler { showPlaces = false }
                PlacesScreen(
                    vm = vm,
                    onDone = { showPlaces = false }
                )
            }

            else ->
                HomeScreen(
                    vm = vm,
                    // LastDose logs are items too, but they belong on their own screen rather
                    // than in a list of lamps whose card offers brightness and colour.
                    tags = ui.tags.filterNot { it.isLastDose },
                    goveeConnected = ui.goveeConnected,
                    tuyaConnected = ui.tuyaConnected,
                    sensiboConnected = ui.sensiboConnected,
                    haConnected = ui.haConnected,
                    isPro = ui.isPro,
                    nfcReady = nfcReady,
                    onOpenConnections = { showConnections = true },
                    onOpenControllers = { showControllers = true },
                    onOpenPlaces = { showPlaces = true },
                    onOpenLastDose = { showLastDose = true },
                    onOpenPc = { showPc = true },
                    onOpenActions = { showActions = true },
                    onOpenAodSettings = { showAodSettings = true },
                    onOpenPro = { showProDialog = true },
                    onOpenDiagnostics = { showDiagnostics = true },
                    onOpenNfcStore = { showNfcStore = true }
                )
        }

        if (wizard.active) {
            BackHandler { vm.stepBack() }
            Surface(Modifier.fillMaxSize()) {
                AddTagFlow(
                    vm = vm,
                    nfcReady = nfcReady,
                    isPro = ui.isPro,
                    onOpenPro = { showProDialog = true },
                    onOpenNfcStore = { showNfcStore = true }
                )
            }
        }

        TapRelayPill(feedback = pill, onDismiss = { vm.clearPill() }, modifier = Modifier.align(Alignment.TopCenter))

        // A trigger set to Quick Controls opens this in place instead of firing.
        QuickControlsSheet(session = vm.quickControls, onDismiss = { vm.closeQuickControls() })

        // A trigger set to Open item lands straight on that item.
        openItemId?.let { id ->
            BackHandler { vm.closeItem() }
            val target = ui.tags.firstOrNull { it.tagId == id }
            if (target == null) {
                LaunchedEffect(id) { vm.closeItem() }
            } else {
                TagDetailSheet(
                    tag = target,
                    onDismiss = { vm.closeItem() },
                    onRename = { vm.renameTag(target, it) },
                    onToggleEnabled = { vm.setTagEnabled(target, it) },
                    onChangeMapping = { vm.editTag(target); vm.closeItem() },
                    onTest = { vm.testTag(target) },
                    onQuickControls = { vm.closeItem(); vm.openQuickControls(target.tagId) },
                    onDelete = { vm.deleteTag(target); vm.closeItem() }
                )
            }
        }

        setupPrompt?.let { id ->
            BackHandler { vm.dismissSetupPrompt() }
            AlertDialog(
                onDismissRequest = { vm.dismissSetupPrompt() },
                title = { Text("New tag") },
                text = { Text("This TapRelay tag isn't set up on this phone yet. Set it up now?") },
                confirmButton = { TextButton(onClick = { vm.configureUnregisteredTag(id) }) { Text("Set up") } },
                dismissButton = { TextButton(onClick = { vm.dismissSetupPrompt() }) { Text("Not now") } }
            )
        }

        if (showProDialog) {
            BackHandler { showProDialog = false }
            ProUpgradeDialog(
                isPro = ui.isPro,
                isTrialActive = ui.isTrialActive,
                trialDaysRemaining = ui.trialDaysRemaining,
                onDismiss = { showProDialog = false },
                onStartFreeTrial = { cb -> vm.startFreeTrial(cb) },
                onActivateLicense = { key, cb -> vm.activateLicense(key, cb) },
                onDeactivate = { vm.deactivateLicense() }
            )
        }

        if (showDiagnostics) {
            BackHandler { showDiagnostics = false }
            DiagnosticsSheet(
                vm = vm,
                onDismiss = { showDiagnostics = false }
            )
        }

        if (showNfcStore) {
            BackHandler { showNfcStore = false }
            NfcStoreDialog(
                isPro = ui.isPro,
                onDismiss = { showNfcStore = false }
            )
        }
    }
}

@Composable
private fun OnboardingScreen(onGetStarted: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Nfc, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(32.dp))
            Text(
                "Turn NFC stickers into physical buttons for your smart home.",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Tap a sticker, pick a light and an action, and save. Tap it again anytime to run it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(40.dp))
            Button(onClick = onGetStarted, Modifier.fillMaxWidth().height(52.dp)) { Text("Get started") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectionsScreen(
    vm: TapRelayViewModel,
    isOnboarding: Boolean,
    onDone: () -> Unit
) {
    val ui by vm.ui.collectAsState()
    val state by vm.connectState.collectAsState()

    var goveeKey by rememberSaveable { mutableStateOf("") }
    var tuyaAccessId by rememberSaveable { mutableStateOf("") }
    var tuyaSecret by rememberSaveable { mutableStateOf("") }
    var tuyaRegion by rememberSaveable { mutableStateOf("us") }
    var tuyaUid by rememberSaveable { mutableStateOf("") }
    var sensiboKey by rememberSaveable { mutableStateOf("") }
    var haUrl by rememberSaveable { mutableStateOf("") }
    var haToken by rememberSaveable { mutableStateOf("") }

    var showGoogleInfo by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isOnboarding) "Connect Smart Home" else "Connections") },
                navigationIcon = {
                    if (!isOnboarding) {
                        IconButton(onClick = onDone) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refreshConnections() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("Providers", style = MaterialTheme.typography.titleMedium)

            // --- GOVEE CARD ---
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Govee", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                if (ui.goveeConnected) "Connected • ${ui.goveeDeviceCount} devices" else "Not connected",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (ui.goveeConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }
                        if (ui.goveeConnected) {
                            FilledTonalButton(onClick = { vm.disconnectGovee() }) { Text("Disconnect") }
                        }
                    }

                    if (!ui.goveeConnected) {
                        Text(
                            "Get your personal API key from Govee Home: Profile → Settings → Apply for API Key.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = goveeKey,
                            onValueChange = { goveeKey = it; vm.resetConnectState() },
                            label = { Text("Govee API Key") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = { vm.connectGovee(goveeKey) },
                            enabled = state !is TapRelayViewModel.ConnectState.Validating,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (state is TapRelayViewModel.ConnectState.Validating) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Connect Govee")
                            }
                        }
                    }
                }
            }

            // --- SMART LIFE / TUYA CARD ---
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Smart Life / Tuya", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            val statusText = if (ui.tuyaConnected) {
                                "Connected • ${ui.tuyaDeviceCount} devices • ${ui.tuyaSceneCount} scenes"
                            } else "Not connected"
                            Text(
                                statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (ui.tuyaConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }
                        if (ui.tuyaConnected) {
                            FilledTonalButton(onClick = { vm.disconnectTuya() }) { Text("Disconnect") }
                        }
                    }

                    if (!ui.tuyaConnected) {
                        Text(
                            "Link your Smart Life or Tuya app to a Cloud project at iot.tuya.com and enter your credentials.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = tuyaAccessId,
                            onValueChange = { tuyaAccessId = it; vm.resetConnectState() },
                            label = { Text("Access ID / Client ID") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = tuyaSecret,
                            onValueChange = { tuyaSecret = it; vm.resetConnectState() },
                            label = { Text("Access Secret") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = tuyaUid,
                            onValueChange = { tuyaUid = it; vm.resetConnectState() },
                            label = { Text("User ID / UID (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Region:", style = MaterialTheme.typography.bodySmall)
                            listOf("us", "eu", "cn").forEach { r ->
                                FilterChip(
                                    selected = tuyaRegion == r,
                                    onClick = { tuyaRegion = r },
                                    label = { Text(r.uppercase()) }
                                )
                            }
                        }

                        Button(
                            onClick = {
                                vm.connectTuya(
                                    accessId = tuyaAccessId,
                                    secret = tuyaSecret,
                                    region = tuyaRegion,
                                    uid = tuyaUid
                                )
                            },
                            enabled = state !is TapRelayViewModel.ConnectState.Validating,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (state is TapRelayViewModel.ConnectState.Validating) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Connect Smart Life")
                            }
                        }
                    }
                }
            }

            // --- SENSIBO CARD ---
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Sensibo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            val statusText = if (ui.sensiboConnected) {
                                "Connected • ${ui.sensiboDeviceCount} pods"
                            } else "Not connected"
                            Text(
                                statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (ui.sensiboConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }
                        if (ui.sensiboConnected) {
                            FilledTonalButton(onClick = { vm.disconnectSensibo() }) { Text("Disconnect") }
                        }
                    }

                    if (!ui.sensiboConnected) {
                        Text(
                            "Control your Sensibo Air Purifiers, Fans, and AC units. Get your personal API key at home.sensibo.com/me/api.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = sensiboKey,
                            onValueChange = { sensiboKey = it; vm.resetConnectState() },
                            label = { Text("Sensibo API Key") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = { vm.connectSensibo(sensiboKey) },
                            enabled = state !is TapRelayViewModel.ConnectState.Validating,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (state is TapRelayViewModel.ConnectState.Validating) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Connect Sensibo")
                            }
                        }
                    }
                }
            }

            // --- HOME ASSISTANT CARD ---
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Home Assistant", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            val statusText = if (ui.haConnected) {
                                "Connected • ${ui.haEntityCount} things"
                            } else "Not connected"
                            Text(
                                statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (ui.haConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }
                        if (ui.haConnected) {
                            FilledTonalButton(onClick = { vm.disconnectHomeAssistant() }) { Text("Disconnect") }
                        }
                    }

                    if (!ui.haConnected) {
                        Text(
                            "Bring in everything your own Home Assistant already controls — Zigbee, " +
                                "Z-Wave, Matter, anything. TapRelay talks to it directly from this " +
                                "phone; nothing goes through us.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = haUrl,
                            onValueChange = { haUrl = it; vm.resetConnectState() },
                            label = { Text("Address") },
                            placeholder = { Text("homeassistant.local:8123") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = haToken,
                            onValueChange = { haToken = it; vm.resetConnectState() },
                            label = { Text("Access token") },
                            singleLine = true,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "In Home Assistant open your profile, scroll to Long-lived access " +
                                "tokens and create one for TapRelay. It is encrypted on this phone " +
                                "and never leaves it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { vm.connectHomeAssistant(haUrl, haToken) },
                            enabled = state !is TapRelayViewModel.ConnectState.Validating,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (state is TapRelayViewModel.ConnectState.Validating) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Connect Home Assistant")
                            }
                        }
                    }
                }
            }

            // --- GOOGLE HOME CARD ---
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Google Home", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Unavailable in this build", color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { showGoogleInfo = true }) { Text("Learn why") }
                }
            }

            (state as? TapRelayViewModel.ConnectState.Error)?.let {
                Text(it.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(if (isOnboarding) "Continue to TapRelay" else "Done")
            }
        }
    }

    if (showGoogleInfo) {
        AlertDialog(
            onDismissRequest = { showGoogleInfo = false },
            confirmButton = { TextButton(onClick = { showGoogleInfo = false }) { Text("OK") } },
            title = { Text("Google Home API") },
            text = {
                Text(
                    "Google Home programmatic automation triggering requires enterprise partner access from Google. " +
                        "TapRelay maintains a standards-compliant provider abstraction ready for when Google opens direct client access."
                )
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    vm: TapRelayViewModel,
    tags: List<TagEntity>,
    goveeConnected: Boolean,
    tuyaConnected: Boolean,
    sensiboConnected: Boolean,
    haConnected: Boolean,
    isPro: Boolean,
    nfcReady: Boolean,
    onOpenConnections: () -> Unit,
    onOpenControllers: () -> Unit,
    onOpenPlaces: () -> Unit,
    onOpenLastDose: () -> Unit,
    onOpenPc: () -> Unit,
    onOpenActions: () -> Unit,
    onOpenAodSettings: () -> Unit,
    onOpenPro: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenNfcStore: () -> Unit
) {
    var detail by remember { mutableStateOf<TagEntity?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    val anyConnected = goveeConnected || tuyaConnected || sensiboConnected || haConnected
    val controllers by vm.connectedControllers.collectAsState()
    val controllerMappings by vm.controllerMappings.collectAsState()
    val running by vm.runningItems.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("TapRelay")
                    }
                },
                actions = {
                    AssistChip(
                        onClick = onOpenConnections,
                        label = {
                            val parts = buildList {
                                if (goveeConnected) add("Govee")
                                if (tuyaConnected) add("Smart Life")
                                if (sensiboConnected) add("Sensibo")
                                if (haConnected) add("Home Assistant")
                            }
                            Text(
                                // Naming them only reads well while they fit. Past two it becomes
                                // a wrapping sentence next to the title, so it becomes a count.
                                when {
                                    parts.isEmpty() -> "Not connected"
                                    parts.size <= 2 -> parts.joinToString(" + ")
                                    else -> "${parts.size} connected"
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = {
                            Icon(
                                if (anyConnected) Icons.Default.CheckCircle else Icons.Default.Hub,
                                null,
                                tint = if (anyConnected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline
                            )
                        }
                    )
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Magic Actions") },
                            leadingIcon = { Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary) },
                            onClick = { menuOpen = false; onOpenActions() }
                        )
                        DropdownMenuItem(
                            text = { Text("Controllers & Remotes") },
                            leadingIcon = { Icon(Icons.Default.SportsEsports, null, tint = MaterialTheme.colorScheme.primary) },
                            onClick = { menuOpen = false; onOpenControllers() }
                        )
                        DropdownMenuItem(
                            text = { Text("LastDose Logs") },
                            leadingIcon = { Icon(Icons.Default.Bolt, null) },
                            onClick = { menuOpen = false; onOpenLastDose() }
                        )
                        DropdownMenuItem(
                            text = { Text("Windows PC") },
                            leadingIcon = { Icon(Icons.Default.Computer, null, tint = MaterialTheme.colorScheme.primary) },
                            onClick = { menuOpen = false; onOpenPc() }
                        )
                        DropdownMenuItem(
                            text = { Text("Always-on face") },
                            leadingIcon = { Icon(Icons.Default.Smartphone, null) },
                            onClick = { menuOpen = false; onOpenAodSettings() }
                        )
                        DropdownMenuItem(
                            text = { Text("Places & Routines") },
                            leadingIcon = { Icon(Icons.Default.Place, null, tint = MaterialTheme.colorScheme.primary) },
                            onClick = { menuOpen = false; onOpenPlaces() }
                        )
                        DropdownMenuItem(
                            text = { Text("TapRelay Pro") },
                            leadingIcon = { Icon(Icons.Default.Star, null, tint = MaterialTheme.colorScheme.primary) },
                            onClick = { menuOpen = false; onOpenPro() }
                        )
                        DropdownMenuItem(
                            text = { Text("Diagnostics & Replay") },
                            leadingIcon = { Icon(Icons.Default.History, null) },
                            onClick = { menuOpen = false; onOpenDiagnostics() }
                        )
                        DropdownMenuItem(
                            text = { Text("Shop NFC Hardware") },
                            leadingIcon = { Icon(Icons.Default.ShoppingCart, null) },
                            onClick = { menuOpen = false; onOpenNfcStore() }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Connections") },
                            leadingIcon = { Icon(Icons.Default.Hub, null) },
                            onClick = { menuOpen = false; onOpenConnections() }
                        )
                        DropdownMenuItem(
                            text = { Text("About TapRelay") },
                            leadingIcon = { Icon(Icons.Default.Info, null) },
                            onClick = { menuOpen = false; showAbout = true }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { vm.startWizard() },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Add Tag") }
            )
        }
    ) { pad ->
      Column(Modifier.padding(pad).fillMaxSize()) {
        if (!nfcReady) NfcOffBanner()
        if (tags.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("No tags yet", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Add an NFC sticker and turn it into a physical smart-home button.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                if (controllers.isNotEmpty() || controllerMappings.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenControllers)
                            .padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.SportsEsports,
                                contentDescription = null,
                                tint = if (controllers.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = if (controllers.isNotEmpty()) "${controllers.first().name} connected" else "Game Controller Remotes",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (controllerMappings.isNotEmpty()) "${controllerMappings.size} button mappings active" else "Tap to map buttons",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (tags.size == 1) "1 active tag" else "${tags.size} active tags",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                tags.forEach { tag ->
                    val busy = running.contains(tag.tagId)
                    ElevatedCard(
                        onClick = { detail = tag },
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                    ) {
                        Row(
                            Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(40.dp).clip(CircleShape)
                                    .background(
                                        if (busy) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    iconFor(tag.iconKey), null,
                                    tint = if (busy) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    tag.friendlyName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    // The row the finger landed on says so itself. A cloud toggle
                                    // has to read the device before it can invert it, and without
                                    // this the card sits looking untouched for the whole round trip.
                                    if (busy) "Working…" else
                                        ItemLabels.detailed(tag, tagActionSummary(tag)) +
                                            if (!tag.enabled) " • off" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (busy) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (busy) {
                                CircularProgressIndicator(
                                    Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(12.dp))
                            } else {
                                TextButton(onClick = { vm.testTag(tag) }) { Text("Test") }
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
                Spacer(Modifier.height(80.dp))
            }
        }
      }
    }

    if (showAbout) AboutDialog(onDismiss = { showAbout = false })

    detail?.let { tag ->
        TagDetailSheet(
            tag = tags.firstOrNull { it.tagId == tag.tagId } ?: tag,
            onDismiss = { detail = null },
            onRename = { vm.renameTag(tag, it) },
            onToggleEnabled = { vm.setTagEnabled(tag, it) },
            onChangeMapping = { vm.editTag(tag); detail = null },
            onTest = { vm.testTag(tag) },
            onQuickControls = { detail = null; vm.openQuickControls(tag.tagId) },
            onDelete = { vm.deleteTag(tag); detail = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagDetailSheet(
    tag: TagEntity,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onChangeMapping: () -> Unit,
    onTest: () -> Unit,
    onQuickControls: () -> Unit,
    onDelete: () -> Unit
) {
    var name by remember(tag.tagId) { mutableStateOf(tag.friendlyName) }
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Edit tag", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = { onRename(name) }, Modifier.fillMaxWidth()) { Text("Save name") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Enabled", Modifier.weight(1f))
                Switch(checked = tag.enabled, onCheckedChange = onToggleEnabled)
            }
            Text(
                "Currently ${tagActionSummary(tag).lowercase()} on ${tag.allTargets.joinToString { it.name.ifBlank { if (tag.providerId == SENSIBO_PROVIDER_ID) "a device" else "a light" } }}. " +
                    "Changing the device, scene, or action does not require re-tapping the sticker.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "When triggered: ${tag.activation.label.lowercase()}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FilledTonalButton(onClick = onQuickControls, Modifier.fillMaxWidth()) { Text("Open Quick Controls") }
            OutlinedButton(onClick = onChangeMapping, Modifier.fillMaxWidth()) { Text("Change device, scene, or action") }
            OutlinedButton(onClick = onTest, Modifier.fillMaxWidth()) { Text("Test action") }
            if (!confirmDelete) {
                TextButton(onClick = { confirmDelete = true }, Modifier.fillMaxWidth()) {
                    Text("Delete tag", color = MaterialTheme.colorScheme.error)
                }
            } else {
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Delete for good") }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTagFlow(
    vm: TapRelayViewModel,
    nfcReady: Boolean,
    isPro: Boolean,
    onOpenPro: () -> Unit,
    onOpenNfcStore: () -> Unit
) {
    val w by vm.wizard.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Devices, 1 = Scenes

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (w.editingExisting) "Change mapping" else "Add tag") },
            navigationIcon = {
                IconButton(onClick = { vm.stepBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            }
        )
    }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (w.step) {
                WizardStep.SCAN -> ScanStep(w, vm, nfcReady, onOpenNfcStore)

                WizardStep.PICK_DEVICE -> {
                    Text("What should this tag control?", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Pick one light or device, or several to control them together.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (w.discovering) {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (w.error != null) {
                        Text(w.error!!, color = MaterialTheme.colorScheme.error)
                        Button(onClick = { vm.retryDiscovery() }) { Text("Try again") }
                    } else if (w.devices.isEmpty() && w.scenes.isEmpty()) {
                        Text("No controllable devices or scenes found. Check your connections in Settings.")
                        Button(onClick = { vm.retryDiscovery() }) { Text("Refresh") }
                    } else {
                        if (w.scenes.isNotEmpty()) {
                            TabRow(selectedTabIndex = selectedTab) {
                                Tab(
                                    selected = selectedTab == 0,
                                    onClick = { selectedTab = 0 },
                                    text = { Text("DEVICES (${w.devices.size})") }
                                )
                                Tab(
                                    selected = selectedTab == 1,
                                    onClick = { selectedTab = 1 },
                                    text = { Text("SCENES (${w.scenes.size})") }
                                )
                            }
                        }

                        if (selectedTab == 0 || w.scenes.isEmpty()) {
                            w.devices.forEach { d ->
                                val picked = w.selectedDevices.any {
                                    it.deviceId == d.deviceId && it.providerId == d.providerId
                                }
                                val dIcon = when (d.providerId) {
                                    SENSIBO_PROVIDER_ID -> Icons.Default.Air
                                    else -> Icons.Default.Lightbulb
                                }
                                ElevatedCard(
                                    onClick = { vm.toggleDeviceSelection(d) },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    Row(
                                        Modifier.padding(16.dp).fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(dIcon, null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(d.name, style = MaterialTheme.typography.titleMedium)
                                            val pLabel = when {
                                                d.providerId == TUYA_PROVIDER_ID -> "Smart Life"
                                                d.providerId == SENSIBO_PROVIDER_ID -> "Sensibo"
                                                d.sku == GOVEE_GROUP_SKU -> "Govee group"
                                                else -> "Govee"
                                            }
                                            val statusLabel = if (d.isOnline) "Online" else "Offline"
                                            val abilityLabel =
                                                if (!d.supportsColor && !d.supportsBrightness) " • on/off only" else ""
                                            Text(
                                                "$pLabel • $statusLabel$abilityLabel",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (d.isOnline) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline
                                            )
                                        }
                                        Checkbox(checked = picked, onCheckedChange = { vm.toggleDeviceSelection(d) })
                                    }
                                }
                            }

                            if (w.selectedDevices.size > 1) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                ) {
                                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "One tag, ${w.selectedDevices.size} devices",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = { vm.continueFromDevices() },
                                enabled = w.selectedDevices.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 8.dp)
                            ) {
                                Text(
                                    when (w.selectedDevices.size) {
                                        0 -> "Pick a device"
                                        1 -> "Continue"
                                        else -> "Continue with ${w.selectedDevices.size} devices"
                                    }
                                )
                            }
                        } else {
                            w.scenes.forEach { s ->
                                ElevatedCard(
                                    onClick = { vm.selectScene(s) },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    Row(
                                        Modifier.padding(16.dp).fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(s.name, style = MaterialTheme.typography.titleMedium)
                                            Text("Smart Life Scene", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                WizardStep.PICK_ACTION -> {
                    val picked = w.selectedDevices.ifEmpty { listOfNotNull(w.device) }
                    val isClimateOrFan = picked.isNotEmpty() && picked.all { it.providerId == SENSIBO_PROVIDER_ID }
                    val colorCount = picked.count { it.supportsColor }
                    val brightCount = picked.count { it.supportsBrightness }

                    if (isClimateOrFan) {
                        Text("What should this tag do?", style = MaterialTheme.typography.titleMedium)
                        // Only the speeds this unit reports; a single-speed unit gets no fan options.
                        val fanLevels = w.fanLevels.filterNot { it.equals("auto", true) }
                        val slowest = fanLevels.firstOrNull()?.let { DeviceLabels.fanLevel(it) }
                        val fastest = fanLevels.lastOrNull()?.let { DeviceLabels.fanLevel(it) }
                        val climateActions = buildList {
                            add(Triple(ActionType.TOGGLE, "Toggle Power", "Turns power on or off with each tap"))
                            if (fanLevels.size >= 2) {
                                add(
                                    Triple(
                                        ActionType.TOGGLE_FAN_SPEED,
                                        "Toggle Fan Speed ($slowest ↔ $fastest)",
                                        "Flips between $slowest and $fastest on each tap"
                                    )
                                )
                            }
                            add(Triple(ActionType.TURN_ON, "Turn On", "Always turns the unit on"))
                            add(Triple(ActionType.TURN_OFF, "Turn Off", "Always turns the unit off"))
                        }
                        if (w.readingCapabilities) {
                            Text(
                                "Reading what this unit can do…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (w.fanLevels.isEmpty()) {
                            Text(
                                "Couldn't read this unit's fan speeds, so only power actions are offered.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        climateActions.forEach { (a, title, desc) ->
                            val chosen = w.action == a
                            ElevatedCard(
                                onClick = { vm.selectAction(a) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    Modifier.padding(16.dp).fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    RadioButton(selected = chosen, onClick = { vm.selectAction(a) })
                                }
                            }
                        }

                        if (w.fanLevels.isNotEmpty() &&
                            (w.action == ActionType.TOGGLE || w.action == ActionType.TURN_ON)
                        ) {
                            Text(
                                if (w.action == ActionType.TOGGLE) "And when it turns on…" else "Fan speed setting",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            ExtraToggleRow(
                                label = "Set fan speed on activation",
                                hint = if (w.wantsFanSpeed) "Sets fan to ${DeviceLabels.fanLevel(w.fanLevel)} when active" else "Keep current fan speed",
                                checked = w.wantsFanSpeed,
                                onCheckedChange = { vm.setWantsFanSpeed(it) }
                            )
                            if (w.wantsFanSpeed) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    w.fanLevels.forEach { lvl ->
                                        val selected = w.fanLevel.equals(lvl, ignoreCase = true)
                                        FilterChip(
                                            selected = selected,
                                            onClick = { vm.setFanLevel(lvl) },
                                            label = {
                                                Text(
                                                    DeviceLabels.fanLevel(lvl),
                                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Text("What should the power do?", style = MaterialTheme.typography.titleMedium)
                        listOf(ActionType.TOGGLE, ActionType.TURN_ON, ActionType.TURN_OFF).forEach { a ->
                            val chosen = w.action.powerIntent() == a
                            ElevatedCard(
                                onClick = { vm.selectAction(a) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    Modifier.padding(16.dp).fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        actionLabel(a),
                                        Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    RadioButton(selected = chosen, onClick = { vm.selectAction(a) })
                                }
                            }
                        }

                        // Colour and brightness are extras on top of the power action, not
                        // alternatives to it, so a tag can toggle a light *and* give it a look.
                        val turnsOff = w.action.powerIntent() == ActionType.TURN_OFF
                        if (!turnsOff && (colorCount > 0 || brightCount > 0)) {
                            Text(
                                "And when it comes on…",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            if (colorCount > 0) {
                                ExtraToggleRow(
                                    label = "Also set a colour",
                                    hint = if (colorCount < picked.size)
                                        "$colorCount of ${picked.size} of your lights can do this" else null,
                                    checked = w.wantsColor,
                                    onCheckedChange = { vm.setWantsColor(it) }
                                )
                            }
                            if (brightCount > 0) {
                                ExtraToggleRow(
                                    label = "Also set a brightness",
                                    hint = if (brightCount < picked.size)
                                        "$brightCount of ${picked.size} of your lights can do this" else null,
                                    checked = w.wantsBrightness,
                                    onCheckedChange = { vm.setWantsBrightness(it) }
                                )
                            }
                        }
                    }

                    // Pro Feature: Context-Aware Time-of-Day Condition
                    ExtraToggleRow(
                        label = "Time-of-day condition",
                        hint = if (w.timeConditionEnabled)
                            "Active ${String.format("%02d:%02d", w.startHour, w.startMinute)} – ${String.format("%02d:%02d", w.endHour, w.endMinute)}"
                        else "Only trigger this action during specific hours",
                        checked = w.timeConditionEnabled,
                        onCheckedChange = { checked -> vm.setTimeConditionEnabled(checked) }
                    )

                    if (w.timeConditionEnabled) {
                        ElevatedCard(
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Active Window", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("From: ${String.format("%02d:%02d", w.startHour, w.startMinute)}", style = MaterialTheme.typography.bodyMedium)
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        FilledTonalButton(onClick = { vm.setTimeWindow((w.startHour - 1 + 24) % 24, w.startMinute, w.endHour, w.endMinute) }) { Text("-1h") }
                                        FilledTonalButton(onClick = { vm.setTimeWindow((w.startHour + 1) % 24, w.startMinute, w.endHour, w.endMinute) }) { Text("+1h") }
                                    }
                                }
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Until: ${String.format("%02d:%02d", w.endHour, w.endMinute)}", style = MaterialTheme.typography.bodyMedium)
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        FilledTonalButton(onClick = { vm.setTimeWindow(w.startHour, w.startMinute, (w.endHour - 1 + 24) % 24, w.endMinute) }) { Text("-1h") }
                                        FilledTonalButton(onClick = { vm.setTimeWindow(w.startHour, w.startMinute, (w.endHour + 1) % 24, w.endMinute) }) { Text("+1h") }
                                    }
                                }
                                Text("Outside window: turns off connected devices", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Button(
                        onClick = { vm.continueFromAction() },
                        modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 8.dp)
                    ) { Text("Continue") }
                }

                WizardStep.TUNE -> {
                    val wantsColor = w.wantsColor
                    val wantsBrightness = w.wantsBrightness

                    if (wantsColor) {
                        Text("Which colour?", style = MaterialTheme.typography.titleMedium)

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = !w.colorPickingWhite,
                                onClick = { vm.setColorPickingWhite(false) },
                                label = { Text("Colours") }
                            )
                            FilterChip(
                                selected = w.colorPickingWhite,
                                onClick = { vm.setColorPickingWhite(true) },
                                label = { Text("Whites") }
                            )
                        }

                        val swatches = if (w.colorPickingWhite) LightPresets.WHITES else LightPresets.COLORS
                        swatches.chunked(6).forEach { rowItems ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                            ) {
                                rowItems.forEach { preset ->
                                    val selected = w.colorRgb == preset.rgb
                                    Box(
                                        Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF000000L.toInt() or preset.rgb))
                                            .border(
                                                width = if (selected) 3.dp else 1.dp,
                                                color = if (selected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.outlineVariant,
                                                shape = CircleShape
                                            )
                                            .clickable {
                                                if (w.colorPickingWhite) {
                                                    ColorMath.nearestKelvin(preset.rgb)?.let { vm.setWhiteKelvin(it) }
                                                        ?: vm.setColor(preset.rgb)
                                                } else {
                                                    vm.setColor(preset.rgb)
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (selected) {
                                            Icon(
                                                Icons.Default.Check, null,
                                                tint = Color.Black.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (w.colorPickingWhite) {
                            Text(
                                "${w.whiteKelvin} K",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                            Slider(
                                value = w.whiteKelvin.toFloat(),
                                onValueChange = { vm.setWhiteKelvin(it.roundToInt()) },
                                valueRange = LightPresets.MIN_KELVIN.toFloat()..LightPresets.MAX_KELVIN.toFloat(),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Text(
                            LightPresets.nameFor(w.colorRgb),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    if (wantsBrightness) {
                        Text("How bright?", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${w.brightnessPercent}%",
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Slider(
                            value = w.brightnessPercent.toFloat(),
                            onValueChange = { vm.setBrightness(it.roundToInt()) },
                            valueRange = Brightness.MIN_PERCENT.toFloat()..Brightness.MAX_PERCENT.toFloat(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Text(
                        if (wantsColor && wantsBrightness) {
                            "Whenever these lights come on, they come on at this colour and brightness."
                        } else if (wantsColor) {
                            "Every tap sets these lights to exactly this colour."
                        } else {
                            "Every tap sets these lights to exactly this brightness."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = { vm.confirmTuning() },
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) { Text("Continue") }
                }

                WizardStep.NAME -> {
                    Text("Name & icon", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = w.name,
                        onValueChange = { vm.setName(it) },
                        label = { Text("Name") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ICONS.forEach { (key, icon) ->
                            FilterChip(
                                selected = w.iconKey == key,
                                onClick = { vm.setIcon(key) },
                                label = { Icon(icon, key) }
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("When this is triggered", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Applies to NFC taps, controller buttons, and anything else that fires this item.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ActivationModePicker(
                        selected = w.activationMode,
                        onSelect = { vm.setActivationMode(it) }
                    )
                    Button(
                        onClick = { vm.saveTag() },
                        enabled = !w.busy,
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) { Text("Save tag") }
                }

                WizardStep.DONE -> {
                    Icon(Icons.Default.CheckCircle, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("${w.name.ifBlank { "Your tag" }} is ready.", style = MaterialTheme.typography.titleMedium)
                    Text("Tap the sticker now to test it.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { vm.cancelWizard() }, Modifier.fillMaxWidth()) { Text("Done") }
                }
            }
        }
    }
}

/**
 * The activation-mode choice, shared by the tag wizard and the controller mapping dialog so a
 * trigger and an item are configured the same way.
 */
@Composable
fun ActivationModePicker(
    selected: ActivationMode?,
    onSelect: (ActivationMode) -> Unit,
    modifier: Modifier = Modifier,
    inheritLabel: String? = null,
    onInherit: (() -> Unit)? = null
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (inheritLabel != null && onInherit != null) {
            ActivationModeRow(
                title = inheritLabel,
                description = "Follow whatever this item is set to.",
                selected = selected == null,
                onClick = onInherit
            )
        }
        ActivationMode.entries.forEach { mode ->
            ActivationModeRow(
                title = mode.label,
                description = mode.description,
                selected = selected == mode,
                onClick = { onSelect(mode) }
            )
        }
    }
}

@Composable
private fun ActivationModeRow(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun NfcOffBanner() {
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.Nfc, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Text(
                "NFC is off. Turn it on in your phone settings to set up or use tags.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        title = { Text("About TapRelay") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "TapRelay by ARBH Labs — version ${com.arbhlabs.taprelay.BuildConfig.VERSION_NAME} (alpha).",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "This is an early internal test build. Things may change or break.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Smart-home control uses your own personal developer credentials under a Bring-Your-Own-Key model. " +
                        "TapRelay is not affiliated with, sponsored by, or endorsed by Govee, Tuya, Smart Life, Sensibo, or Google.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "All credentials are encrypted using hardware-backed Android Keystore keys (AES256-GCM) on this device only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@Composable
private fun ScanStep(
    w: WizardState,
    vm: TapRelayViewModel,
    nfcReady: Boolean,
    onOpenNfcStore: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (!nfcReady) NfcOffBanner()
        Text(
            "Hold an NFC sticker to the back of your phone",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            "On a Pixel, the sensor is near the top-centre of the back. Keep the sticker still until you feel a tap.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            when (w.scanPhase) {
                ScanPhase.READY -> NfcPulse()
                ScanPhase.DETECTED, ScanPhase.WRITING ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text(
                            if (w.scanPhase == ScanPhase.WRITING) "Setting up your sticker…" else "NFC sticker detected",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                ScanPhase.CONFIRM_OVERWRITE ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "This sticker already contains data. TapRelay can replace it.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = { vm.cancelWizard() }) { Text("Cancel") }
                            Button(onClick = { vm.confirmOverwrite() }) { Text("Use this tag") }
                        }
                        Text(
                            "Then hold the same sticker to the phone again.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                ScanPhase.ERROR ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            w.error ?: "Something went wrong.",
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Button(onClick = { vm.retryScan() }) { Text("Try again") }
                    }
            }
        }

        TextButton(
            onClick = onOpenNfcStore,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Icon(Icons.Default.ShoppingCart, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Need stickers? Order compatible NFC tags")
        }
    }
}

@Composable
private fun NfcPulse() {
    val transition = rememberInfiniteTransition(label = "nfc")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "ripple"
    )
    val primary = MaterialTheme.colorScheme.primary
    Box(Modifier.size(168.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val maxR = size.minDimension / 2f
            repeat(3) { i ->
                val f = (progress + i / 3f) % 1f
                drawCircle(
                    color = primary.copy(alpha = (1f - f) * 0.4f),
                    radius = maxR * f,
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }
        Box(
            Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Nfc, null, Modifier.size(36.dp), tint = primary)
        }
    }
}

/** What a saved tag does, including fan speed, brightness or colour it was given. */
private fun tagActionSummary(tag: TagEntity): String {
    if (tag.isLastDose) {
        val qualifier = listOf(tag.lastDoseAmount.orEmpty(), tag.lastDoseUnit.orEmpty())
            .filter { it.isNotBlank() }.joinToString(" ")
        val name = tag.lastDoseItemName.orEmpty().ifBlank { "log" }
        return if (qualifier.isBlank()) "LastDose • $name" else "LastDose • $name $qualifier"
    }
    if (tag.actionType == ActionType.TOGGLE_FAN_SPEED) {
        val count = tag.allTargets.size
        return if (count > 1) "Fan Low ↔ High • $count devices" else "Fan Low ↔ High"
    }
    val parts = buildList {
        add(actionLabel(tag.actionType.powerIntent()))
        tag.fanLevel?.let { add("Fan " + DeviceLabels.fanLevel(it)) }
        tag.colorRgb?.let { add(LightPresets.nameFor(it)) }
        tag.brightnessPercent?.let { add("${Brightness.clampPercent(it)}%") }
        if (tag.timeConditionEnabled && tag.startHour != null && tag.endHour != null) {
            add(String.format("%02d:%02d–%02d:%02d", tag.startHour, tag.startMinute ?: 0, tag.endHour, tag.endMinute ?: 0))
        }
    }
    val action = parts.joinToString(" • ")
    val count = tag.allTargets.size
    return if (count > 1) "$action • $count devices" else action
}

/** Govee reports a Home room grouping as a device with this sku. */
private const val GOVEE_GROUP_SKU = "SameModeGroup"

@Composable
private fun ExtraToggleRow(
    label: String,
    hint: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    // The whole row toggles, not just the switch: a 40dp switch is a fiddly target.
    ElevatedCard(
        onClick = { onCheckedChange(!checked) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                if (hint != null) {
                    Text(
                        hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

private fun actionLabel(a: ActionType) = when (a) {
    ActionType.TOGGLE -> "Toggle"
    ActionType.TURN_ON -> "Turn On"
    ActionType.TURN_OFF -> "Turn Off"
    ActionType.SET_BRIGHTNESS -> "Set Brightness"
    ActionType.SET_COLOR -> "Set Colour"
    ActionType.SET_SCENE -> "Set Colour + Brightness"
    ActionType.RUN_SCENE -> "Run Scene"
    ActionType.TOGGLE_FAN_SPEED -> "Fan Low ↔ High"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProUpgradeDialog(
    isPro: Boolean,
    isTrialActive: Boolean = false,
    trialDaysRemaining: Int = 0,
    onDismiss: () -> Unit,
    onStartFreeTrial: ((Result<String>) -> Unit) -> Unit,
    onActivateLicense: (String, (Result<String>) -> Unit) -> Unit,
    onDeactivate: () -> Unit = {}
) {
    var licenseKeyInput by rememberSaveable { mutableStateOf("") }
    var activating by remember { mutableStateOf(false) }
    var startingTrial by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Star, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("TapRelay Pro")
            }
        },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (isPro) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                if (isTrialActive) {
                                    Text("7-Day Free Trial Active", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    Text("$trialDaysRemaining days remaining. All Pro features unlocked.", style = MaterialTheme.typography.bodySmall)
                                } else {
                                    Text("Pro Unlocked", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    Text("All Pro capabilities active on this device.", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    TextButton(
                        onClick = onDeactivate,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Reset to Free Tier", color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    Text(
                        "Supercharge your physical smart-home buttons with advanced routines and telemetry.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Button(
                        onClick = {
                            startingTrial = true
                            statusMessage = null
                            onStartFreeTrial { res ->
                                startingTrial = false
                                res.onSuccess { msg ->
                                    isError = false
                                    statusMessage = msg
                                }.onFailure { err ->
                                    isError = true
                                    statusMessage = err.message ?: "Trial activation failed"
                                }
                            }
                        },
                        enabled = !startingTrial && !activating,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        if (startingTrial) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(Icons.Filled.Star, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Start 7-Day Free Trial", fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        "1-tap instant access • No credit card required",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )

                    ElevatedCard(shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("1. Multi-Target Routines", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("Trigger groups of lights, fans, and ACs simultaneously from a single NFC tag.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                            HorizontalDivider(Modifier.padding(vertical = 4.dp))

                            Text("2. Context-Aware Smart Tags", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("Set time-of-day execution windows (e.g. daytime vs night relaxation behaviors).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                            HorizontalDivider(Modifier.padding(vertical = 4.dp))

                            Text("3. Action History & Replay", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("Real-time telemetry, millisecond execution audit logs, and 1-tap instant action replay.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                            HorizontalDivider(Modifier.padding(vertical = 4.dp))

                            Text("Perk: 20% NFC Hardware Discount", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            Text("Use code PROTAP20 for 20% off all pre-formatted NTAG stickers on arbhlabs.com.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Text("Plans", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedCard(Modifier.weight(1f)) {
                            Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Monthly", style = MaterialTheme.typography.labelSmall)
                                Text("€1.99", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("/month", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                        OutlinedCard(Modifier.weight(1f)) {
                            Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Annual", style = MaterialTheme.typography.labelSmall)
                                Text("€19.99", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("/year", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                        OutlinedCard(Modifier.weight(1f)) {
                            Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Lifetime", style = MaterialTheme.typography.labelSmall)
                                Text("€49.99", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("one-time", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }

                Text("Activate License", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = licenseKeyInput,
                    onValueChange = { licenseKeyInput = it },
                    label = { Text("License Key or Admin Bypass") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        activating = true
                        statusMessage = null
                        onActivateLicense(licenseKeyInput) { res ->
                            activating = false
                            res.onSuccess { msg ->
                                isError = false
                                statusMessage = msg
                            }.onFailure { err ->
                                isError = true
                                statusMessage = err.message ?: "Activation failed"
                            }
                        }
                    },
                    enabled = !activating && !startingTrial && licenseKeyInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (activating) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Activate")
                    }
                }

                statusMessage?.let { msg ->
                    Text(
                        msg,
                        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsSheet(
    vm: TapRelayViewModel,
    onDismiss: () -> Unit
) {
    val logs by vm.tapLogs.collectAsState(initial = emptyList())

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = 24.dp, vertical = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Tap Diagnostics & Replay", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                "Execution history and latency telemetry. Tap 'Replay' to test-trigger any action without tapping the sticker.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (logs.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No action history recorded yet. Tap an NFC tag to record telemetry.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                }
            } else {
                Column(
                    Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    logs.forEach { log ->
                        ElevatedCard(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.padding(12.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier.size(10.dp).clip(CircleShape).background(
                                        if (log.success) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                                    )
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(log.tagName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                    val timeStr = java.time.Instant.ofEpochMilli(log.timestamp)
                                        .atZone(java.time.ZoneId.systemDefault())
                                        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
                                    val statusDetail = if (log.success) "${log.durationMs}ms" else (log.errorMessage ?: "Failed")
                                    Text(
                                        "$timeStr • ${log.providerId} • $statusDetail",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                FilledTonalButton(
                                    onClick = { vm.replay(log.tagId) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text("Replay", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun NfcStoreDialog(
    isPro: Boolean,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://arbhlabs.com/taprelay/tags")
                    )
                    context.startActivity(intent)
                    onDismiss()
                }
            ) {
                Text("Open Tag Store")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ShoppingCart, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Shop NFC Hardware")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "High-grade pre-formatted NFC tags tested for optimal read range with Pixel, Galaxy, and other Android devices.",
                    style = MaterialTheme.typography.bodyMedium
                )
                ElevatedCard(shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("• Standard Stickers (NTAG213 / NTAG215)", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                        Text("Ultra-thin adhesive stickers ideal for walls, desks, bedside tables, and nightstands.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text("• Anti-Metal Shielded Tags", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                        Text("Ferrite-backed tags engineered for metal surfaces, radiators, and metal-cased appliances.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("20% Hardware Discount", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("Use promo code PROTAP20 at checkout for 20% off all packs on arbhlabs.com.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }
    )
}
