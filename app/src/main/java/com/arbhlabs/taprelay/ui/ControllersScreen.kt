package com.arbhlabs.taprelay.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arbhlabs.taprelay.controller.model.ControllerDevice
import com.arbhlabs.taprelay.controller.model.ControllerInput
import com.arbhlabs.taprelay.data.local.entity.ControllerMappingEntity
import com.arbhlabs.taprelay.data.local.entity.TagEntity
import androidx.compose.ui.platform.LocalContext
import com.arbhlabs.taprelay.data.prefs.AodDensity
import com.arbhlabs.taprelay.domain.model.ActivationMode
import com.arbhlabs.taprelay.ui.remote.RemoteModeActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControllersScreen(
    vm: TapRelayViewModel,
    onDone: () -> Unit
) {
    val ui by vm.ui.collectAsState()
    val controllers by vm.connectedControllers.collectAsState()
    val mappings by vm.controllerMappings.collectAsState()
    val isLearning by vm.isControllerLearning.collectAsState()
    val capturedInput by vm.capturedControllerInput.collectAsState()
    val keepAwake by vm.keepScreenAwake.collectAsState()

    var showAssignDialog by remember { mutableStateOf(false) }
    var mappingStep by remember { mutableStateOf(1) } // 1: Press button, 2: Pick action
    var selectedTargetTag by remember { mutableStateOf<TagEntity?>(null) }
    // null means: do whatever the item itself is set to.
    var selectedActivation by remember { mutableStateOf<ActivationMode?>(null) }
    var editingMapping by remember { mutableStateOf<ControllerMappingEntity?>(null) }

    BackHandler {
        if (showAssignDialog) {
            if (mappingStep == 2 && editingMapping == null) {
                mappingStep = 1
            } else {
                showAssignDialog = false
                vm.stopControllerLearning()
            }
        } else {
            onDone()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Controllers & Remotes") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refreshControllers() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh controllers")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            // 1. Connected Gamepads
            item {
                Text(
                    text = "Connected Gamepads",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (controllers.isEmpty()) {
                item {
                    ElevatedCard(
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.SportsEsports,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "No controller detected",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Pair any Xbox, PlayStation, 8BitDo, or standard Bluetooth gamepad in Android Settings. It will appear here automatically.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                items(controllers, key = { it.id }) { dev ->
                    ElevatedCard(
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.SportsEsports,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    dev.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(Color(0xFF4CAF50), CircleShape)
                                    )
                                    Text(
                                        "Connected • Ready",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF4CAF50)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Remote Mode - the closest Android allows to using the pad without the app.
            item {
                val context = LocalContext.current
                ElevatedCard(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Remote Mode",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "A dimmed, always-awake screen that shows over your lock screen, so the " +
                                "phone can sit on a desk and the controller just works. Add the " +
                                "\"TapRelay Remote\" tile to Quick Settings to get here from anywhere " +
                                "without opening the app.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Android delivers game-controller buttons only to the app that is in " +
                                "front, so no app can read your pad from the background without an " +
                                "accessibility service. TapRelay will not do that.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        FilledTonalButton(
                            onClick = { context.startActivity(RemoteModeActivity.intent(context)) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.SportsEsports, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Start Remote Mode")
                        }
                    }
                }
            }

            // 3. Preferences - four switches, deliberately kept to one card.
            item {
                val rumble by vm.controllerRumble.collectAsState()
                val haptics by vm.phoneHaptics.collectAsState()
                val autoDim by vm.remoteAutoDim.collectAsState()
                val density by vm.aodDensity.collectAsState()

                ElevatedCard(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        PreferenceRow(
                            title = "Controller rumble",
                            hint = "Buzz the pad in your hand when a button fires. Pads without a motor ignore this.",
                            checked = rumble,
                            onCheckedChange = { vm.setControllerRumble(it) }
                        )
                        PreferenceRow(
                            title = "Phone haptics",
                            hint = "Vibrate the phone on taps and actions.",
                            checked = haptics,
                            onCheckedChange = { vm.setPhoneHaptics(it) }
                        )
                        PreferenceRow(
                            title = "Dim Remote Mode when idle",
                            hint = "Fade to near-black after a few seconds. Touch or a button press brightens it.",
                            checked = autoDim,
                            onCheckedChange = { vm.setRemoteAutoDim(it) }
                        )
                        // Everything else about the always-on face lives on its own screen, so
                        // this one has a single home rather than two half-settings pages.
                        PreferenceRow(
                            title = "Keep screen awake in the app",
                            hint = "For a docked phone, without leaving the main screen.",
                            checked = keepAwake,
                            onCheckedChange = { vm.setKeepScreenAwake(it) }
                        )
                    }
                }
            }

            // 3. Button Mappings Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Button Mappings (${mappings.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    FilledTonalButton(
                        onClick = {
                            selectedTargetTag = null
                            selectedActivation = null
                            mappingStep = 1
                            showAssignDialog = true
                            vm.startControllerLearning()
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add Mapping")
                    }
                }
            }

            // 4. Mappings List
            if (mappings.isEmpty()) {
                item {
                    ElevatedCard(
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "No button mappings yet",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Tap 'Add Mapping' to assign controller buttons (A, B, D-pad, bumpers, triggers, or combinations) to any TapRelay smart home action.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            } else {
                items(mappings, key = { it.id }) { mapping ->
                    val linkedTag = ui.tags.firstOrNull { it.tagId == mapping.tagId }
                    ElevatedCard(
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Input Button Badge
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.padding(end = 12.dp)
                            ) {
                                Text(
                                    text = mapping.inputLabel,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            // Action Info
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clickable { editingMapping = mapping }
                            ) {
                                Text(
                                    text = linkedTag?.friendlyName ?: "Mapped Action",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val effective = mapping.activationMode
                                    ?: linkedTag?.activation
                                    ?: ActivationMode.EXECUTE
                                Text(
                                    text = mapping.controllerName + " \u2022 " + effective.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Test Button
                            IconButton(onClick = { vm.testControllerMapping(mapping.tagId) }) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "Test mapping",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            // Enable Toggle
                            Switch(
                                checked = mapping.enabled,
                                onCheckedChange = { vm.toggleControllerMapping(mapping) }
                            )

                            // Delete Button
                            IconButton(onClick = { vm.deleteControllerMapping(mapping.id) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete mapping",
                                    tint = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    // Assign / Learning surface.
    //
    // Deliberately NOT a Dialog. A Compose Dialog lives in its own window, and Android's
    // InputDispatcher delivers gamepad events only to the focused window - so inside a dialog
    // the controller just drives system focus and TapRelay never sees the press. Drawn in the
    // activity's own window, MainActivity.dispatchKeyEvent receives every button.
    if (showAssignDialog) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { }
            ) {
                Column(
                    Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        if (mappingStep == 1) "Press a Controller Button" else "Assign Action",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                if (mappingStep == 1) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                        val scale by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 1.12f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(800),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "scale"
                        )

                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .scale(if (capturedInput == null) scale else 1f)
                                .background(
                                    if (capturedInput != null) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.SportsEsports,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = if (capturedInput != null) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline
                            )
                        }

                        if (capturedInput == null) {
                            Text(
                                "Press any button, D-pad direction, bumper, or combination (e.g. LB + A) on your connected controller.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "Detected Input:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Spacer(Modifier.height(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        capturedInput!!.label,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Step 2: Pick which existing TapRelay tag / smart home action to link
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Choose which action or LastDose log should trigger when you press ${capturedInput?.label}:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (ui.tags.isEmpty()) {
                            Text(
                                "No actions or LastDose logs configured yet. Please configure at least one smart home device or LastDose log first.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(ui.tags, key = { it.tagId }) { tag ->
                                    val isSelected = selectedTargetTag?.tagId == tag.tagId
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedTargetTag = tag }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                iconFor(tag.iconKey),
                                                contentDescription = null,
                                                tint = if (isSelected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(Modifier.width(12.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    tag.friendlyName,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                                    else MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    if (tag.isLastDose) "LastDose log"
                                                    else tag.providerId.replaceFirstChar { it.uppercase() },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                            }
                                            if (isSelected) {
                                                Icon(
                                                    Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            Text(
                                "When this button is pressed",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 190.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                ActivationModePicker(
                                    selected = selectedActivation,
                                    onSelect = { selectedActivation = it },
                                    inheritLabel = "Same as the item",
                                    onInherit = { selectedActivation = null }
                                )
                            }
                        }
                    }
                }

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                if (mappingStep == 2) {
                                    mappingStep = 1
                                } else {
                                    vm.stopControllerLearning()
                                    showAssignDialog = false
                                }
                            }
                        ) {
                            Text(if (mappingStep == 2) "Back" else "Cancel")
                        }
                        Spacer(Modifier.width(8.dp))
                        if (mappingStep == 1) {
                            Button(
                                onClick = { mappingStep = 2 },
                                enabled = capturedInput != null
                            ) { Text("Choose Action") }
                        } else {
                            Button(
                                onClick = {
                                    val input = capturedInput
                                    val tag = selectedTargetTag
                                    if (input != null && tag != null) {
                                        val dev = controllers.firstOrNull()
                                        val descriptor = dev?.descriptor ?: "*"
                                        val devName = dev?.name ?: "Game Controller"
                                        vm.saveControllerMapping(
                                            controllerDescriptor = descriptor,
                                            controllerName = devName,
                                            inputKey = input.key,
                                            inputLabel = input.label,
                                            tagId = tag.tagId,
                                            activationMode = selectedActivation
                                        )
                                        vm.stopControllerLearning()
                                        showAssignDialog = false
                                    }
                                },
                                enabled = selectedTargetTag != null
                            ) { Text("Save Mapping") }
                        }
                    }
                }
            }
        }
    }

    editingMapping?.let { mapping ->
        val linkedTag = ui.tags.firstOrNull { it.tagId == mapping.tagId }
        AlertDialog(
            onDismissRequest = { editingMapping = null },
            title = { Text(mapping.inputLabel) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "What pressing this button should do with " +
                            (linkedTag?.friendlyName ?: "this action") + ".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        ActivationModePicker(
                            selected = mapping.activationMode,
                            onSelect = {
                                vm.setControllerMappingActivationMode(mapping, it)
                                editingMapping = null
                            },
                            inheritLabel = "Same as the item",
                            onInherit = {
                                vm.setControllerMappingActivationMode(mapping, null)
                                editingMapping = null
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { editingMapping = null }) { Text("Done") }
            }
        )
    }
}

/** One preference line. Kept deliberately plain so the card never turns into a settings screen. */
@Composable
private fun PreferenceRow(
    title: String,
    hint: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
