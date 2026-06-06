package com.komgareader.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * E-Ink-Theme: maximaler Kontrast, nur Schwarz/Weiß/zwei Graustufen, keine
 * Akzentfarbe (Akzent = solides Schwarz bzw. invertiert Weiß). Tiefe entsteht im
 * UI über 1.5px-Border statt Schatten/Verläufe (Ghosting-arm).
 */
private val LightEink = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFE8E8E8),
    onSurfaceVariant = Color(0xFF222222),
    outline = Color.Black,
    outlineVariant = Color(0xFFCCCCCC),
)

private val DarkEink = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFFDDDDDD),
    outline = Color.White,
    outlineVariant = Color(0xFF444444),
)

private val EinkShapes = Shapes(
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
)

enum class ThemeMode { LIGHT, DARK, SYSTEM }

@Composable
fun KomgaReaderTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkEink else LightEink,
        shapes = EinkShapes,
        content = content,
    )
}
