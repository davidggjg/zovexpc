package com.zovex.pc.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Windows 11-like dark palette, built from our own tokens. No Microsoft
// assets are bundled; see docs/DESIGN.md for the font and icon plan.
private val ZovexDark = darkColorScheme(
    primary = Color(0xFF4CC2FF),          // accent
    onPrimary = Color(0xFF003544),
    background = Color(0xFF202020),       // desktop base
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF2C2C2C),          // card / flyout
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF383838),   // layered control
    onSurfaceVariant = Color(0xFFC5C5C5), // secondary text
    outline = Color(0xFF4A4A4A),
    error = Color(0xFFFF99A4),
)

@Composable
fun ZovexTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ZovexDark,
        typography = Typography(),
        content = content,
    )
}
