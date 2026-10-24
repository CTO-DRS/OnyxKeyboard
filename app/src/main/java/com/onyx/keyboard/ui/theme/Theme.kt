package com.onyx.keyboard.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Onyx brand palette — deep black + neon violet + holographic blue.
val OnyxBlack = Color(0xFF0B0B12)
val OnyxSurface = Color(0xFF12101C)
val OnyxSurfaceHi = Color(0xFF1E1A2E)
val OnyxPurple = Color(0xFFB37BFF)
val OnyxBlue = Color(0xFF3EE6FF)
val OnyxGlow = Color(0xFF8A5CFF)
val OnyxText = Color(0xFFF2EDFF)
val OnxDanger = Color(0xFFFF5CA8)

private val OnyxColorScheme = darkColorScheme(
    primary = OnyxPurple,
    onPrimary = Color.Black,
    secondary = OnyxBlue,
    onSecondary = Color.Black,
    tertiary = OnyxGlow,
    background = OnyxBlack,
    onBackground = OnyxText,
    surface = OnyxSurface,
    onSurface = OnyxText,
    surfaceVariant = OnyxSurfaceHi,
    onSurfaceVariant = OnyxText.copy(alpha = 0.75f),
    error = OnxDanger,
    outline = OnyxPurple.copy(alpha = 0.35f),
    primaryContainer = OnyxPurple.copy(alpha = 0.22f),
    onPrimaryContainer = OnyxText,
)

@Composable
fun OnyxAppTheme(content: @Composable () -> Unit) {
    // Onyx is a night-brand experience: always dark, regardless of system setting.
    MaterialTheme(
        colorScheme = OnyxColorScheme,
        content = content
    )
}
