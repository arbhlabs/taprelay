package com.arbhlabs.taprelay.ui.lastdose

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arbhlabs.taprelay.data.local.entity.TagEntity
import com.arbhlabs.taprelay.execution.lastdose.LastDoseItem
import com.arbhlabs.taprelay.ui.TapRelayViewModel

/**
 * LastDose logs, set up as ordinary TapRelay items.
 *
 * Nothing here is controller-specific: what is saved is the same item a lamp is, so the moment it
 * exists it can be bound to a controller button on the Controllers screen, written to an NFC tag,
 * or fired by a place - all through the one `trigger -> item -> activation mode` route.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LastDoseScreen(vm: TapRelayViewModel, onDone: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val available by vm.lastDoseAvailable.collectAsState()
    val catalogue by vm.lastDoseItems.collectAsState()

    var editing by remember { mutableStateOf<TagEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<TagEntity?>(null) }

    BackHandler {
        when {
            showEditor -> {
                showEditor = false
                editing = null
            }
            confirmDelete != null -> {
                confirmDelete = null
            }
            else -> onDone()
        }
    }

    LaunchedEffect(Unit) { vm.refreshLastDose() }

    val items = ui.tags.filter { it.isLastDose }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LastDose Logs") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
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
                "A LastDose log becomes a TapRelay item. Point a controller button, an NFC tag or a " +
                    "place at it and one press writes one entry - LastDose never opens.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            when (available) {
                null -> Text("Checking LastDose…", style = MaterialTheme.typography.bodySmall)
                false -> ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("LastDose isn't available", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Install LastDose 7.1.10 or later on this phone, then come back. " +
                                "Existing LastDose items are kept meanwhile.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        TextButton(onClick = { vm.refreshLastDose() }) { Text("Check again") }
                    }
                }
                true -> Button(
                    onClick = { editing = null; showEditor = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add a LastDose log")
                }
            }

            Spacer(Modifier.height(16.dp))

            if (items.isEmpty()) {
                Text(
                    "No LastDose items yet.",
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
                            Row(
                                Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        tag.friendlyName,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        summaryOf(tag),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

    if (showEditor) {
        LastDoseEditorDialog(
            existing = editing,
            catalogue = catalogue.orEmpty(),
            onDismiss = { showEditor = false },
            onSave = { itemId, itemName, amount, unit, name ->
                vm.saveLastDoseItem(editing, itemId, itemName, amount, unit, name)
                showEditor = false
            }
        )
    }

    confirmDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Remove ${target.friendlyName}?") },
            text = {
                Text(
                    "This removes the TapRelay item and any controller button or place pointing at " +
                        "it. Entries already logged in LastDose are untouched."
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.deleteLastDoseItem(target); confirmDelete = null }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } }
        )
    }
}

private fun summaryOf(tag: TagEntity): String {
    val qualifier = listOf(tag.lastDoseAmount.orEmpty(), tag.lastDoseUnit.orEmpty())
        .filter { it.isNotBlank() }
        .joinToString(" ")
    val log = tag.lastDoseItemName.orEmpty().ifBlank { "Unknown log" }
    return if (qualifier.isBlank()) log else "$log • $qualifier"
}

/** Pick the log, then say how much. Both come pre-filled from LastDose's own defaults. */
@Composable
private fun LastDoseEditorDialog(
    existing: TagEntity?,
    catalogue: List<LastDoseItem>,
    onDismiss: () -> Unit,
    onSave: (itemId: Long, itemName: String, amount: String, unit: String, name: String) -> Unit
) {
    var selected by remember {
        mutableStateOf(
            catalogue.firstOrNull { it.id == existing?.lastDoseItemId }
                ?: existing?.let { LastDoseItem(it.lastDoseItemId ?: 0L, it.lastDoseItemName.orEmpty(), it.lastDoseUnit.orEmpty(), "") }
        )
    }
    var amount by remember { mutableStateOf(existing?.lastDoseAmount.orEmpty()) }
    var unit by remember { mutableStateOf(existing?.lastDoseUnit.orEmpty()) }
    var name by remember { mutableStateOf(existing?.friendlyName.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add a LastDose log" else "Edit LastDose item") },
        text = {
            Column {
                Text("Which log?", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                if (catalogue.isEmpty()) {
                    Text(
                        "LastDose has no logs to choose from yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                } else {
                    LazyColumn(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(catalogue, key = { it.id }) { option ->
                            val isSelected = selected?.id == option.id
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selected = option
                                        // LastDose's own defaults are almost always what is wanted.
                                        if (amount.isBlank()) amount = option.defaultAmount
                                        if (unit.isBlank()) unit = option.unit
                                        if (name.isBlank()) name = option.name
                                    }
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            option.name.ifBlank { "Log ${option.id}" },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val hint = listOf(option.defaultAmount, option.unit)
                                            .filter { it.isNotBlank() }.joinToString(" ")
                                        if (hint.isNotBlank()) {
                                            Text(
                                                hint,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
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
                }

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Amount") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Unit") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Left blank, LastDose uses the log's own default.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name in TapRelay") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            val target = selected
            TextButton(
                enabled = target != null && target.id > 0L,
                onClick = {
                    if (target != null) onSave(target.id, target.name, amount, unit, name)
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
