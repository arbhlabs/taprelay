package com.arbhlabs.taprelay.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Weekend
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arbhlabs.taprelay.data.local.entity.TagEntity
import com.arbhlabs.taprelay.domain.model.ActionType
import com.arbhlabs.taprelay.domain.model.DiscoveredDevice
import com.arbhlabs.taprelay.ui.components.TapRelayPill

val ICONS: Map<String, ImageVector> = mapOf(
    "lamp" to Icons.Default.Lightbulb,
    "room" to Icons.Default.Weekend,
    "plug" to Icons.Default.Power,
    "switch" to Icons.Default.Bolt,
)

fun iconFor(key: String) = ICONS[key] ?: Icons.Default.Lightbulb

@Composable
fun TapRelayApp(vm: TapRelayViewModel) {
    val ui by vm.ui.collectAsState()
    val pill by vm.pill.collectAsState()
    val wizard by vm.wizard.collectAsState()
    val setupPrompt by vm.setupPrompt.collectAsState()

    var startedOnboarding by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        when {
            !ui.onboardingComplete && !startedOnboarding ->
                OnboardingScreen(onGetStarted = { startedOnboarding = true })

            !ui.onboardingComplete ->
                ConnectScreen(vm, onDone = { vm.completeOnboarding() })

            else -> HomeScreen(vm, ui.tags, ui.goveeConnected)
        }

        if (wizard.active) {
            Surface(Modifier.fillMaxSize()) { AddTagFlow(vm) }
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
            Icon(Icons.Default.Bolt, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(28.dp))
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
private fun ConnectScreen(vm: TapRelayViewModel, onDone: () -> Unit) {
    val state by vm.connectState.collectAsState()
    var key by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state) {
        if (state is TapRelayViewModel.ConnectState.Success) onDone()
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Connect Govee") }) }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Private alpha", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                "TapRelay controls your Govee lights using a personal key from the Govee Home app. " +
                    "Open the Govee Home app, go to Profile → Settings → Apply for API Key, then paste the key it emails you below.",
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedTextField(
                value = key,
                onValueChange = { key = it; vm.resetConnectState() },
                label = { Text("Govee key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            (state as? TapRelayViewModel.ConnectState.Error)?.let {
                Text(it.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = { vm.connectGovee(key) },
                enabled = state !is TapRelayViewModel.ConnectState.Validating,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (state is TapRelayViewModel.ConnectState.Validating)
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text("Connect")
            }
            Divider()
            Text("Google Home", style = MaterialTheme.typography.titleSmall)
            Text(
                "Google Home support needs provider access from Google and isn't enabled in this private alpha.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(vm: TapRelayViewModel, tags: List<TagEntity>, goveeConnected: Boolean) {
    var detail by remember { mutableStateOf<TagEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TapRelay") },
                actions = {
                    AssistChip(
                        onClick = {},
                        label = { Text(if (goveeConnected) "Govee connected" else "Not connected") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.CheckCircle, null,
                                tint = if (goveeConnected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline
                            )
                        }
                    )
                    Spacer(Modifier.width(8.dp))
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
        if (tags.isEmpty()) {
            Column(
                Modifier.padding(pad).fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("No tags yet", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Add a sticker and turn it into a physical smart-home button.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                tags.forEach { tag ->
                    ElevatedCard(
                        onClick = { detail = tag },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                    ) {
                        Row(
                            Modifier.padding(16.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(iconFor(tag.iconKey), null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    tag.friendlyName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    actionLabel(tag.actionType) + if (!tag.enabled) " • off" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { vm.testTag(tag) }) { Text("Test") }
                        }
                    }
                }
                Spacer(Modifier.height(80.dp))
            }
        }
    }

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
                "Currently ${actionLabel(tag.actionType).lowercase()} on “${tag.friendlyName}”. " +
                    "Changing the device or action does not require re-tapping the sticker.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onChangeMapping, Modifier.fillMaxWidth()) { Text("Change device or action") }
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
private fun AddTagFlow(vm: TapRelayViewModel) {
    val w by vm.wizard.collectAsState()

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
                WizardStep.SCAN -> {
                    Text("Hold an NFC sticker to the back of your phone", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "On a Pixel, the sensor is near the top-centre of the back. Keep the sticker still until you feel a tap.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    CircularProgressIndicator()
                    w.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }

                WizardStep.PICK_DEVICE -> {
                    Text("Choose a device", style = MaterialTheme.typography.titleMedium)
                    if (w.discovering) {
                        CircularProgressIndicator()
                    } else if (w.error != null) {
                        Text(w.error!!, color = MaterialTheme.colorScheme.error)
                        Button(onClick = { vm.retryDiscovery() }) { Text("Try again") }
                    } else if (w.devices.isEmpty()) {
                        Text("No controllable lights found on your Govee account.")
                        Button(onClick = { vm.retryDiscovery() }) { Text("Refresh") }
                    } else {
                        w.devices.forEach { d ->
                            ElevatedCard(onClick = { vm.selectDevice(d) }, modifier = Modifier.fillMaxWidth()) {
                                Text(d.name, Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }

                WizardStep.PICK_ACTION -> {
                    Text("Choose an action", style = MaterialTheme.typography.titleMedium)
                    ActionType.entries.forEach { a ->
                        ElevatedCard(onClick = { vm.selectAction(a) }, modifier = Modifier.fillMaxWidth()) {
                            Text(actionLabel(a), Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                        }
                    }
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

private fun actionLabel(a: ActionType) = when (a) {
    ActionType.TOGGLE -> "Toggle"
    ActionType.TURN_ON -> "Turn On"
    ActionType.TURN_OFF -> "Turn Off"
}
