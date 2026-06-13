package com.komgareader.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Der vereinheitlichende Aurora-Akzent (Cobalt/Royal). `internal`, damit Tests die Konstante statt
 *  wiederholter Magic-Hex referenzieren (analog `AccentVividLight` in `DesignTokens.kt`). */
internal val Cobalt = Color(0xFF3D5AFE)

private val AuroraDark = darkColorScheme(
    primary = Cobalt, onPrimary = Color.White,
    primaryContainer = Color(0xFF262C45), onPrimaryContainer = Color(0xFFAEB7F5),
    background = Color(0xFF15171C), onBackground = Color(0xFFE9EAEE),
    surface = Color(0xFF1C1F26), onSurface = Color(0xFFE9EAEE),
    surfaceVariant = Color(0xFF1C1F26), onSurfaceVariant = Color(0xFF9296A0),
    outline = Color(0xFF2E313A), outlineVariant = Color(0xFF3A3E48),
)

private val AuroraLight = lightColorScheme(
    primary = Cobalt, onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE0FF), onPrimaryContainer = Color(0xFF11205E),
    // Weiß als Haupt-Hintergrund + sauberes neutrales (nicht blaustichiges) Hellgrau für Flächen/Nav-Dock.
    background = Color(0xFFFFFFFF), onBackground = Color(0xFF1A1B1E),
    surface = Color(0xFFEDEDEF), onSurface = Color(0xFF1A1B1E),
    surfaceVariant = Color(0xFFE1E1E4), onSurfaceVariant = Color(0xFF6B6C70),
    outline = Color(0xFFD3D3D6), outlineVariant = Color(0xFFE1E1E4),
)

private val AuroraShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
)

private val AuroraBase = Typography()
private val AuroraTypography = AuroraBase.copy(
    headlineSmall = AuroraBase.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    titleLarge = AuroraBase.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleMedium = AuroraBase.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = AuroraBase.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

/**
 * Aurora: distinktiver Modern-Mobile-Look (LCD-Geräteklasse). Slate/Deeper-Grey + Cobalt, weiche große
 * Radien, Tiefe über Schatten, Motion erlaubt. Vierter [UiPack] hinter der Theme-Naht; gewählt im
 * Smartphone-Modus (`packFor`, Task 2). Spec: `2026-06-12-modern-mobile-ui-pack-aurora-design.md` §2.
 */
val AuroraPack: UiPack = object : UiPack {
    override val id = "aurora"
    override fun colorScheme(dark: Boolean) = if (dark) AuroraDark else AuroraLight
    override fun designTokens(dark: Boolean) = DesignTokens(
        accent = Cobalt,
        onAccent = Color.White,
        usesShadows = true,
        cardElevation = 3.dp,
        cornerRadius = 16.dp,
    )
    override val shapes = AuroraShapes
    override val typography = AuroraTypography
}
