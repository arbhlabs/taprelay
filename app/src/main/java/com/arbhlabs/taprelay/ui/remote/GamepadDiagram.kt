package com.arbhlabs.taprelay.ui.remote

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.arbhlabs.taprelay.controller.model.ControllerKeys

/**
 * A stylised pad drawn from the mapping table, not from a picture: whichever controls are
 * actually mapped are ringed in the accent colour, and the control just pressed flashes.
 *
 * Drawn rather than shipped as an asset so it scales cleanly and stays legible at the very low
 * brightness Remote Mode runs at.
 */
@Composable
fun GamepadDiagram(
    mappedKeys: Set<String>,
    pressedKey: String?,
    accent: Color,
    idle: Color,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val w = maxWidth
        Canvas(Modifier.fillMaxWidth().height(w * 0.44f)) {
            drawPad(mappedKeys, pressedKey, accent, idle)
        }
    }
}

private fun DrawScope.drawPad(
    mapped: Set<String>,
    pressed: String?,
    accent: Color,
    idle: Color
) {
    val w = size.width
    val h = size.height
    val unit = w / 100f

    fun colorFor(key: String): Color = when {
        pressed == key || pressed?.endsWith("+$key") == true -> accent
        mapped.any { it == key || it.endsWith("+$key") } -> accent.copy(alpha = 0.55f)
        else -> idle
    }

    fun filled(key: String): Boolean =
        pressed == key || pressed?.endsWith("+$key") == true

    // Body: two grips joined by a slab, drawn as one soft outline.
    val bodyStroke = Stroke(width = unit * 0.9f)
    drawRoundRectOutline(
        left = unit * 7f, top = h * 0.22f, right = w - unit * 7f, bottom = h * 0.88f,
        radius = unit * 16f, color = idle.copy(alpha = 0.45f), stroke = bodyStroke
    )

    val centerY = h * 0.52f

    // Bumpers
    listOf(
        ControllerKeys.BUTTON_L1 to Offset(unit * 24f, h * 0.17f),
        ControllerKeys.BUTTON_R1 to Offset(w - unit * 24f, h * 0.17f)
    ).forEach { (key, at) ->
        val c = colorFor(key)
        drawRoundRectOutline(
            left = at.x - unit * 9f, top = at.y - unit * 3.2f,
            right = at.x + unit * 9f, bottom = at.y + unit * 3.2f,
            radius = unit * 3.2f, color = c,
            stroke = Stroke(width = unit * 0.9f), fill = filled(key)
        )
    }

    // Triggers, sitting just above the bumpers.
    listOf(
        ControllerKeys.BUTTON_L2 to Offset(unit * 24f, h * 0.06f),
        ControllerKeys.BUTTON_R2 to Offset(w - unit * 24f, h * 0.06f)
    ).forEach { (key, at) ->
        val c = colorFor(key)
        drawRoundRectOutline(
            left = at.x - unit * 6.5f, top = at.y - unit * 2.6f,
            right = at.x + unit * 6.5f, bottom = at.y + unit * 2.6f,
            radius = unit * 2.6f, color = c,
            stroke = Stroke(width = unit * 0.8f), fill = filled(key)
        )
    }

    // D-pad, as four separate arms so each direction lights on its own.
    val dpad = Offset(unit * 24f, centerY)
    val arm = unit * 5.2f
    val thick = unit * 3.6f
    listOf(
        ControllerKeys.DPAD_UP to Offset(0f, -arm),
        ControllerKeys.DPAD_DOWN to Offset(0f, arm),
        ControllerKeys.DPAD_LEFT to Offset(-arm, 0f),
        ControllerKeys.DPAD_RIGHT to Offset(arm, 0f)
    ).forEach { (key, delta) ->
        val c = colorFor(key)
        val cx = dpad.x + delta.x
        val cy = dpad.y + delta.y
        val halfW = if (delta.x == 0f) thick / 2f else arm * 0.62f
        val halfH = if (delta.x == 0f) arm * 0.62f else thick / 2f
        drawRoundRectOutline(
            left = cx - halfW, top = cy - halfH, right = cx + halfW, bottom = cy + halfH,
            radius = unit * 1.1f, color = c,
            stroke = Stroke(width = unit * 0.8f), fill = filled(key)
        )
    }

    // Face buttons in the Xbox diamond.
    val face = Offset(w - unit * 24f, centerY)
    val spread = unit * 8.6f
    val r = unit * 3.5f
    listOf(
        ControllerKeys.BUTTON_Y to Offset(0f, -spread),
        ControllerKeys.BUTTON_A to Offset(0f, spread),
        ControllerKeys.BUTTON_X to Offset(-spread, 0f),
        ControllerKeys.BUTTON_B to Offset(spread, 0f)
    ).forEach { (key, delta) ->
        val c = colorFor(key)
        val at = Offset(face.x + delta.x, face.y + delta.y)
        if (filled(key)) drawCircle(color = c, radius = r, center = at)
        else drawCircle(color = c, radius = r, center = at, style = Stroke(width = unit * 0.9f))
    }

    // Sticks.
    listOf(
        ControllerKeys.BUTTON_THUMBL to Offset(unit * 40f, h * 0.72f),
        ControllerKeys.BUTTON_THUMBR to Offset(w - unit * 40f, h * 0.72f)
    ).forEach { (key, at) ->
        val c = colorFor(key)
        if (filled(key)) drawCircle(color = c, radius = unit * 5.2f, center = at)
        else drawCircle(color = c, radius = unit * 5.2f, center = at, style = Stroke(width = unit * 0.9f))
    }

    // View / Menu.
    listOf(
        ControllerKeys.BUTTON_SELECT to Offset(w / 2f - unit * 7f, centerY - unit * 3f),
        ControllerKeys.BUTTON_START to Offset(w / 2f + unit * 7f, centerY - unit * 3f)
    ).forEach { (key, at) ->
        val c = colorFor(key)
        if (filled(key)) drawCircle(color = c, radius = unit * 2.1f, center = at)
        else drawCircle(color = c, radius = unit * 2.1f, center = at, style = Stroke(width = unit * 0.7f))
    }
}

private fun DrawScope.drawRoundRectOutline(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    radius: Float,
    color: Color,
    stroke: Stroke,
    fill: Boolean = false
) {
    val topLeft = Offset(left, top)
    val size = Size(right - left, bottom - top)
    val corner = androidx.compose.ui.geometry.CornerRadius(radius, radius)
    if (fill) {
        drawRoundRect(color = color, topLeft = topLeft, size = size, cornerRadius = corner)
    } else {
        drawRoundRect(color = color, topLeft = topLeft, size = size, cornerRadius = corner, style = stroke)
    }
}
