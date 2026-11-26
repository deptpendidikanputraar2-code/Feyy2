package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MmqaLightColorScheme = lightColorScheme(
    primary = Emerald900,
    onPrimary = Color.White,
    primaryContainer = Emerald100,
    onPrimaryContainer = Emerald950,
    secondary = Amber400,
    onSecondary = Emerald950,
    secondaryContainer = Amber100,
    onSecondaryContainer = Amber950,
    tertiary = Sky700,
    onTertiary = Color.White,
    tertiaryContainer = Sky100,
    onTertiaryContainer = Sky900,
    background = Slate50,
    onBackground = Slate900,
    surface = Color.White,
    onSurface = Slate900,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate700,
    outline = Slate300,
    outlineVariant = Slate200,
    error = Rose600,
    onError = Color.White,
    errorContainer = Rose100,
    onErrorContainer = Rose800
)

@Composable
fun MmqaAr2Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MmqaLightColorScheme,
        typography = AppTypography,
        content = content
    )
}
