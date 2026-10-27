package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val StudioDarkColorScheme = darkColorScheme(
    primary = SignalOrange,
    onPrimary = Ink950,
    primaryContainer = Ink800,
    onPrimaryContainer = Sand50,
    secondary = TealAccent,
    onSecondary = Ink950,
    secondaryContainer = Ink750,
    onSecondaryContainer = TealAccent,
    tertiary = LimeAccent,
    onTertiary = Ink950,
    background = Ink950,
    onBackground = Sand50,
    surface = Ink900,
    onSurface = Sand50,
    surfaceVariant = Ink850,
    onSurfaceVariant = Sand300,
    outline = Ink700,
    error = RecRed,
    onError = Ink950
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StudioDarkColorScheme,
        typography = Typography,
        content = content
    )
}
