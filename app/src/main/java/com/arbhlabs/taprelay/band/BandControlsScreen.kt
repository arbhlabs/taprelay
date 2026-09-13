package com.arbhlabs.taprelay.band

import android.graphics.BitmapFactory
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

private val TILE_OFF = Color(0xFF2A3139)
private val INK_ON = Color(0xFF0B1512)
private val INK_OFF = Color(0xFFE6EDF2)

private val iconCache = ConcurrentHashMap<String, ImageBitmap>()

/** The same PNG the band draws (assets/band_icons, generated with the band app), tinted. */
@Composable
private fun BandIcon(key: String, modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current, description: String? = null) {
    val context = LocalContext.current
    val bitmap = remember(key) {
        iconCache[key] ?: runCatching {
            context.assets.open("band_icons/$key.png").use { BitmapFactory.decodeStream(it).asImageBitmap() }
        }.getOrNull()?.also { iconCache[key] = it }
    }
    if (bitmap != null) Icon(bitmap = bitmap, contentDescription = description, tint = tint, modifier = modifier)
    else Box(modifier)
}

/** Chooses which TapRelay controls the Xiaomi band shows, their order, each tile's icon, and how the band looks. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BandControlsScreen(onDone: () -> Unit) {
    BackHandler { onDone() }
    val scope = rememberCoroutineScope()
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
        var query by remember(control.id) { mutableStateOf("") }
        val keys = remember(query) {
            val q = query.trim().lowercase().replace(' ', '_')
            BandBridge.ICON_KEYS.filter { q.isEmpty() || it.contains(q) || BandIcons.category(it)?.startsWith(q) == true }
        }
        AlertDialog(
            onDismissRequest = { picking = null },
            title = { Text("Icon for ${control.label}") },
            text = {
                Column {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        placeholder = { Text("Search ${BandBridge.ICON_KEYS.size} icons, e.g. lamp, volume") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyVerticalGrid(columns = GridCells.Fixed(6), modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp)) {
                        items(keys) { key ->
                            IconButton(onClick = {
                                BandBridge.setIcon(control.id, key)
                                raw = raw.map { if (it.id == control.id) it.copy(icon = key) else it }
                                picking = null
                            }) {
                                BandIcon(
                                    key, description = key.replace('_', ' '),
                                    tint = if (key == control.icon) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = null }) { Text("Close") } },
            dismissButton = {
                TextButton(onClick = {
                    BandBridge.clearIcon(control.id)
                    picking = null
                    scope.launch { raw = BandBridge.catalogue() }
                }) { Text("Automatic") }
            }
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
                    "In this order on the band. Icons are picked from each name; tap one to choose your own. " +
                        "On the band, hold a tile for its own controls.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            itemsIndexed(chosen, key = { _, c -> "on-${c.id}" }) { i, c ->
                ListItem(
                    leadingContent = {
                        IconButton(onClick = { picking = c }) { BandIcon(c.icon, description = "Change icon") }
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
                    leadingContent = { BandIcon(c.icon) },
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
    var size by remember { mutableStateOf(BandBridge.look(look.SIZE, "large")) }
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
                Text("Size", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                ChipRow(listOf("standard" to "S", "large" to "L", "xl" to "XL"), size) {
                    size = it; BandBridge.setLook(look.SIZE, it)
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

/** A miniature Band 9: the same clock, header, layout, colours and icons as the wrist, first few tiles only. */
@Composable
private fun BandPreview(controls: List<BandBridge.Control>, layout: String, accent: Color, hr: String, labels: Boolean) {
    Column(
        Modifier.width(100.dp).height(240.dp).clip(RoundedCornerShape(44.dp)).background(Color.Black)
            .padding(horizontal = 9.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("21:42", color = INK_OFF, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(22.dp)) {
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
                    BandIcon(c.icon, tint = if (on(c)) INK_ON else INK_OFF, modifier = Modifier.size(12.dp))
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
                                BandIcon(
                                    c.icon, tint = if (on(c)) INK_ON else INK_OFF,
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
