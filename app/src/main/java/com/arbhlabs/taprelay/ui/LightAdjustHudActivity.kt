package com.arbhlabs.taprelay.ui

import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arbhlabs.taprelay.TapRelayApplication
import com.arbhlabs.taprelay.controller.ControllerManager
import com.arbhlabs.taprelay.ui.theme.TapRelayTheme

/**
 * A small pill at the bottom of the screen that exists only for the 20-second light-adjustment
 * window when TapRelay is not already on screen.
 *
 * Android never gives an accessibility service the Xbox D-pad (it is a hat axis, not a key), so
 * outside TapRelay the D-pad went to whatever app was in front - or, in LastDose's always-on
 * screen, only the mapped directions reached TapRelay. Holding window focus for the window is
 * the one reliable way to receive it. Touches outside the pill still reach the app behind, and a
 * touch there hands the D-pad back early. It closes itself the moment the window ends.
 */
class LightAdjustHudActivity : ComponentActivity() {

    private lateinit var controllerManager: ControllerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controllerManager = (application as TapRelayApplication).services.controllerManager
        if (!controllerManager.automaticLightControlState.value.adjusting) {
            finish()
            return
        }
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        window.setGravity(Gravity.BOTTOM)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        )
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

        setContent {
            TapRelayTheme {
                val state by controllerManager.automaticLightControlState.collectAsState()
                LaunchedEffect(state.adjusting) { if (!state.adjusting) finish() }
                Pill(state.name, state.description)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        controllerManager.startListening()
    }

    override fun onStop() {
        controllerManager.stopListening()
        super.onStop()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        controllerManager.handleKeyEvent(event) || super.dispatchKeyEvent(event)

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        controllerManager.handleGenericMotionEvent(event) || super.dispatchGenericMotionEvent(event)

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // The owner reached for the phone: give the screen back rather than keep holding focus.
        if (event.action == MotionEvent.ACTION_OUTSIDE) {
            finish()
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }
}

@Composable
private fun Pill(name: String?, description: String?) {
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp
        ) {
            Text(
                listOfNotNull(name, description).joinToString(" • "),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}
