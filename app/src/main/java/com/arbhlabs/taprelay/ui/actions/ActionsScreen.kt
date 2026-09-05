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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arbhlabs.taprelay.data.local.entity.TagEntity
import com.arbhlabs.taprelay.domain.model.ItemLabels
import com.arbhlabs.taprelay.ui.TapRelayViewModel
import com.arbhlabs.taprelay.ui.iconFor

/** Which editor the Actions screen currently has open. */
private sealed interface Editing {
    data class Magic(val existing: TagEntity?) : Editing
    data class Web(val existing: TagEntity?) : Editing
    data class Open(val existing: TagEntity?) : Editing
    data class Phone(val existing: TagEntity?) : Editing
}

/**
 * Magic Actions and the other actions that are not a smart-home device.
 *
 * Everything created here is an ordinary TapRelay item, so the moment it is saved it can be bound
 * to a controller button, written to an NFC tag, fired by a place, put on the always-on face or
 * dropped on a home-screen widget. There is nothing here that a trigger has to learn about.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionsScreen(vm: TapRelayViewModel, onDone: () -> Unit) {
    val ui by vm.ui.collectAsState()
    var editing by remember { mutableStateOf<Editing?>(null) }
    var showNewSheet by rememberSaveable { mutableStateOf(false) }

    // Keeps the reading position when an editor closes, rather than snapping back to the top.
    val listState = rememberLazyListState()

    BackHandler {
        when {
            editing != null -> editing = null
            showNewSheet -> showNewSheet = false
            else -> onDone()
        }
    }

    when (val target = editing) {
        is Editing.Magic -> {
            MagicActionEditor(
                vm = vm,
                existing = target.existing,
                onDone = { editing = null }
            )
            return
        }
        is Editing.Web -> {
            WebRequestEditor(vm = vm, existing = target.existing, onDone = { editing = null })
            return
        }
        is Editing.Open -> {
            OpenEditor(vm = vm, existing = target.existing, onDone = { editing = null })
            return
        }
        is Editing.Phone -> {
            PhoneEditor(vm = vm, existing = target.existing, onDone = { editing = null })
            return
        }
        null -> Unit
    }

    val magic = ui.tags.filter { it.isMagicAction }
    val others = ui.tags.filter { it.isWebhook || it.isLaunch || it.isPhone }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Actions") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showNewSheet = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New action") }
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Text(
                    "A Magic Action runs several of your actions in order from one trigger. " +
                        "Point an NFC tag, a controller button or the always-on face at it and " +
                        "the whole sequence happens.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            if (magic.isEmpty() && others.isEmpty()) {
                item { EmptyActions() }
            }

            if (magic.isNotEmpty()) {
                item { SectionLabel("Magic Actions") }
                items(magic, key = { it.tagId }) { tag ->
                    ActionRow(
                        tag = tag,
                        supporting = magicSummary(tag, ui.tags),
                        onOpen = { editing = Editing.Magic(tag) },
                        onRun = { vm.testItem(tag) }
                    )
                }
            }

            if (others.isNotEmpty()) {
                item { SectionLabel("Single actions") }
                items(others, key = { it.tagId }) { tag ->
                    ActionRow(
                        tag = tag,
                        supporting = singleSummary(tag),
                        onOpen = {
                            editing = when {
                                tag.isWebhook -> Editing.Web(tag)
                                tag.isLaunch -> Editing.Open(tag)
                                else -> Editing.Phone(tag)
                            }
                        },
                        onRun = { vm.testItem(tag) }
                    )
                }
            }

            item { Spacer(Modifier.height(88.dp)) }
        }
    }

    if (showNewSheet) {
        ModalBottomSheet(onDismissRequest = { showNewSheet = false }) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
                Text(
                    "New action",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                NewActionRow(
                    icon = Icons.Default.AutoAwesome,
                    title = "Magic Action",
                    subtitle = "Several actions in order, from one trigger"
                ) {
                    showNewSheet = false
                    editing = Editing.Magic(null)
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                NewActionRow(
                    icon = Icons.Default.OpenInNew,
                    title = "Open an app or link",
                    subtitle = "Launch something on this phone"
                ) {
                    showNewSheet = false
                    editing = Editing.Open(null)
                }
                NewActionRow(
                    icon = Icons.Default.DoNotDisturbOn,
                    title = "Do Not Disturb",
                    subtitle = "Quieten the phone, alarms still ring"
                ) {
                    showNewSheet = false
                    editing = Editing.Phone(null)
                }
                NewActionRow(
                    icon = Icons.Default.Language,
                    title = "Web request",
                    subtitle = "Advanced · call an address you already have"
                ) {
                    showNewSheet = false
                    editing = Editing.Web(null)
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp, start = 4.dp)
    )
}

@Composable
private fun ActionRow(
    tag: TagEntity,
    supporting: String,
    onOpen: () -> Unit,
    onRun: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth()
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            leadingContent = {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        iconFor(tag.iconKey),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            },
            headlineContent = {
                Text(tag.friendlyName, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            supportingContent = {
                Text(supporting, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            trailingContent = {
                IconButton(onClick = onRun) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Run ${tag.friendlyName} now",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        )
    }
}

@Composable
private fun NewActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(subtitle) },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
    )
}

@Composable
private fun EmptyActions() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp)
        )
        Spacer(Modifier.height(14.dp))
        Text("No actions yet", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "Make a Magic Action to run your lights, air and logs together from one tap.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

/** "4 actions · Desk Lamp, Air, Bedside…" - what it does, not how it is stored. */
internal fun magicSummary(tag: TagEntity, all: List<TagEntity>): String {
    val steps = tag.magicSteps
    val actions = steps.filterNot { it.isWait }
    if (actions.isEmpty()) return "No steps yet"
    val names = actions.take(3).map { step ->
        all.firstOrNull { it.tagId == step.tagId }?.friendlyName
            ?: step.label.ifBlank { "Deleted action" }
    }
    val count = if (actions.size == 1) "1 action" else "${actions.size} actions"
    return "$count · " + names.joinToString(", ") + if (actions.size > 3) "…" else ""
}

internal fun singleSummary(tag: TagEntity): String = when {
    // The method matters here and only here: this is the screen where it was configured.
    tag.isWebhook -> "${tag.webhookMethod ?: "POST"} \u00b7 ${ItemLabels.summary(tag)}"
    else -> ItemLabels.summary(tag)
}
