package com.arbhlabs.taprelay.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arbhlabs.taprelay.execution.TapFeedback

/**
 * The one message TapRelay shows about a trigger.
 *
 * It sits *below* the app bar rather than over it: the pill appears while the owner is looking at
 * a screen they are still using, and covering that screen's own title with a floating message is
 * the difference between an app telling you something and an app interrupting you.
 *
 * A Magic Action also carries how far through it is, because a five-step sequence takes a few
 * seconds and "Bedtime • Vent Lamp…" alone does not say whether that is step two or step five.
 */
@Composable
fun TapRelayPill(
    feedback: TapFeedback?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** Height of the app bar this pill has to clear. Zero on a screen that has none. */
    topBarHeight: Dp = 64.dp
) {
    LaunchedEffect(feedback) {
        if (feedback != null && !feedback.pending) {
            kotlinx.coroutines.delay(2200)
            onDismiss()
        }
    }

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = feedback != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut()
        ) {
            val fb = feedback ?: return@AnimatedVisibility
            val bg = if (fb.isError) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh
            val fg = if (fb.isError) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onSurface

            val progress = fb.progress
            val fraction by animateFloatAsState(
                targetValue = if (progress == null || progress.total == 0) {
                    0f
                } else {
                    progress.completed.toFloat() / progress.total
                },
                animationSpec = tween(220),
                label = "magicProgress"
            )

            Column(
                modifier = Modifier
                    .padding(
                        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
                            topBarHeight + 8.dp,
                        start = 16.dp,
                        end = 16.dp
                    )
                    .clip(RoundedCornerShape(22.dp))
                    .background(bg)
                    .widthIn(max = 360.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!fb.pending) {
                        Icon(
                            if (fb.isError) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (fb.isError) fg else MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        fb.title,
                        color = fg,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (progress != null && fb.pending) {
                        Text(
                            "${progress.completed}/${progress.total}",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                // A thin line across the bottom of the pill, only while a sequence is mid-flight.
                if (progress != null && fb.pending) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent,
                        drawStopIndicator = {}
                    )
                }
            }
        }
    }
}
