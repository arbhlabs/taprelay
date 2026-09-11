package com.arbhlabs.taprelay.ui.pc

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arbhlabs.taprelay.data.local.entity.TagEntity
import com.arbhlabs.taprelay.domain.model.ItemLabels
import com.arbhlabs.taprelay.pc.PcRelayAction
import com.arbhlabs.taprelay.pc.PcRelayProtocol
import com.arbhlabs.taprelay.ui.TapRelayViewModel

/**
 * Pair a Windows PC running TapRelay PC Relay, then add PC actions as ordinary items - so a
 * controller button, an NFC tag or a place can press play/pause, a shortcut, open a link or lock
 * the PC through the same trigger -> item route as a lamp.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcRelayScreen(vm: TapRelayViewModel, onDone: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val creds by vm.pcRelayCredentials.collectAsState()
    val found by vm.pcFound.collectAsState()
    val busy by vm.pcBusy.collectAsState()
    val message by vm.pcMessage.collectAsState()

    var pairing by remember { mutableStateOf<PcRelayProtocol.DiscoveryResponse?>(null) }
    var editing by remember { mutableStateOf<TagEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<TagEntity?>(null) }

    val items = ui.tags.filter { it.isPcRelay }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Windows PC") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Control your PC from a controller button, NFC tag or place: media keys, any keyboard " +
                    "shortcut, open a link or app, lock, sleep. Needs TapRelay PC Relay running on the PC, " +
                    "on the same network.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val paired = creds
                    if (paired != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text("PC paired", fontWeight = FontWeight.SemiBold)
                                Text(
                                    paired.baseUrl.removePrefix("http://"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            TextButton(onClick = { vm.unpairPc() }) { Text("Unpair") }
                        }
                    } else {
                        Text("Pair your PC", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Run TapRelay PC Relay on the PC. It shows a 6-digit code.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(onClick = { vm.findPcs() }, enabled = !busy) { Text("Find my PC") }
                        found?.forEach { pc ->
                            OutlinedButton(onClick = { pairing = pc }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Computer, null)
                                Spacer(Modifier.width(8.dp))
                                Text("${pc.name}  •  ${pc.host}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    message?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { editing = null; showEditor = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add a PC action")
            }
            Spacer(Modifier.height(16.dp))

            if (items.isEmpty()) {
                Text(
                    "No PC actions yet. Add one, then map it to a button on Controllers & Remotes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(items, key = { it.tagId }) { tag ->
                        ElevatedCard(
                            Modifier
                                .fillMaxWidth()
                                .clickable { editing = tag; showEditor = true }
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Computer, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(tag.friendlyName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        ItemLabels.summary(tag),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                TextButton(onClick = { vm.testItem(tag) }) { Text("Test") }
                                IconButton(onClick = { confirmDelete = tag }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pairing?.let { pc ->
        var code by remember(pc) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pairing = null },
            title = { Text("Pair ${pc.name}") },
            text = {
                Column {
                    Text("Type the 6-digit code shown in TapRelay PC Relay on the PC.")
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = code,
                        onValueChange = { v -> code = v.filter { it.isDigit() }.take(6) },
                        label = { Text("Code") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = code.length == 6, onClick = { vm.pairPc(pc, code); pairing = null }) { Text("Pair") }
            },
            dismissButton = { TextButton(onClick = { pairing = null }) { Text("Cancel") } }
        )
    }

    if (showEditor) {
        PcActionEditor(
            existing = editing,
            onDismiss = { showEditor = false },
            onSave = { action, value, name ->
                vm.savePcItem(editing, action, value, name)
                showEditor = false
            }
        )
    }

    confirmDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Remove ${target.friendlyName}?") },
            text = { Text("This removes the item and any controller button, tag or place pointing at it.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteLastDoseItem(target); confirmDelete = null }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun PcActionEditor(
    existing: TagEntity?,
    onDismiss: () -> Unit,
    onSave: (action: String, value: String, name: String) -> Unit
) {
    var action by remember { mutableStateOf(existing?.deviceId?.takeIf { it in PcRelayAction.all } ?: PcRelayAction.MEDIA_PLAY_PAUSE) }
    var value by remember { mutableStateOf(existing?.deviceSku.orEmpty()) }
    var name by remember { mutableStateOf(existing?.friendlyName.orEmpty()) }
    val spec = PcRelayAction.spec(action)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add a PC action" else "Edit PC action") },
        text = {
            Column {
                LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(PcRelayAction.specs, key = { it.id }) { option ->
                        val selected = option.id == action
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { action = option.id }
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(option.label, Modifier.weight(1f), fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                                if (selected) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                if (spec?.valueLabel != null) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        label = { Text(spec.valueLabel) },
                        placeholder = { Text(spec.valueHint.orEmpty()) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name in TapRelay") },
                    placeholder = { Text(PcRelayAction.label(action)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            val needsValue = spec?.valueLabel != null
            TextButton(
                enabled = !needsValue || value.isNotBlank(),
                onClick = { onSave(action, if (needsValue) value else "", name) }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
