package com.komgareader.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
    // Mittelgrau statt #CCCCCC — auf E-Ink ist ein zu heller Hairline unsichtbar.
    outlineVariant = Color(0xFF777777),
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
    // Heller als #444444 — sonst auf dunklem E-Ink-Grund unsichtbar.
    outlineVariant = Color(0xFF8A8A8A),
)

private val EinkShapes = Shapes(
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
)

/**
 * E-Ink-Typografie: Material-Default rendert Fließ- und Label-Text in [FontWeight.Normal] (400) —
 * auf E-Ink (kein sub-pixel-Smoothing, gedämpfter Kontrast) wirkt das bei kleiner Schrift **zu
 * dünn/blass** (Such-Placeholder, Kapitel-Untertitel, „Lädt…", leere-Tab-Platzhalter). Darum hier
 * **zentral** die Gewichte anheben: Body → Medium (500), Labels + kleine Titel → SemiBold (600).
 * Große Überschriften bleiben (Größe trägt den Kontrast). Eine Quelle der Wahrheit — kein
 * `fontWeight` an jeder einzelnen `Text`-Stelle.
 */
private val Base = Typography()
private val EinkTypography = Base.copy(
    bodyLarge = Base.bodyLarge.copy(fontWeight = FontWeight.Medium),
    bodyMedium = Base.bodyMedium.copy(fontWeight = FontWeight.Medium),
    bodySmall = Base.bodySmall.copy(fontWeight = FontWeight.Medium),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = Base.labelMedium.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = Base.labelSmall.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
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
        typography = EinkTypography,
        content = content,
    )
}
