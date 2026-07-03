package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = PartOutPrimary,
    onPrimary = PartOutTextPrimary,
    secondary = PartOutTextSecondary,
    background = PartOutBackground,
    onBackground = PartOutTextPrimary,
    surface = PartOutSurface,
    onSurface = PartOutTextPrimary,
    outline = PartOutBorder,
    surfaceVariant = NeutralDark,
    onSurfaceVariant = PartOutTextSecondary
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force dark theme for the industrial aesthetic
    dynamicColor: Boolean = false, // Disable dynamic colors to preserve visual branding
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
