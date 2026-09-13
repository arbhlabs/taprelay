package com.arbhlabs.taprelay.band

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Blinds
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Desk
import androidx.compose.material.icons.filled.DoorFront
import androidx.compose.material.icons.filled.EmojiObjects
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Fluorescent
import androidx.compose.material.icons.filled.Garage
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Hvac
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Light
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.ModeFanOff
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Outlet
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tungsten
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbIncandescent
import androidx.compose.material.icons.filled.WbIridescent
import androidx.compose.material.icons.filled.Weekend
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    "tune" to Icons.Default.Tune,
    "ceiling" to Icons.Default.Light, "tube" to Icons.Default.Fluorescent, "spot" to Icons.Default.Highlight,
    "glow" to Icons.Default.WbIridescent, "tungsten" to Icons.Default.Tungsten, "night" to Icons.Default.Nightlight,
    "bulb" to Icons.Default.WbIncandescent, "vent" to Icons.Default.Hvac, "desk" to Icons.Default.Desk,
    "blinds" to Icons.Default.Blinds, "ac" to Icons.Default.AcUnit, "humid" to Icons.Default.WaterDrop,
    "speaker" to Icons.Default.Speaker, "monitor" to Icons.Default.Monitor, "coffee" to Icons.Default.Coffee,
    "outlet" to Icons.Default.Outlet, "power" to Icons.Default.PowerSettingsNew, "garage" to Icons.Default.Garage,
    "plant" to Icons.Default.LocalFlorist, "voldown" to Icons.AutoMirrored.Filled.VolumeDown,
    "mute" to Icons.AutoMirrored.Filled.VolumeMute, "next" to Icons.Default.SkipNext, "prev" to Icons.Default.SkipPrevious,
    "bed" to Icons.Default.Bed, "kitchen" to Icons.Default.Kitchen
)

private fun bandIcon(key: String) = BAND_ICONS[key] ?: Icons.Default.Bolt

private val TILE_OFF = Color(0xFF2A3139)
private val INK_ON = Color(0xFF0B1512)
private val INK_OFF = Color(0xFFE6EDF2)

/** Chooses which TapRelay controls the Xiaomi band shows, their order, each tile's icon, and how the band looks. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BandControlsScreen(onDone: () -> Unit) {
    BackHandler { onDone() }
    var raw by remember { mutableStateOf<List<BandBridge.Control>>(emptyList()) }
    var order by remember { mutableStateOf<List<String>>(emptyList()) }
    var picking by remember { mutableStateOf<BandBridge.Control?>(null) }
    LaunchedEffect(Unit) {
        raw = BandBridge.catalogue()
        order = BandBridge.order(raw)
    }
    // The same automatic icons the band gets, so what you see here is what the wrist shows.
    val all = remember(raw, order) { BandBridge.withDistinctIcons(raw, order) }
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
                            raw = raw.map { if (it.id == control.id) it.copy(icon = key) else it }
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
            item { LookSection(chosen) }
            item {
                Text("Tiles", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                Text(
                    "In this order on the band. Icons are picked from each name; tap one to choose your own.",
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

/** Band look: layout, accent, heart-rate size and behaviour, with a live miniature of the wrist. Applied within a second. */
@Composable
private fun LookSection(chosen: List<BandBridge.Control>) {
    val look = BandBridge.Look
    var layout by remember { mutableStateOf(BandBridge.look(look.LAYOUT, "grid")) }
    var accent by remember { mutableStateOf(BandBridge.look(look.ACCENT, "teal")) }
    var hr by remember { mutableStateOf(BandBridge.look(look.HR, "large")) }
    var labels by remember { mutableStateOf(BandBridge.lookFlag(look.LABELS)) }
    var awake by remember { mutableStateOf(BandBridge.lookFlag(look.AWAKE)) }
    var haptics by remember { mutableStateOf(BandBridge.lookFlag(look.HAPTICS)) }
    val accentColor = Color(look.ACCENTS[accent] ?: 0xFF5BE7D6)

    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text("Band look", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BandPreview(chosen, layout, accentColor, hr, labels)
            Column(Modifier.weight(1f)) {
                Text("Layout", style = MaterialTheme.typography.labelLarge)
                ChipRow(listOf("grid" to "Grid", "compact" to "Compact", "list" to "List"), layout) {
                    layout = it; BandBridge.setLook(look.LAYOUT, it)
                }
                Text("Heart rate", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                ChipRow(listOf("large" to "Large", "small" to "Small", "hidden" to "Off"), hr) {
                    hr = it; BandBridge.setLook(look.HR, it)
                }
                Text("Accent", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
                look.ACCENTS.entries.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                        row.forEach { (key, argb) ->
                            Box(
                                Modifier.size(30.dp).clip(CircleShape).background(Color(argb))
                                    .then(if (key == accent) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                    .clickable { accent = key; BandBridge.setLook(look.ACCENT, key) }
                            )
                        }
                    }
                }
            }
        }
        SwitchRow("Names on tiles", labels) { labels = it; BandBridge.setLookFlag(look.LABELS, it) }
        SwitchRow("Keep band screen on while open", awake) { awake = it; BandBridge.setLookFlag(look.AWAKE, it) }
        SwitchRow("Buzz when a tap is done", haptics) { haptics = it; BandBridge.setLookFlag(look.HAPTICS, it) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChipRow(options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (key, label) -> FilterChip(selected = key == selected, onClick = { onPick(key) }, label = { Text(label) }) }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** A miniature Band 9: the same header, layout, colours and icons as the wrist, first few tiles only. */
@Composable
private fun BandPreview(controls: List<BandBridge.Control>, layout: String, accent: Color, hr: String, labels: Boolean) {
    Column(
        Modifier.width(100.dp).height(230.dp).clip(RoundedCornerShape(44.dp)).background(Color.Black)
            .padding(horizontal = 9.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(24.dp)) {
            Box(Modifier.size(4.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(4.dp))
            if (hr != "hidden") {
                Icon(Icons.Default.Favorite, null, tint = Color(0xFFFF4858), modifier = Modifier.size(if (hr == "large") 14.dp else 10.dp))
                Spacer(Modifier.width(2.dp))
                Text("72", color = Color.White, fontSize = if (hr == "large") 18.sp else 12.sp, fontWeight = FontWeight.Bold)
            } else {
                Text("TapRelay", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        fun on(c: BandBridge.Control) = c.toggle && c.on == true
        when (layout) {
            "list" -> controls.take(5).forEach { c ->
                Row(
                    Modifier.fillMaxWidth().height(26.dp).clip(RoundedCornerShape(9.dp)).background(if (on(c)) accent else TILE_OFF)
                        .padding(horizontal = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(bandIcon(c.icon), null, tint = if (on(c)) INK_ON else INK_OFF, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(c.label, fontSize = 8.sp, color = if (on(c)) INK_ON else INK_OFF, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            else -> {
                val cols = if (layout == "compact") 3 else 2
                controls.take(cols * 4).chunked(cols).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { c ->
                            Column(
                                Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(if (cols == 3) 7.dp else 11.dp))
                                    .background(if (on(c)) accent else TILE_OFF),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                val showLabel = cols == 2 && labels
                                Icon(
                                    bandIcon(c.icon), null, tint = if (on(c)) INK_ON else INK_OFF,
                                    modifier = Modifier.size(if (cols == 3) 13.dp else if (showLabel) 15.dp else 20.dp)
                                )
                                if (showLabel) {
                                    Text(c.label, fontSize = 6.sp, color = if (on(c)) INK_ON else INK_OFF, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}
