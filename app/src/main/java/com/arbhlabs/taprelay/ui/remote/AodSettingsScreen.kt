package com.arbhlabs.taprelay.ui.remote

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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arbhlabs.taprelay.data.local.entity.TagEntity
import com.arbhlabs.taprelay.data.prefs.AodDensity
import com.arbhlabs.taprelay.ui.TapRelayViewModel
import com.arbhlabs.taprelay.ui.iconFor

/**
 * What the always-on face shows.
 *
 * Progressive disclosure on purpose: favourites and their order are the whole point and sit at the
 * top, the two choices that change how it feels come next, and the panel-level settings that most
 * people should never touch are behind Advanced. The list keeps its scroll position while things
 * are toggled, because every one of these settings is something you change *while looking at the
 * list you are changing*.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AodSettingsScreen(vm: TapRelayViewModel, onDone: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val favourites by vm.aodFavourites.collectAsState()
    val showClock by vm.aodShowClock.collectAsState()
    val confirmActions by vm.aodConfirmActions.collectAsState()
    val monochrome by vm.aodMonochrome.collectAsState()
    val density by vm.aodDensity.collectAsState()
    val autoDim by vm.remoteAutoDim.collectAsState()
    val context = LocalContext.current

    var showAdvanced by remember { mutableStateOf(false) }

    // A single hoisted state, so toggling a switch never scrolls the list back to the top.
    val listState = rememberLazyListState()

    BackHandler { onDone() }

    val chosen = favourites.mapNotNull { id -> ui.tags.firstOrNull { it.tagId == id } }
    val available = ui.tags.filterNot { tag -> favourites.contains(tag.tagId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Always-on face") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        context.startActivity(RemoteModeActivity.intent(context))
                    }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Open the always-on face")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                Text(
                    "Prop the phone up and press what you use most. The first favourite gets the " +
                        "big button; the rest sit underneath it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            item { Section("On the face") }

            if (chosen.isEmpty()) {
                item {
                    Text(
                        "Nothing chosen yet. Pick from your actions below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }

            items(chosen, key = { "fav-${it.tagId}" }) { tag ->
                val index = favourites.indexOf(tag.tagId)
                FavouriteRow(
                    tag = tag,
                    isPrimary = index == 0,
                    isFirst = index == 0,
                    isLast = index == favourites.lastIndex,
                    onUp = { vm.moveAodFavourite(tag.tagId, -1) },
                    onDown = { vm.moveAodFavourite(tag.tagId, 1) },
                    onRemove = { vm.toggleAodFavourite(tag.tagId) }
                )
            }

            item { Section("Add to the face") }

            if (available.isEmpty()) {
                item {
                    Text(
                        "Everything you have is already on the face.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }

            items(available, key = { "add-${it.tagId}" }) { tag ->
                ListItem(
                    modifier = Modifier.fillMaxWidth().clickable { vm.toggleAodFavourite(tag.tagId) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = {
                        Icon(
                            iconFor(tag.iconKey),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    headlineContent = {
                        Text(tag.friendlyName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = { Text(aodSubtitle(tag)) },
                    trailingContent = {
                        Checkbox(checked = false, onCheckedChange = { vm.toggleAodFavourite(tag.tagId) })
                    }
                )
            }

            item { Section("How it behaves") }

            item {
                SettingSwitch(
                    title = "Ask before running",
                    subtitle = "One tap arms a button, the second runs it. Stops a sleeve on the " +
                        "glass turning the lights off.",
                    checked = confirmActions,
                    onChange = { vm.setAodConfirmActions(it) }
                )
            }
            item {
                SettingSwitch(
                    title = "Show the clock",
                    subtitle = "Time and date at the top of the face.",
                    checked = showClock,
                    onChange = { vm.setAodShowClock(it) }
                )
            }
            item {
                SettingSwitch(
                    title = "Pure white on black",
                    subtitle = "Drops the accent colour. Dimmer, and easier on a dark room.",
                    checked = monochrome,
                    onChange = { vm.setAodMonochrome(it) }
                )
            }

            item {
                Surface(
                    onClick = { showAdvanced = !showAdvanced },
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (showAdvanced) "Hide advanced" else "Advanced",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 16.dp, horizontal = 4.dp)
                    )
                }
            }

            if (showAdvanced) {
                item {
                    SettingSwitch(
                        title = "Dim when left alone",
                        subtitle = "After a while the face fades to the panel's dimmest setting " +
                            "and slows the display down. Slide to bring it back.",
                        checked = autoDim,
                        onChange = { vm.setRemoteAutoDim(it) }
                    )
                }
                item {
                    Column(Modifier.padding(vertical = 8.dp, horizontal = 4.dp)) {
                        Text("Size", fontWeight = FontWeight.Medium)
                        Text(
                            "How large the clock and the buttons are.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AodDensity.entries.forEach { option ->
                                FilterChip(
                                    selected = density == option,
                                    onClick = { vm.setAodDensity(option) },
                                    label = { Text(option.label) }
                                )
                            }
                        }
                    }
                }
                item {
                    Text(
                        "The face keeps the screen on, so leave it plugged in for long sessions. " +
                            "It drifts a few pixels every minute so nothing burns into the panel.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                    )
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun Section(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp, start = 4.dp)
    )
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun FavouriteRow(
    tag: TagEntity,
    isPrimary: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(
                        if (isPrimary) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    iconFor(tag.iconKey),
                    contentDescription = null,
                    tint = if (isPrimary) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    tag.friendlyName,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (isPrimary) "Big button · " + aodSubtitle(tag) else aodSubtitle(tag),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onUp, enabled = !isFirst) {
                Icon(Icons.Default.ArrowUpward, contentDescription = "Move up", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDown, enabled = !isLast) {
                Icon(Icons.Default.ArrowDownward, contentDescription = "Move down", modifier = Modifier.size(18.dp))
            }
            Checkbox(checked = true, onCheckedChange = { onRemove() })
        }
    }
}
