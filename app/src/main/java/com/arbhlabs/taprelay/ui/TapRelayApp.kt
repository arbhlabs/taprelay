package com.arbhlabs.taprelay.ui

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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Weekend
import androidx.compose.material3.*
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
import androidx.compose.ui.unit.dp
import com.arbhlabs.taprelay.data.local.entity.TagEntity
import com.arbhlabs.taprelay.domain.model.ActionType
import com.arbhlabs.taprelay.domain.model.Brightness
import com.arbhlabs.taprelay.domain.model.DiscoveredDevice
import com.arbhlabs.taprelay.domain.model.DiscoveredScene
import com.arbhlabs.taprelay.domain.model.LightPresets
import com.arbhlabs.taprelay.domain.provider.GOVEE_PROVIDER_ID
import com.arbhlabs.taprelay.domain.provider.TUYA_PROVIDER_ID
import com.arbhlabs.taprelay.ui.components.TapRelayPill
import kotlin.math.roundToInt

val ICONS: Map<String, ImageVector> = mapOf(
    "lamp" to Icons.Default.Lightbulb,
    "room" to Icons.Default.Weekend,
    "plug" to Icons.Default.Power,
    "switch" to Icons.Default.Bolt,
    "scene" to Icons.Default.AutoAwesome
)

fun iconFor(key: String) = ICONS[key] ?: Icons.Default.Lightbulb

