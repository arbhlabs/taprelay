package com.arbhlabs.taprelay.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val Teal = Color(0xFF5BE7D6)
private val TealDim = Color(0xFF7DD6CC)
private val TealDark = Color(0xFF1C6E68)

private val LightColors = lightColorScheme(
    primary = TealDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB9F3EC),
    onPrimaryContainer = Color(0xFF00201E),
    secondary = TealDark,
    background = Color(0xFFF5F8F8),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE3EAEA),
    outline = Color(0xFF9BB0AE),
)

private val DarkColors = darkColorScheme(
    primary = Teal,
    onPrimary = Color(0xFF00332F),
    primaryContainer = Color(0xFF0E4A44),
    onPrimaryContainer = Color(0xFFB9F3EC),
    secondary = TealDim,
    background = Color(0xFF080C0E),
    onBackground = Color(0xFFE7EDEC),
    surface = Color(0xFF10161A),
    onSurface = Color(0xFFE7EDEC),
    surfaceVariant = Color(0xFF1B2530),
    onSurfaceVariant = Color(0xFFAFC0BE),
    surfaceContainer = Color(0xFF161D22),
    surfaceContainerHigh = Color(0xFF1C242A),
    outline = Color(0xFF3A4A48),
    errorContainer = Color(0xFF5A2426),
    onErrorContainer = Color(0xFFFFD9D9),
)

@Composable
fun TapRelayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(colorScheme = colorScheme, typography = Typography(), content = content)
}
