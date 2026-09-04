package com.arbhlabs.taprelay.ui.quick

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arbhlabs.taprelay.domain.model.Brightness
import com.arbhlabs.taprelay.domain.model.ColorMath
import com.arbhlabs.taprelay.domain.model.DeviceKind
import com.arbhlabs.taprelay.domain.model.DeviceLabels
import com.arbhlabs.taprelay.domain.model.LightPresets
import com.arbhlabs.taprelay.domain.provider.SENSIBO_PROVIDER_ID
import com.arbhlabs.taprelay.domain.provider.TUYA_PROVIDER_ID
import com.arbhlabs.taprelay.ui.iconFor
import kotlin.math.roundToInt

/** The Quick Controls surface as a bottom sheet, for use inside the app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickControlsSheet(session: QuickControlsSession, onDismiss: () -> Unit) {
    val state by session.state.collectAsState()
    if (!state.open) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        QuickControlsContent(session)
    }
}

/**
 * Capability-driven controls for one item. Nothing here is drawn speculatively: a slider or a
 * fan level only appears once the provider has said the real device supports it.
 */
@Composable
fun QuickControlsContent(session: QuickControlsSession, modifier: Modifier = Modifier) {
    val state by session.state.collectAsState()
    val tag = state.tag
    val caps = state.capabilities
    val haptics = LocalHapticFeedback.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            // A landscape phone has very little height; the panel scrolls rather than clipping.
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(bottom = 12.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ---- Header: what this is, and what it is doing right now ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    iconFor(tag?.iconKey ?: "lamp"),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    tag?.friendlyName ?: "Quick Controls",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    subtitleFor(state),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (state.busy) {
                CircularProgressIndicator(
                    Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (state.loadingCapabilities) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Reading what this device can do…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (caps != null && caps.kind == DeviceKind.SCENE) {
            FilledTonalButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    session.runScene()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Run scene")
            }
        }

        // ---- Power ----
        if (caps == null || (caps.supportsPower && caps.kind != DeviceKind.SCENE)) {
            val isOn = state.snapshot.power == 1
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                PowerButton(
                    label = "Off",
                    selected = state.snapshot.power == 0,
                    modifier = Modifier.weight(1f)
                ) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    session.setPower(false)
                }
                PowerButton(
                    label = "On",
                    selected = isOn,
                    modifier = Modifier.weight(1f)
                ) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    session.setPower(true)
                }
            }
        }

        // ---- Fan speed: only the levels the unit itself reports ----
        if (caps != null && caps.supportsFanSpeed) {
            ControlLabel("Fan speed")
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                caps.fanLevels.forEach { level ->
                    FilterChip(
                        selected = state.snapshot.fanLevel.equals(level, ignoreCase = true),
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            session.setFanLevel(level)
                        },
                        label = { Text(DeviceLabels.fanLevel(level)) }
                    )
                }
            }
        }

        // ---- Operating mode: only when the unit has more than one ----
        if (caps != null && caps.supportsModes) {
            ControlLabel("Mode")
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                caps.modes.forEach { mode ->
                    FilterChip(
                        selected = state.snapshot.mode.equals(mode, ignoreCase = true),
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            session.setMode(mode)
                        },
                        label = { Text(DeviceLabels.mode(mode)) }
                    )
                }
            }
        }

        // ---- Brightness ----
        if (caps != null && caps.supportsBrightness) {
            ControlLabel("Brightness • ${state.brightnessPercent}%")
            Slider(
                value = state.brightnessPercent.toFloat(),
                onValueChange = { session.setBrightness(it.roundToInt()) },
                onValueChangeFinished = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    session.commitBrightness()
                },
                valueRange = Brightness.MIN_PERCENT.toFloat()..Brightness.MAX_PERCENT.toFloat()
            )
        }

        // ---- White temperature ----
        if (caps != null && caps.supportsColorTemperature) {
            ControlLabel("White • ${state.whiteKelvin}K")
            Slider(
                value = state.whiteKelvin.toFloat(),
                onValueChange = { session.setWhiteKelvin(it.roundToInt()) },
                onValueChangeFinished = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    session.commitWhiteKelvin()
                },
                valueRange = LightPresets.MIN_KELVIN.toFloat()..LightPresets.MAX_KELVIN.toFloat()
            )
        }

        // ---- Colour ----
        if (caps != null && caps.supportsColor) {
            ControlLabel("Colour")
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LightPresets.COLORS.forEach { preset ->
                    val selected = state.colorRgb == preset.rgb
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                Color(
                                    ColorMath.red(preset.rgb),
                                    ColorMath.green(preset.rgb),
                                    ColorMath.blue(preset.rgb)
                                )
                            )
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape
                            )
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                session.setColor(preset.rgb)
                            }
                    )
                }
            }
        }

        // ---- Outcome ----
        AnimatedVisibility(visible = state.error != null || state.status != null) {
            val error = state.error
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (error != null) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    error ?: state.status.orEmpty(),
                    Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (error != null) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun ControlLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun PowerButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    if (selected) {
        FilledTonalButton(onClick = onClick, modifier = modifier) {
            Icon(Icons.Default.PowerSettingsNew, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) {
            Icon(Icons.Default.PowerSettingsNew, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label)
        }
    }
}

private fun subtitleFor(state: QuickControlsUiState): String {
    val tag = state.tag ?: return "Loading…"
    val provider = when (tag.providerId) {
        TUYA_PROVIDER_ID -> "Smart Life"
        SENSIBO_PROVIDER_ID -> "Sensibo"
        else -> "Govee"
    }
    val parts = mutableListOf(provider)
    when (state.snapshot.power) {
        1 -> parts.add("On")
        0 -> parts.add("Off")
    }
    state.snapshot.fanLevel?.let { parts.add("Fan " + DeviceLabels.fanLevel(it)) }
    state.snapshot.mode?.takeIf { (state.capabilities?.modes?.size ?: 0) > 1 }
        ?.let { parts.add(DeviceLabels.mode(it)) }
    return parts.joinToString(" • ")
}
