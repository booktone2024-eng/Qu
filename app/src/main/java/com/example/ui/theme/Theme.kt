package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ElegantDarkColorScheme = darkColorScheme(
    primary = ElegantPrimary,
    onPrimary = ElegantOnPrimary,
    primaryContainer = ElegantPrimaryContainer,
    onPrimaryContainer = ElegantOnPrimaryContainer,
    secondary = ElegantSecondary,
    onSecondary = ElegantOnSecondary,
    secondaryContainer = ElegantSecondaryContainer,
    onSecondaryContainer = ElegantOnSecondaryContainer,
    tertiary = ElegantTertiary,
    onTertiary = ElegantOnTertiary,
    background = ElegantBackground,
    onBackground = ElegantOnBackground,
    surface = ElegantSurface,
    onSurface = ElegantOnSurface,
    surfaceVariant = ElegantSurfaceVariant,
    onSurfaceVariant = ElegantOnSurfaceVariant,
    outline = ElegantOutline,
    outlineVariant = ElegantOutlineVariant,
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force dark theme for Elegant Dark
    dynamicColor: Boolean = false, // Disable dynamic colors to enforce the specific brand colors
    content: @Composable () -> Unit,
) {
    // Always use the Elegant Dark ColorScheme
    val colorScheme = ElegantDarkColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
