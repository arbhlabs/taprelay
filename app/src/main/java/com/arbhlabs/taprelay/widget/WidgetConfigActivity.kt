package com.arbhlabs.taprelay.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arbhlabs.taprelay.TapRelayApplication
import com.arbhlabs.taprelay.data.local.entity.TagEntity
import com.arbhlabs.taprelay.ui.iconFor
import com.arbhlabs.taprelay.ui.theme.TapRelayTheme

/**
 * Choosing what a widget does, both when it is first dropped on the home screen and when a
 * configured one is tapped through to later.
 *
 * One activity for both widgets: the Action Key picks one item and finishes the moment it is
 * chosen, the Remote picks up to six and keeps their order. Anything TapRelay can run is offered,
 * because to a widget a Magic Action, a lamp and a LastDose log are the same thing.
 */
class WidgetConfigActivity : ComponentActivity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // Cancelled unless the owner finishes: the launcher removes a widget whose configuration
        // activity comes back without RESULT_OK, which is the behaviour we want on Back.
        setResult(Activity.RESULT_CANCELED, resultIntent())

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val services = (application as TapRelayApplication).services
        val store = WidgetStore(this)
        val isRemote = isRemoteWidget()
        val maxSlots = if (isRemote) WidgetRenderer.MAX_REMOTE_TILES else 1

        setContent {
            TapRelayTheme {
                var items by remember { mutableStateOf<List<TagEntity>?>(null) }
                val chosen = remember { store.targets(widgetId).toMutableStateList() }

                LaunchedEffect(Unit) {
                    items = services.tagRepository.getTagsOnce()
                }

                WidgetConfigScreen(
                    isRemote = isRemote,
                    maxSlots = maxSlots,
                    items = items,
                    chosen = chosen,
                    onToggle = { tag ->
                        when {
                            chosen.contains(tag.tagId) -> chosen.remove(tag.tagId)
                            maxSlots == 1 -> {
                                chosen.clear()
                                chosen.add(tag.tagId)
                                // One choice is the whole configuration; do not make them
                                // confirm what they just tapped.
                                commit(store, chosen.toList())
                            }
                            chosen.size < maxSlots -> chosen.add(tag.tagId)
                        }
                    },
                    onDone = { commit(store, chosen.toList()) },
                    onCancel = { finish() }
                )
            }
        }
    }

    private fun isRemoteWidget(): Boolean {
        val manager = AppWidgetManager.getInstance(this)
        val provider = manager.getAppWidgetInfo(widgetId)?.provider?.className
        return provider == RemoteWidget::class.java.name
    }

    private fun commit(store: WidgetStore, targets: List<String>) {
        store.setTargets(widgetId, targets)
        store.clearRunState(widgetId)
        TapRelayWidget.refreshOne(applicationContext, widgetId)
        setResult(Activity.RESULT_OK, resultIntent())
        finish()
    }

    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetConfigScreen(
    isRemote: Boolean,
    maxSlots: Int,
    items: List<TagEntity>?,
    chosen: List<String>,
    onToggle: (TagEntity) -> Unit,
    onDone: () -> Unit,
    onCancel: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(if (isRemote) "TapRelay Remote" else "Action Key") })
        },
        bottomBar = {
            if (isRemote) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Button(
                            onClick = onDone,
                            enabled = chosen.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Text(
                                when (chosen.size) {
                                    0 -> "Pick at least one"
                                    1 -> "Add with 1 action"
                                    else -> "Add with ${chosen.size} actions"
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        when (val list = items) {
            null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                // Never a spinner for a read this short: a flash of a spinner reads worse than
                // a beat of nothing.
                Text("", style = MaterialTheme.typography.bodyMedium)
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                item {
                    Text(
                        if (isRemote) {
                            "Choose up to $maxSlots. They appear in the order you pick them, and " +
                                "the widget shows as many as it has room for."
                        } else {
                            "Choose the one action this button runs."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                if (list.isEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(vertical = 40.dp)) {
                            Text("Nothing to put on it yet", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Set up a light, a Magic Action or a LastDose log in TapRelay " +
                                    "first, then add this widget.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(20.dp))
                            Button(onClick = onCancel) { Text("Close") }
                        }
                    }
                }

                items(list, key = { it.tagId }, contentType = { "item" }) { tag ->
                    val index = chosen.indexOf(tag.tagId)
                    val selected = index >= 0
                    ListItem(
                        modifier = Modifier.fillMaxWidth().clickable { onToggle(tag) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = {
                            Box(
                                Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected && isRemote) {
                                    Text(
                                        "${index + 1}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Icon(
                                        iconFor(tag.iconKey),
                                        contentDescription = null,
                                        tint = if (selected) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                        },
                        headlineContent = {
                            Text(tag.friendlyName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = { Text(WidgetRenderer.subtitleFor(tag)) },
                        trailingContent = {
                            if (isRemote) {
                                Checkbox(checked = selected, onCheckedChange = { onToggle(tag) })
                            } else {
                                RadioButton(selected = selected, onClick = { onToggle(tag) })
                            }
                        }
                    )
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}
