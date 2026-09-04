package com.arbhlabs.taprelay.ui.places

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.arbhlabs.taprelay.data.local.entity.PlaceTransition
import com.arbhlabs.taprelay.data.local.entity.TagEntity
import com.arbhlabs.taprelay.ui.TapRelayViewModel
import com.arbhlabs.taprelay.ui.iconFor
import kotlin.math.roundToInt

/**
 * Places: the same items TapRelay already drives, fired by arriving somewhere or leaving it.
 *
 * No map and no API key — a place is set from where the phone is standing, which is how these
 * get created in practice ("I'm at home, call this Home").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacesScreen(vm: TapRelayViewModel, onDone: () -> Unit) {
    val context = LocalContext.current
    val ui by vm.ui.collectAsState()
    val places by vm.placeTriggers.collectAsState()
    val permission by vm.locationPermission.collectAsState()

    var showAdd by remember { mutableStateOf(false) }
    // Android 11+ usually answers the background request by silently denying it, so Settings is
    // offered only after the in-app prompt has actually been tried once.
    var askedForBackground by remember { mutableStateOf(false) }

    val foregroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { vm.refreshLocationPermission() }

    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        askedForBackground = true
        vm.refreshLocationPermission()
    }

    LaunchedEffect(Unit) { vm.refreshLocationPermission() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Places & Routines") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            // Permission is a two-step story on modern Android, so it is told in two steps.
            if (!permission.foreground) {
                item {
                    PermissionCard(
                        title = "Let TapRelay use your location",
                        body = "A place needs your location to know when you have arrived or left. " +
                            "Nothing is uploaded anywhere — the circle stays on this phone.",
                        action = "Allow location"
                    ) {
                        foregroundLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                }
            } else if (!permission.background) {
                item {
                    PermissionCard(
                        title = "Set location to \"Allow all the time\"",
                        body = if (askedForBackground) {
                            "Android only lets a geofence fire in the background with all-the-time " +
                                "location, and it can only be granted from Settings: open Permissions " +
                                "\u2192 Location \u2192 Allow all the time. Your places are saved either " +
                                "way, they just stay asleep until then."
                        } else {
                            "Places fire while TapRelay is closed, and Android only allows that with " +
                                "all-the-time location. Without it your places are saved but stay asleep."
                        },
                        action = if (askedForBackground) "Open settings" else "Allow all the time"
                    ) {
                        if (askedForBackground) {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null)
                                )
                            )
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        } else {
                            askedForBackground = true
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Places (${places.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    FilledTonalButton(
                        onClick = { showAdd = true },
                        enabled = permission.foreground && ui.tags.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add place")
                    }
                }
            }

            if (ui.tags.isEmpty()) {
                item {
                    Text(
                        "Add at least one item first — a place fires an item you have already set up.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (places.isEmpty()) {
                item {
                    ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "No places yet",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Stand where you want the circle centred — home, the office, the garage " +
                                    "— add a place, and pick what should happen when you arrive or leave.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(places, key = { it.id }) { place ->
                    val arriveTag = ui.tags.firstOrNull { it.tagId == place.tagId }
                    val leaveTag = ui.tags.firstOrNull { it.tagId == place.leaveTagId }
                    ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Place,
                                contentDescription = null,
                                tint = if (place.enabled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    place.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    buildString {
                                        append(place.transition.label)
                                        append(" • ")
                                        append(arriveTag?.friendlyName ?: "item")
                                        if (place.transition == PlaceTransition.BOTH && leaveTag != null) {
                                            append(" / ")
                                            append(leaveTag.friendlyName)
                                        }
                                        append(" • ${place.radiusMeters.roundToInt()} m")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = place.enabled,
                                onCheckedChange = { vm.setPlaceEnabled(place, it) }
                            )
                            IconButton(onClick = { vm.deletePlace(place) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete place",
                                    tint = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "A place always runs its item's action. Android does not allow an app to open " +
                        "a screen from the background, so Quick Controls is not offered here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    if (showAdd) {
        AddPlaceDialog(
            vm = vm,
            tags = ui.tags,
            onDismiss = { showAdd = false; vm.clearPickedLocation() }
        )
    }
}

@Composable
private fun PermissionCard(
    title: String,
    body: String,
    action: String,
    onClick: () -> Unit
) {
    ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onClick, Modifier.fillMaxWidth()) { Text(action) }
        }
    }
}

@Composable
private fun AddPlaceDialog(
    vm: TapRelayViewModel,
    tags: List<TagEntity>,
    onDismiss: () -> Unit
) {
    val picked by vm.pickedLocation.collectAsState()
    val locating by vm.locating.collectAsState()

    var name by remember { mutableStateOf("Home") }
    var radius by remember { mutableStateOf(150f) }
    var transition by remember { mutableStateOf(PlaceTransition.ARRIVE) }
    var arriveTag by remember { mutableStateOf<TagEntity?>(null) }
    var leaveTag by remember { mutableStateOf<TagEntity?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a place") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedButton(
                    onClick = { vm.useCurrentLocation() },
                    enabled = !locating,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (locating) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Finding you…")
                    } else {
                        Icon(Icons.Default.MyLocation, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (picked == null) "Use my current location" else "Update to here")
                    }
                }

                picked?.let { (lat, lng) ->
                    Text(
                        "Centred on %.5f, %.5f".format(lat, lng),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text("Radius • ${radius.roundToInt()} m", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = radius,
                    onValueChange = { radius = it },
                    // Android geofences are unreliable below ~100 m, so the floor is honest.
                    valueRange = 100f..1000f
                )

                Text("When", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlaceTransition.entries.forEach { option ->
                        FilterChip(
                            selected = transition == option,
                            onClick = { transition = option },
                            label = {
                                Text(
                                    when (option) {
                                        PlaceTransition.ARRIVE -> "Arrive"
                                        PlaceTransition.LEAVE -> "Leave"
                                        PlaceTransition.BOTH -> "Both"
                                    }
                                )
                            }
                        )
                    }
                }

                TagPicker(
                    label = if (transition == PlaceTransition.LEAVE) "Run when leaving"
                    else "Run when arriving",
                    tags = tags,
                    selected = arriveTag,
                    onSelect = { arriveTag = it }
                )

                if (transition == PlaceTransition.BOTH) {
                    TagPicker(
                        label = "Run when leaving",
                        tags = tags,
                        selected = leaveTag,
                        onSelect = { leaveTag = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = picked != null && arriveTag != null &&
                    (transition != PlaceTransition.BOTH || leaveTag != null),
                onClick = {
                    val (lat, lng) = picked ?: return@Button
                    vm.savePlace(
                        name = name,
                        latitude = lat,
                        longitude = lng,
                        radiusMeters = radius,
                        transition = transition,
                        tagId = arriveTag!!.tagId,
                        leaveTagId = if (transition == PlaceTransition.BOTH) leaveTag?.tagId else null
                    )
                    onDismiss()
                }
            ) { Text("Save place") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun TagPicker(
    label: String,
    tags: List<TagEntity>,
    selected: TagEntity?,
    onSelect: (TagEntity) -> Unit
) {
    Text(label, style = MaterialTheme.typography.labelLarge)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        tags.forEach { tag ->
            val isSelected = selected?.tagId == tag.tagId
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().clickable { onSelect(tag) }
            ) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        iconFor(tag.iconKey),
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        tag.friendlyName,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
