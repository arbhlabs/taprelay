package com.arbhlabs.taprelay.band

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DoorFront
import androidx.compose.material.icons.filled.EmojiObjects
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.ModeFanOff
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Weekend
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Phone-side previews of the band's bundled tile icons (same keys as [BandBridge.ICON_KEYS]). */
private val BAND_ICONS: Map<String, ImageVector> = mapOf(
    "bolt" to Icons.Default.Bolt, "light" to Icons.Default.Lightbulb, "lamp" to Icons.Default.EmojiObjects,
    "room" to Icons.Default.Weekend, "plug" to Icons.Default.Power, "scene" to Icons.Default.AutoAwesome,
    "air" to Icons.Default.Air, "fan" to Icons.Default.ModeFanOff, "heat" to Icons.Default.Thermostat,
    "tv" to Icons.Default.Tv, "pc" to Icons.Default.Computer, "media" to Icons.Default.PlayArrow,
    "music" to Icons.Default.MusicNote, "volume" to Icons.AutoMirrored.Filled.VolumeUp, "moon" to Icons.Default.Bedtime,
    "sun" to Icons.Default.LightMode, "door" to Icons.Default.DoorFront, "lock" to Icons.Default.Lock,
    "remote" to Icons.Default.SettingsRemote, "heart" to Icons.Default.Favorite, "repeat" to Icons.Default.Replay,
    "aod" to Icons.Default.Visibility, "haptic" to Icons.Default.Vibration, "game" to Icons.Default.SportsEsports,
    "pill" to Icons.Default.Medication, "phone" to Icons.Default.Smartphone, "bell" to Icons.Default.Notifications,
    "tune" to Icons.Default.Tune
)

private fun bandIcon(key: String) = BAND_ICONS[key] ?: Icons.Default.Bolt

/** Chooses which TapRelay controls the Xiaomi band shows, their order, and each tile's icon. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BandControlsScreen(onDone: () -> Unit) {
    BackHandler { onDone() }
    var all by remember { mutableStateOf<List<BandBridge.Control>>(emptyList()) }
    var order by remember { mutableStateOf<List<String>>(emptyList()) }
    var picking by remember { mutableStateOf<BandBridge.Control?>(null) }
    LaunchedEffect(Unit) {
        all = BandBridge.catalogue()
        order = BandBridge.order(all)
    }
    fun save(ids: List<String>) {
        order = ids
        BandBridge.saveOrder(ids)
    }
    fun move(i: Int, by: Int) {
        val j = i + by
        if (j !in order.indices) return
        save(order.toMutableList().also { val t = it[i]; it[i] = it[j]; it[j] = t })
    }

    picking?.let { control ->
        AlertDialog(
            onDismissRequest = { picking = null },
            title = { Text("Icon for ${control.label}") },
            text = {
                LazyVerticalGrid(columns = GridCells.Fixed(5), modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    items(BandBridge.ICON_KEYS) { key ->
                        IconButton(onClick = {
                            BandBridge.setIcon(control.id, key)
                            all = all.map { if (it.id == control.id) it.copy(icon = key) else it }
                            picking = null
                        }) {
                            Icon(
                                bandIcon(key), contentDescription = key,
                                tint = if (key == control.icon) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = null }) { Text("Close") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Band 9 controls") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        val chosen = order.mapNotNull { id -> all.firstOrNull { it.id == id } }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item {
                Text(
                    "Tiles on your Xiaomi Band, two per row, in this order. Tap an icon to change it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            itemsIndexed(chosen, key = { _, c -> "on-${c.id}" }) { i, c ->
                ListItem(
                    leadingContent = {
                        IconButton(onClick = { picking = c }) { Icon(bandIcon(c.icon), contentDescription = "Change icon") }
                    },
                    headlineContent = { Text(c.label) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { move(i, -1) }, enabled = i > 0) { Icon(Icons.Default.ArrowUpward, "Move up") }
                            IconButton(onClick = { move(i, 1) }, enabled = i < chosen.lastIndex) { Icon(Icons.Default.ArrowDownward, "Move down") }
                            IconButton(onClick = { save(order - c.id) }) { Icon(Icons.Default.Close, "Remove") }
                        }
                    }
                )
            }
            item { Text("Add", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)) }
            items(all.filter { it.id !in order }, key = { "off-${it.id}" }) { c ->
                ListItem(
                    leadingContent = { Icon(bandIcon(c.icon), contentDescription = null) },
                    headlineContent = { Text(c.label) },
                    trailingContent = { IconButton(onClick = { save(order + c.id) }) { Icon(Icons.Default.Add, "Add") } }
                )
            }
        }
    }
}
