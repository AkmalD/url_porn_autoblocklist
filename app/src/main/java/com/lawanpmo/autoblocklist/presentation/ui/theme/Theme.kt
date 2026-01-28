package com.lawanpmo.autoblocklist.presentation.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = Blue500,
    onPrimary = Color.White,
    primaryContainer = Blue200,
    onPrimaryContainer = Blue700,
    secondary = Teal500,
    onSecondary = Color.White,
    secondaryContainer = Teal200,
    onSecondaryContainer = Teal700,
    background = Gray50,
    onBackground = Gray900,
    surface = Color.White,
    onSurface = Gray900,
    error = Red500,
    onError = Color.White,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer
)

private val DarkColorScheme = darkColorScheme(
    primary = Blue200,
    onPrimary = Blue700,
    primaryContainer = Blue700,
    onPrimaryContainer = Blue200,
    secondary = Teal200,
    onSecondary = Teal700,
    secondaryContainer = Teal700,
    onSecondaryContainer = Teal200,
    background = Gray900,
    onBackground = Gray50,
    surface = Gray900,
    onSurface = Gray50,
    error = Red500,
    onError = Color.White,
    errorContainer = Red700,
    onErrorContainer = Color.White
)

@Composable
fun AutoBlocklistTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