@Composable
fun TapRelayApp(vm: TapRelayViewModel) {
    val ui by vm.ui.collectAsState()
    val pill by vm.pill.collectAsState()
    val wizard by vm.wizard.collectAsState()
    val setupPrompt by vm.setupPrompt.collectAsState()
    val nfcReady by vm.nfcReady.collectAsState()

    var startedOnboarding by rememberSaveable { mutableStateOf(false) }
    var showConnections by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        when {
            !ui.onboardingComplete && !startedOnboarding ->
                OnboardingScreen(onGetStarted = { startedOnboarding = true })

            !ui.onboardingComplete ->
                ConnectionsScreen(
                    vm = vm,
                    isOnboarding = true,
                    onDone = { vm.completeOnboarding() }
                )

            showConnections ->
                ConnectionsScreen(
                    vm = vm,
                    isOnboarding = false,
                    onDone = { showConnections = false }
                )

            else ->
                HomeScreen(
                    vm = vm,
                    tags = ui.tags,
                    goveeConnected = ui.goveeConnected,
                    tuyaConnected = ui.tuyaConnected,
                    nfcReady = nfcReady,
                    onOpenConnections = { showConnections = true }
                )
        }

        if (wizard.active) {
            Surface(Modifier.fillMaxSize()) { AddTagFlow(vm, nfcReady) }
        }

        TapRelayPill(feedback = pill, onDismiss = { vm.clearPill() }, modifier = Modifier.align(Alignment.TopCenter))

        setupPrompt?.let { id ->
            AlertDialog(
                onDismissRequest = { vm.dismissSetupPrompt() },
                title = { Text("New tag") },
                text = { Text("This TapRelay tag isn't set up on this phone yet. Set it up now?") },
                confirmButton = { TextButton(onClick = { vm.configureUnregisteredTag(id) }) { Text("Set up") } },
                dismissButton = { TextButton(onClick = { vm.dismissSetupPrompt() }) { Text("Not now") } }
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
    nfcReady: Boolean,
    onOpenConnections: () -> Unit
) {
    var detail by remember { mutableStateOf<TagEntity?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    val anyConnected = goveeConnected || tuyaConnected

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TapRelay") },
                actions = {
                    AssistChip(
                        onClick = onOpenConnections,
                        label = {
                            Text(
                                if (anyConnected) {
                                    val parts = mutableListOf<String>()
                                    if (goveeConnected) parts.add("Govee")
                                    if (tuyaConnected) parts.add("Smart Life")
                                    parts.joinToString(" + ")
                                } else "Not connected"
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
                            text = { Text("Connections") },
                            onClick = { menuOpen = false; onOpenConnections() }
                        )
                        DropdownMenuItem(
                            text = { Text("About TapRelay") },
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
                tags.forEach { tag ->
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
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(iconFor(tag.iconKey), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    tag.friendlyName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                val providerLabel = if (tag.providerId == TUYA_PROVIDER_ID) "Smart Life" else "Govee"
                                val actLabel = tagActionSummary(tag)
                                Text(
                                    "$providerLabel • $actLabel" + if (!tag.enabled) " • off" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { vm.testTag(tag) }) { Text("Test") }
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
    onDelete: () -> Unit
) {
    var name by remember(tag.tagId) { mutableStateOf(tag.friendlyName) }
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                "Currently ${tagActionSummary(tag).lowercase()} on ${tag.allTargets.joinToString { it.name.ifBlank { "a light" } }}. " +
                    "Changing the device, scene, or action does not require re-tapping the sticker.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
private fun AddTagFlow(vm: TapRelayViewModel, nfcReady: Boolean) {
    val w by vm.wizard.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Devices, 1 = Scenes

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (w.editingExisting) "Change mapping" else "Add tag") },
            navigationIcon = {
                IconButton(onClick = { vm.cancelWizard() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close")
                }
            }
        )
    }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (w.step) {
                WizardStep.SCAN -> ScanStep(w, vm, nfcReady)

                WizardStep.PICK_DEVICE -> {
                    Text("What should this tag control?", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Pick one light, or several to control them together.",
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
                                ElevatedCard(
                                    onClick = { vm.toggleDeviceSelection(d) },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    Row(
                                        Modifier.padding(16.dp).fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Lightbulb, null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(d.name, style = MaterialTheme.typography.titleMedium)
                                            // Govee exposes a room grouping as a device of its
                                            // own, and its API only ever lets that switch on and
                                            // off — so say so rather than leaving the user to
                                            // wonder why it cannot take a colour.
                                            val pLabel = when {
                                                d.providerId == TUYA_PROVIDER_ID -> "Smart Life"
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
                            Button(
                                onClick = { vm.continueFromDevices() },
                                enabled = w.selectedDevices.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 8.dp)
                            ) {
                                Text(
                                    when (w.selectedDevices.size) {
                                        0 -> "Pick a light"
                                        1 -> "Continue"
                                        else -> "Continue with ${w.selectedDevices.size} lights"
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
                    Text("What should it do?", style = MaterialTheme.typography.titleMedium)
                    val actions = buildList {
                        addAll(listOf(ActionType.TOGGLE, ActionType.TURN_ON, ActionType.TURN_OFF))
                        val picked = w.selectedDevices.ifEmpty { listOfNotNull(w.device) }
                        // Offered when any picked light can do it. A group is often a mix of
                        // colour bulbs and plain on/off lights, and the colour ones should
                        // still be settable; the ones that cannot are reported after the tap.
                        if (picked.any { it.supportsBrightness }) add(ActionType.SET_BRIGHTNESS)
                        if (picked.any { it.supportsColor }) add(ActionType.SET_COLOR)
                        if (picked.any { it.supportsColor && it.supportsBrightness }) add(ActionType.SET_SCENE)
                    }
                    val pickedForHint = w.selectedDevices.ifEmpty { listOfNotNull(w.device) }
                    actions.forEach { a ->
                        val capable = when (a) {
                            ActionType.SET_BRIGHTNESS -> pickedForHint.count { it.supportsBrightness }
                            ActionType.SET_COLOR -> pickedForHint.count { it.supportsColor }
                            ActionType.SET_SCENE ->
                                pickedForHint.count { it.supportsColor && it.supportsBrightness }
                            else -> pickedForHint.size
                        }
                        ElevatedCard(onClick = { vm.selectAction(a) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(actionLabel(a), style = MaterialTheme.typography.titleMedium)
                                if (capable < pickedForHint.size) {
                                    Text(
                                        "$capable of ${pickedForHint.size} of your lights can do this",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                WizardStep.TUNE -> {
                    val wantsColor = w.action == ActionType.SET_COLOR || w.action == ActionType.SET_SCENE
                    val wantsBrightness =
                        w.action == ActionType.SET_BRIGHTNESS || w.action == ActionType.SET_SCENE

                    if (wantsColor) {
                        Text("Which colour?", style = MaterialTheme.typography.titleMedium)
                        LightPresets.ALL.chunked(4).forEach { row ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                            ) {
                                row.forEach { preset ->
                                    val selected = w.colorRgb == preset.rgb
                                    Box(
                                        Modifier
                                            .size(56.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF000000L.toInt() or preset.rgb))
                                            .border(
                                                width = if (selected) 3.dp else 1.dp,
                                                color = if (selected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.outlineVariant,
                                                shape = CircleShape
                                            )
                                            .clickable { vm.setColor(preset.rgb) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (selected) {
                                            Icon(
                                                Icons.Default.Check,
                                                null,
                                                tint = Color.Black.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Text(
                            LightPresets.nameFor(w.colorRgb),
                            style = MaterialTheme.typography.titleMedium
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
                        if (w.action == ActionType.SET_SCENE) {
                            "Every tap sets all of these lights to this exact colour and brightness."
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
                Text("TapRelay by ARBH Labs — version 0.0.7 (alpha).", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "This is an early internal test build. Things may change or break.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Smart-home control uses your own personal developer credentials under a Bring-Your-Own-Key model. " +
                        "TapRelay is not affiliated with, sponsored by, or endorsed by Govee, Tuya, Smart Life, or Google.",
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
private fun ScanStep(w: WizardState, vm: TapRelayViewModel, nfcReady: Boolean) {
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

/** What a saved tag does, including the brightness or colour it was given. */
private fun tagActionSummary(tag: TagEntity): String {
    val action = when (tag.actionType) {
        ActionType.SET_BRIGHTNESS ->
            "Brightness ${Brightness.clampPercent(tag.brightnessPercent ?: Brightness.DEFAULT_PERCENT)}%"
        ActionType.SET_COLOR -> LightPresets.nameFor(tag.colorRgb ?: 0)
        ActionType.SET_SCENE ->
            "${LightPresets.nameFor(tag.colorRgb ?: 0)} " +
                "${Brightness.clampPercent(tag.brightnessPercent ?: Brightness.DEFAULT_PERCENT)}%"
        else -> actionLabel(tag.actionType)
    }
    val count = tag.allTargets.size
    return if (count > 1) "$action • $count lights" else action
}

/** Govee reports a Home room grouping as a device with this sku. */
private const val GOVEE_GROUP_SKU = "SameModeGroup"

private fun actionLabel(a: ActionType) = when (a) {
    ActionType.TOGGLE -> "Toggle"
    ActionType.TURN_ON -> "Turn On"
    ActionType.TURN_OFF -> "Turn Off"
    ActionType.SET_BRIGHTNESS -> "Set Brightness"
    ActionType.SET_COLOR -> "Set Colour"
    ActionType.SET_SCENE -> "Set Colour + Brightness"
    ActionType.RUN_SCENE -> "Run Scene"
}
