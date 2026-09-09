package com.arbhlabs.taprelay.ui.remote

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arbhlabs.taprelay.data.local.entity.TagEntity
import com.arbhlabs.taprelay.domain.model.ItemLabels
import com.arbhlabs.taprelay.ui.iconFor
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * The pieces the always-on face is drawn from.
 *
 * Kept apart from the activity so the face is a set of small composables with no lifecycle,
 * window or controller knowledge in them - the activity owns all of that.
 *
 * Two rules run through every one of these:
 *  - **black space is the design.** Nothing gets a filled background it does not need, because
 *    every lit pixel on an OLED is power and, over hours propped up on a desk, wear.
 *  - **one clear thing to press.** A favourite is a target, not a card in a dashboard.
 */
object AodFormats {
    val TIME_24: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM")
}

/** How a tile responds to being touched, so a sleeve on the glass cannot run a routine. */
enum class TileState { IDLE, ARMED, RUNNING }

@Composable
fun AodClock(now: LocalDateTime, clockSp: Int, dim: Boolean) {
    Column {
        Text(
            now.format(AodFormats.TIME_24),
            fontSize = clockSp.sp,
            // Light weight is both the calmer type and, on an OLED, materially fewer lit pixels.
            fontWeight = FontWeight.Light,
            color = Color.White,
            maxLines = 1
        )
        if (!dim) {
            Text(
                now.format(AodFormats.DATE),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.42f),
                maxLines = 1
            )
        }
    }
}

/** The wordmark and, beside it, whatever the hardware is doing. One line, no chrome. */
@Composable
fun AodStatusLine(
    controllerName: String?,
    battery: Int?,
    connected: Boolean,
    accent: Color,
    automaticControls: String? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "TAPRELAY",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.4.sp,
            color = Color.White.copy(alpha = 0.38f)
        )
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (connected) accent else Color.White.copy(alpha = 0.22f))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            buildString {
                append(controllerName ?: "Ready")
                if (battery != null) append(" · $battery%")
            },
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.38f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
    if (connected && automaticControls != null) {
        Text(
            automaticControls,
            style = MaterialTheme.typography.labelSmall,
            color = accent.copy(alpha = 0.72f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(top = 5.dp)
        )
    }
    }
}

/**
 * The one favourite that gets the most room.
 *
 * Deliberately an outline rather than a filled slab: on black, a hairline border and a large label
 * read as a button without lighting a whole rectangle of the panel.
 */
@Composable
fun AodPrimaryTile(
    tag: TagEntity,
    subtitle: String,
    state: TileState,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val armed = state == TileState.ARMED
    val border by animateFloatAsState(if (armed) 1f else 0.16f, tween(160), label = "primaryBorder")

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(26.dp),
        color = if (armed) accent.copy(alpha = 0.12f) else Color.Transparent,
        contentColor = Color.White,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .border(1.dp, accent.copy(alpha = border), RoundedCornerShape(26.dp))
            .semantics {
                role = Role.Button
                contentDescription = if (armed) {
                    "${tag.friendlyName}. Tap again to run."
                } else {
                    "${tag.friendlyName}. $subtitle"
                }
            }
    ) {
        Row(
            Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                iconFor(tag.iconKey),
                contentDescription = null,
                tint = if (armed) accent else Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    tag.friendlyName.uppercase(),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.2.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    if (armed) "Tap again to run" else subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (armed) accent else Color.White.copy(alpha = 0.4f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (state == TileState.RUNNING) {
                AodSpinner(accent)
            }
        }
    }
}

/** A secondary favourite. Same language as the primary, half the height. */
@Composable
fun AodTile(
    tag: TagEntity,
    subtitle: String,
    state: TileState,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val armed = state == TileState.ARMED
    val border by animateFloatAsState(if (armed) 1f else 0.13f, tween(160), label = "tileBorder")

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (armed) accent.copy(alpha = 0.12f) else Color.Transparent,
        contentColor = Color.White,
        modifier = modifier
            // 64dp clears the 48dp minimum touch target with room to spare on a face that is
            // pressed without looking closely at it.
            .heightIn(min = 68.dp)
            .border(1.dp, accent.copy(alpha = border), RoundedCornerShape(20.dp))
            .semantics {
                role = Role.Button
                contentDescription = if (armed) {
                    "${tag.friendlyName}. Tap again to run."
                } else {
                    "${tag.friendlyName}. $subtitle"
                }
            }
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                iconFor(tag.iconKey),
                contentDescription = null,
                tint = if (armed) accent else Color.White.copy(alpha = 0.72f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    tag.friendlyName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (armed) "Tap again" else subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (armed) accent else Color.White.copy(alpha = 0.35f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (state == TileState.RUNNING) {
                AodSpinner(accent, size = 14)
            }
        }
    }
}

/**
 * What happened, for a moment, then gone.
 *
 * Replaces the tiles rather than sitting above them, so the face never has two things competing
 * for the eye and the result is unmissable at a glance from across a desk.
 */
@Composable
fun AodResult(
    success: Boolean,
    title: String,
    detail: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val tint = if (success) accent else Color(0xFFFFB4A9)
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            if (success) Icons.Default.Check else Icons.Default.PriorityHigh,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(34.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (detail.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Three dots rather than a spinning ring: no continuous animation to keep the panel awake. */
@Composable
private fun AodSpinner(accent: Color, size: Int = 18) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(3) { index ->
            val alpha by animateFloatAsState(
                targetValue = if (index == 1) 0.9f else 0.45f,
                animationSpec = tween(400),
                label = "dot$index"
            )
            Box(
                Modifier
                    .size((size / 4).dp.coerceAtLeast(4.dp))
                    .clip(CircleShape)
                    .background(accent.copy(alpha = alpha))
            )
        }
    }
}

@Composable
fun AodEmpty(onOpenSettings: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 26.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            "No favourites yet",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.75f)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Choose the actions you want on this face in TapRelay → Always-on face.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.38f)
        )
        Spacer(Modifier.height(18.dp))
        Surface(
            onClick = onOpenSettings,
            shape = RoundedCornerShape(18.dp),
            color = Color.Transparent,
            contentColor = Color.White,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(18.dp))
        ) {
            Text(
                "Choose favourites",
                Modifier.padding(horizontal = 20.dp, vertical = 13.dp),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

/** "4 actions", "Logs to LastDose", "Smart Life" - one short line under a favourite's name. */
fun aodSubtitle(tag: TagEntity): String = ItemLabels.summary(tag)
