package com.arbhlabs.taprelay.ui.actions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arbhlabs.taprelay.data.local.entity.TagEntity
import com.arbhlabs.taprelay.domain.model.ActionStep
import com.arbhlabs.taprelay.domain.model.ItemLabels
import com.arbhlabs.taprelay.ui.ICONS
import com.arbhlabs.taprelay.ui.TapRelayViewModel
import com.arbhlabs.taprelay.ui.iconFor

/**
 * The Magic Action editor: a name, an icon and a list of steps.
 *
 * There are no conditions, no variables and no expressions here, and that is the product. A step
 * is one of the owner's own actions or a pause, the order is the order it happens in, and the
 * whole thing can be run from this screen before it is ever attached to a tag.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MagicActionEditor(
    vm: TapRelayViewModel,
    existing: TagEntity?,
    onDone: () -> Unit
) {
    val candidates by vm.stepCandidates.collectAsState()

    var name by remember { mutableStateOf(existing?.friendlyName.orEmpty()) }
    var iconKey by remember { mutableStateOf(existing?.iconKey ?: "scene") }
    val steps = remember { existing?.magicSteps.orEmpty().toMutableStateList() }

    var showPicker by remember { mutableStateOf(false) }
    var showDelay by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    BackHandler {
        when {
            showPicker -> showPicker = false
            showDelay -> showDelay = false
            confirmDelete -> confirmDelete = false
            else -> onDone()
        }
    }

    val actionCount = steps.count { !it.isWait }
    val canSave = name.isNotBlank() && actionCount > 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "New Magic Action" else "Magic Action") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (existing != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete this Magic Action")
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
                    if (existing != null) {
                        FilledTonalButton(
                            onClick = { vm.testItem(existing) },
                            modifier = Modifier.height(52.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Test")
                        }
                    }
                    Button(
                        onClick = {
                            vm.saveMagicAction(existing, name.trim(), iconKey, steps.toList())
                            onDone()
                        },
                        enabled = canSave,
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) { Text(if (existing == null) "Save" else "Save changes") }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text("Bedtime") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    ICONS.forEach { (key, icon) ->
                        FilterChip(
                            selected = iconKey == key,
                            onClick = { iconKey = key },
                            label = { Icon(icon, contentDescription = key) }
                        )
                    }
                }
            }

            // Templates only while a brand-new sequence is still empty: once there are steps, an
            // offer to replace them is a trap rather than a shortcut.
            if (existing == null && steps.isEmpty()) {
                item {
                    Text(
                        "Start from",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 14.dp)
                    )
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MagicActionTemplates.ALL.forEach { template ->
                            TemplateRow(template) {
                                name = template.name
                                iconKey = template.iconKey
                                steps.clear()
                                steps.addAll(template.build(candidates))
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Steps",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (actionCount == 0) "" else "$actionCount in order",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (steps.isEmpty()) {
                item {
                    Text(
                        "No steps yet. Add the actions you want this to run, in order.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            }

            itemsIndexed(steps, key = { index, step -> "$index:${step.tagId}:${step.delayMs}" }) { index, step ->
                StepRow(
                    index = index,
                    step = step,
                    resolved = candidates.firstOrNull { it.tagId == step.tagId },
                    isFirst = index == 0,
                    isLast = index == steps.lastIndex,
                    onUp = {
                        if (index > 0) {
                            val moved = steps.removeAt(index)
                            steps.add(index - 1, moved)
                        }
                    },
                    onDown = {
                        if (index < steps.lastIndex) {
                            val moved = steps.removeAt(index)
                            steps.add(index + 1, moved)
                        }
                    },
                    onRemove = { steps.removeAt(index) }
                )
            }

            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = { showPicker = true },
                        enabled = steps.size < ActionStep.MAX_STEPS,
                        modifier = Modifier.weight(1f).height(50.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Add action")
                    }
                    FilledTonalButton(
                        onClick = { showDelay = true },
                        enabled = steps.isNotEmpty() && steps.size < ActionStep.MAX_STEPS,
                        modifier = Modifier.height(50.dp)
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Wait")
                    }
                }
            }

            item {
                if (steps.size >= ActionStep.MAX_STEPS) {
                    Text(
                        "That's the most steps one Magic Action can hold.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            item {
                Text(
                    "If one step can't be reached, the rest still run and TapRelay tells you which " +
                        "one didn't.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 14.dp)
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showPicker) {
        StepPickerSheet(
            candidates = candidates,
            alreadyUsed = steps.mapNotNull { it.tagId }.toSet(),
            onDismiss = { showPicker = false },
            onPick = { tag ->
                steps.add(ActionStep.run(tag.tagId, tag.friendlyName))
                showPicker = false
            }
        )
    }

    if (showDelay) {
        DelaySheet(
            onDismiss = { showDelay = false },
            onPick = { millis ->
                steps.add(ActionStep.wait(millis))
                showDelay = false
            }
        )
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${existing.friendlyName}?") },
            text = {
                Text(
                    "The actions it runs are kept. Any tag, controller button or widget pointing " +
                        "at this Magic Action will stop working."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteItem(existing)
                    confirmDelete = false
                    onDone()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Keep") }
            }
        )
    }
}

@Composable
private fun TemplateRow(template: MagicTemplate, onPick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = onPick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                iconFor(template.iconKey),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(template.name, fontWeight = FontWeight.Medium)
                Text(
                    template.blurb,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StepRow(
    index: Int,
    step: ActionStep,
    resolved: TagEntity?,
    isFirst: Boolean,
    isLast: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit
) {
    val missing = !step.isWait && resolved == null
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (missing) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(
                        if (step.isWait) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.primaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (step.isWait) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                } else {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            if (!step.isWait) {
                Icon(
                    iconFor(resolved?.iconKey ?: "lamp"),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        step.isWait -> ActionStep.describeDelay(step.delayMs)
                        else -> resolved?.friendlyName ?: step.label.ifBlank { "Deleted action" }
                    },
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val detail = when {
                    step.isWait -> "Pause before the next step"
                    missing -> "This action was deleted"
                    resolved != null -> stepDetail(resolved)
                    else -> ""
                }
                if (detail.isNotBlank()) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onUp, enabled = !isFirst) {
                Icon(Icons.Default.ArrowUpward, contentDescription = "Move up", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDown, enabled = !isLast) {
                Icon(Icons.Default.ArrowDownward, contentDescription = "Move down", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "Remove step", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepPickerSheet(
    candidates: List<TagEntity>,
    alreadyUsed: Set<String>,
    onDismiss: () -> Unit,
    onPick: (TagEntity) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding()) {
            Text(
                "Add an action",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )
            Text(
                "Anything you have already set up - lights, air, scenes, logs, apps, web requests.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )
            Spacer(Modifier.height(8.dp))
            if (candidates.isEmpty()) {
                Text(
                    "You have no actions yet. Add a light, a LastDose log or an app first.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    itemsIndexed(candidates, key = { _, tag -> tag.tagId }) { _, tag ->
                        ListItem(
                            modifier = Modifier.fillMaxWidth().clickable { onPick(tag) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            leadingContent = {
                                Icon(
                                    iconFor(tag.iconKey),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            headlineContent = {
                                Text(tag.friendlyName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = { Text(stepDetail(tag)) },
                            trailingContent = {
                                // Repeats are allowed - "lamp off, wait, lamp on" is a real thing -
                                // so this is a note rather than a block.
                                if (alreadyUsed.contains(tag.tagId)) {
                                    Text(
                                        "Already used",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DelaySheet(onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.navigationBarsPadding().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Wait", style = MaterialTheme.typography.titleLarge)
            Text(
                "Gives a light or an air conditioner a moment to settle before the next step.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionStep.DELAY_PRESETS.forEach { millis ->
                    AssistChip(
                        onClick = { onPick(millis) },
                        label = { Text(ActionStep.describeDelay(millis).removePrefix("Wait ")) }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** One line describing what an item does, shared by the picker and the step list. */
internal fun stepDetail(tag: TagEntity): String =
    if (tag.isMagicAction) "Magic Action" else ItemLabels.summary(tag)
