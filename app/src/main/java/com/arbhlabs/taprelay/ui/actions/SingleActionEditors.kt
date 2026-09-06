package com.arbhlabs.taprelay.ui.actions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arbhlabs.taprelay.data.local.entity.TagEntity
import com.arbhlabs.taprelay.domain.model.LaunchKind
import com.arbhlabs.taprelay.domain.model.PhoneAction
import com.arbhlabs.taprelay.execution.webhook.WebhookClient
import com.arbhlabs.taprelay.execution.webhook.WebhookMethod
import com.arbhlabs.taprelay.ui.ICONS
import com.arbhlabs.taprelay.ui.TapRelayViewModel

/** The shared frame every single-action editor sits in, so all three feel like one screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorScaffold(
    title: String,
    existing: TagEntity?,
    canSave: Boolean,
    saveLabel: String,
    onSave: () -> Unit,
    onTest: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onDone: () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    BackHandler {
        if (confirmDelete) confirmDelete = false else onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (onDelete != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete this action")
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (onTest != null) {
                        FilledTonalButton(onClick = onTest, modifier = Modifier.height(52.dp)) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Test")
                        }
                    }
                    Button(
                        onClick = onSave,
                        enabled = canSave,
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) { Text(saveLabel) }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }

    if (confirmDelete && onDelete != null && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${existing.friendlyName}?") },
            text = { Text("Anything pointing at this action will stop working.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    confirmDelete = false
                    onDone()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } }
        )
    }
}

@Composable
private fun IconRow(iconKey: String, onPick: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ICONS.forEach { (key, icon) ->
            FilterChip(
                selected = iconKey == key,
                onClick = { onPick(key) },
                label = { Icon(icon, contentDescription = key) }
            )
        }
    }
}

// ------------------------------------------------------------------ Open an app or a link

@Composable
fun OpenEditor(vm: TapRelayViewModel, existing: TagEntity?, onDone: () -> Unit) {
    val apps by vm.installedApps.collectAsState()

    var name by remember { mutableStateOf(existing?.friendlyName.orEmpty()) }
    var iconKey by remember { mutableStateOf(existing?.iconKey ?: "switch") }
    var kind by remember { mutableStateOf(existing?.deviceSku?.ifBlank { LaunchKind.APP } ?: LaunchKind.APP) }
    var target by remember { mutableStateOf(existing?.deviceId.orEmpty()) }
    var query by remember { mutableStateOf("") }

    val canSave = name.isNotBlank() && target.isNotBlank()

    EditorScaffold(
        title = if (existing == null) "Open an app or link" else "Open",
        existing = existing,
        canSave = canSave,
        saveLabel = if (existing == null) "Save" else "Save changes",
        onSave = {
            vm.saveLaunchItem(existing, name.trim(), iconKey, kind, target)
            onDone()
        },
        onTest = existing?.let { { vm.testItem(it) } },
        onDelete = existing?.let { { vm.deleteItem(it) } },
        onDone = onDone
    ) {
        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item { IconRow(iconKey) { iconKey = it } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = kind == LaunchKind.APP,
                    onClick = { kind = LaunchKind.APP; target = "" },
                    label = { Text("An app") }
                )
                FilterChip(
                    selected = kind == LaunchKind.LINK,
                    onClick = { kind = LaunchKind.LINK; target = "" },
                    label = { Text("A link") }
                )
            }
        }

        if (kind == LaunchKind.LINK) {
            item {
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text("Link") },
                    placeholder = { Text("https://") },
                    singleLine = true,
                    isError = target.isNotBlank() && !WebhookClient.isSupportedUrl(target),
                    supportingText = {
                        if (target.isNotBlank() && !WebhookClient.isSupportedUrl(target)) {
                            Text("Links must start with https://")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Find an app") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            when (val list = apps) {
                null -> item {
                    Text(
                        "Reading your apps…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    val filtered = list.filter { it.label.contains(query, ignoreCase = true) }
                    items(filtered, key = { it.packageName }) { app ->
                        val selected = target == app.packageName
                        ListItem(
                            modifier = Modifier.fillMaxWidth().clickable {
                                target = app.packageName
                                if (name.isBlank()) name = app.label
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = if (selected) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    androidx.compose.ui.graphics.Color.Transparent
                                }
                            ),
                            headlineContent = {
                                Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            trailingContent = { RadioButton(selected = selected, onClick = null) }
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ My phone (media, volume, torch, DND)

@Composable
fun PhoneEditor(vm: TapRelayViewModel, existing: TagEntity?, onDone: () -> Unit) {
    val context = LocalContext.current

    var name by remember { mutableStateOf(existing?.friendlyName.orEmpty()) }
    var iconKey by remember { mutableStateOf(existing?.iconKey ?: "switch") }
    var action by remember {
        mutableStateOf(existing?.deviceId?.ifBlank { PhoneAction.MEDIA_PLAY_PAUSE } ?: PhoneAction.MEDIA_PLAY_PAUSE)
    }
    var granted by remember { mutableStateOf(vm.hasDndAccess()) }
    val needsDnd = PhoneAction.needsDndAccess(action)

    // The switch is granted in system settings, so it can change while this screen is open.
    LaunchedEffect(Unit) { granted = vm.hasDndAccess() }

    EditorScaffold(
        title = if (existing == null) "My phone" else "My phone",
        existing = existing,
        canSave = name.isNotBlank(),
        saveLabel = if (existing == null) "Save" else "Save changes",
        onSave = {
            vm.savePhoneItem(existing, name.trim(), iconKey, action)
            onDone()
        },
        onTest = existing?.let { { vm.testItem(it) } },
        onDelete = existing?.let { { vm.deleteItem(it) } },
        onDone = onDone
    ) {
        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                placeholder = { Text(PhoneAction.label(action)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item { IconRow(iconKey) { iconKey = it } }

        PhoneAction.CATEGORIES.forEach { (heading, keys) ->
            item(key = "head-$heading") {
                Text(
                    heading,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            items(keys, key = { it }) { key ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    onClick = { action = key; if (name.isBlank()) name = PhoneAction.label(key) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(PhoneAction.label(key), Modifier.weight(1f))
                        RadioButton(selected = action == key, onClick = { action = key })
                    }
                }
            }
        }

        if (needsDnd && !granted) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Android needs your permission", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Only you can let an app quieten your phone. Turn TapRelay on in the " +
                                "list Android shows, then come back.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        FilledTonalButton(onClick = {
                            context.startActivity(vm.dndAccessIntent())
                        }) { Text("Open the setting") }
                    }
                }
            }
        }
        item {
            Text(
                when {
                    needsDnd ->
                        "Alarms still ring. TapRelay uses the priority setting rather than total silence."
                    action.startsWith("media_") ->
                        "Goes to whatever is playing — Spotify, YouTube Music, a podcast. No account needed."
                    action.startsWith("flashlight_") ->
                        "Uses the rear flashlight. Needs no permission."
                    else ->
                        "Changes the media volume and shows the system volume panel. Needs no permission."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ------------------------------------------------------------------ Web request (advanced)

@Composable
fun WebRequestEditor(vm: TapRelayViewModel, existing: TagEntity?, onDone: () -> Unit) {
    var name by remember { mutableStateOf(existing?.friendlyName.orEmpty()) }
    var iconKey by remember { mutableStateOf(existing?.iconKey ?: "plug") }
    var url by remember { mutableStateOf(existing?.webhookUrl.orEmpty()) }
    var method by remember {
        mutableStateOf(
            runCatching { WebhookMethod.valueOf(existing?.webhookMethod ?: "POST") }
                .getOrDefault(WebhookMethod.POST)
        )
    }
    var body by remember { mutableStateOf(existing?.webhookBody.orEmpty()) }
    var presetHelp by remember { mutableStateOf("") }
    var secretHeader by remember { mutableStateOf(existing?.webhookSecretHeader.orEmpty()) }
    var secretValue by remember { mutableStateOf("") }
    var hasStoredSecret by remember { mutableStateOf(false) }

    LaunchedEffect(existing?.tagId) {
        hasStoredSecret = existing != null && vm.hasWebhookSecret(existing.tagId)
    }

    val urlValid = url.isBlank() || WebhookClient.isSupportedUrl(url)
    val canSave = name.isNotBlank() && url.isNotBlank() && urlValid

    EditorScaffold(
        title = "Web request",
        existing = existing,
        canSave = canSave,
        saveLabel = if (existing == null) "Save" else "Save changes",
        onSave = {
            vm.saveWebhookItem(
                existing = existing,
                name = name.trim(),
                iconKey = iconKey,
                url = url,
                method = method,
                body = body,
                secretHeader = secretHeader,
                secretValue = secretValue
            )
            onDone()
        },
        onTest = existing?.let { { vm.testItem(it) } },
        onDelete = existing?.let { { vm.deleteItem(it) } },
        onDone = onDone
    ) {
        item {
            Text(
                "For an address you already have - a smart relay on your network, a home server, " +
                    "an automation service. TapRelay sends the request straight from this phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (existing == null) {
            item {
                Text(
                    "Start from",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.arbhlabs.taprelay.domain.model.WebhookPreset.ALL.forEach { preset ->
                        FilterChip(
                            selected = presetHelp == preset.help,
                            onClick = {
                                method = preset.method
                                if (url.isBlank()) url = preset.urlHint.takeIf { it != "https://" }.orEmpty()
                                if (body.isBlank()) body = preset.bodyTemplate
                                if (name.isBlank()) name = preset.label
                                presetHelp = preset.help
                            },
                            label = { Text(preset.label) }
                        )
                    }
                }
            }
            if (presetHelp.isNotBlank()) {
                item {
                    Text(
                        presetHelp,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item { IconRow(iconKey) { iconKey = it } }
        item {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Address") },
                placeholder = { Text("https://") },
                singleLine = true,
                isError = !urlValid,
                supportingText = { if (!urlValid) Text("Addresses must start with https://") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WebhookMethod.entries.forEach { m ->
                    FilterChip(
                        selected = method == m,
                        onClick = { method = m },
                        label = { Text(m.name) }
                    )
                }
            }
        }
        if (method != WebhookMethod.GET) {
            item {
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("What to send (optional)") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp)
                )
            }
        }
        item {
            Text(
                "Key (optional)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        item {
            OutlinedTextField(
                value = secretHeader,
                onValueChange = { secretHeader = it },
                label = { Text("Name") },
                placeholder = { Text("Authorization") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = secretValue,
                onValueChange = { secretValue = it },
                label = { Text(if (hasStoredSecret) "Replace the saved key" else "Value") },
                placeholder = { Text(if (hasStoredSecret) "Leave blank to keep it" else "") },
                singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Text(
                "The key is encrypted on this phone and is never written to your action history, " +
                    "shared, or stored on a tag. Only the name and the address are.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}
