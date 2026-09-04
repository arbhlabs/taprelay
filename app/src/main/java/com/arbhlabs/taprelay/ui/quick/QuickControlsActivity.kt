package com.arbhlabs.taprelay.ui.quick

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.arbhlabs.taprelay.TapRelayApplication
import com.arbhlabs.taprelay.ui.theme.TapRelayTheme

/**
 * The Quick Controls surface for triggers that fire while TapRelay is not on screen — a tag
 * tapped from the home screen, for instance. It is an ordinary activity with a translucent
 * theme, so it needs no overlay or accessibility permission of any kind.
 */
class QuickControlsActivity : ComponentActivity() {

    private lateinit var session: QuickControlsSession

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val services = (application as TapRelayApplication).services
        session = QuickControlsSession(services, lifecycleScope)

        val tagId = intent.getStringExtra(EXTRA_TAG_ID)
        if (tagId.isNullOrBlank()) { finish(); return }
        session.open(tagId)

        setContent {
            TapRelayTheme {
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { visible = true }

                fun dismiss() {
                    visible = false
                    finish()
                    overridePendingTransition(0, android.R.anim.fade_out)
                }

                BoxWithConstraints(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { dismiss() },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    AnimatedVisibility(
                        visible = visible,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 3.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                // Taps inside the sheet must not fall through to the scrim.
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {}
                        ) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = maxHeight * 0.92f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Spacer(Modifier.height(10.dp))
                                Box(
                                    Modifier
                                        .size(width = 34.dp, height = 4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(MaterialTheme.colorScheme.outlineVariant)
                                )
                                Spacer(Modifier.height(8.dp))
                                QuickControlsContent(session, Modifier.padding(bottom = 8.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        session.close()
    }

    companion object {
        const val EXTRA_TAG_ID = "com.arbhlabs.taprelay.extra.TAG_ID"

        fun intent(context: Context, tagId: String): Intent =
            Intent(context, QuickControlsActivity::class.java)
                .putExtra(EXTRA_TAG_ID, tagId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
