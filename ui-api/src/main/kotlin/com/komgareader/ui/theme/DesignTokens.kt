package com.komgareader.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.komgareader.domain.model.DisplayBehavior

/**
 * Skin-Tokens, die **je Geräteklasse variieren** — der Kern der Token-Pack-Naht
 * (`docs/superpowers/plans/2026-06-09-ui-platform-skins.md`, P1). Eine Host-UI, drei Looks:
 * mono E-Ink (flach, monochrom), Farb-E-Ink/Kaleido (flach, gedämpfte Farbe), LCD (Farbe + Tiefe
 * über Schatten). Strukturelle Maße bleiben in [EinkTokens]; hier **nur**, was den Look zwischen
 * den Klassen trennt.
 *
 * Single Source of Truth, abgeleitet aus [LocalDisplayBehavior] in [KomgaReaderTheme] und
 * bereitgestellt über [LocalDesignTokens]. Consumer lesen `LocalDesignTokens.current.*` —
 * **nie** ein eigenes `isEink`-Boolean (zementiert die binäre Geräte-Annahme, erklärter Nogo).
 *
 * @property accent Akzent für aktive Zustände (Nav-Balken, Auswahl, Primär-Buttons). mono =
 *   Schwarz/Weiß, Kaleido = gedämpfte Farbe, LCD = volle Farbe.
 * @property onAccent Vordergrund auf [accent]-Flächen (Text/Icon auf gefülltem Primär-Button).
 * @property usesShadows Darf das UI Elevation/Schatten für Tiefe nutzen? **Nur LCD.** E-Ink
 *   (mono **und** Kaleido) bleibt flach — Tiefe über Border (Ghosting-arm). Consumer wählen
 *   darüber Schatten **oder** Hairline-Border.
 * @property cardElevation Schatten-Höhe für Karten/erhabene Flächen, wenn [usesShadows]. 0 auf E-Ink.
 * @property cornerRadius Eckenradius für Karten/Tiles — auf LCD etwas weicher (moderner), auf E-Ink
 *   knapper (klare Kanten lesen sich auf E-Ink ruhiger).
 */
@Immutable
data class DesignTokens(
    val accent: Color,
    val onAccent: Color,
    val usesShadows: Boolean,
    val cardElevation: Dp,
    val cornerRadius: Dp,
)

/**
 * Provisorische Marken-Akzent-Werte (Indigo, zurückhaltend — funktioniert hell **und** dunkel).
 * Der **Kaleido-Wert ist auf echter Hardware zu verifizieren** (LCD-Vorschau täuscht über Kaleidos
 * Sättigungs-Dämpfung). [DesignTokensTest] prüft nur das Mapping gegen diese Konstanten, nicht die
 * Hex-Werte — jede Zahl ist eine 1-Zeilen-Änderung ohne Test-Bruch.
 */
internal val AccentVividLight = Color(0xFF3A5BC7)
internal val AccentVividDark = Color(0xFFB6C4FF)
internal val AccentMuted = Color(0xFF445A86)

/** App-weit bereitgestellte Skin-Tokens. Default = mono E-Ink (sicherster Fall vor Auflösung). */
val LocalDesignTokens = staticCompositionLocalOf {
    designTokensFor(DisplayBehavior.MONO_EINK, dark = false)
}

/**
 * Pure Abbildung Geräteklasse → [DesignTokens]. Die drei Klassen trennen über die zwei
 * orthogonalen Achsen von [DisplayBehavior]:
 *
 * | Klasse   | allowsMotion | allowsAccentColor | accent          | usesShadows |
 * |----------|--------------|-------------------|-----------------|-------------|
 * | mono     | false        | false             | Schwarz/Weiß    | false       |
 * | Kaleido  | false        | true              | gedämpfte Farbe | false       |
 * | LCD      | true         | true              | volle Farbe     | true        |
 *
 * Die sinnlose Kombi `(motion, !accent)` fällt sicher auf den mono-Zweig (kein Akzent).
 */
fun designTokensFor(behavior: DisplayBehavior, dark: Boolean): DesignTokens = when {
    // mono E-Ink: monochrom, flach, knappe Kanten.
    !behavior.allowsAccentColor -> DesignTokens(
        accent = if (dark) Color.White else Color.Black,
        onAccent = if (dark) Color.Black else Color.White,
        usesShadows = false,
        cardElevation = 0.dp,
        cornerRadius = 8.dp,
    )
    // Kaleido: gedämpfte Farbe, aber weiter E-Ink — flach, knappe Kanten.
    !behavior.allowsMotion -> DesignTokens(
        accent = AccentMuted,
        onAccent = Color.White,
        usesShadows = false,
        cardElevation = 0.dp,
        cornerRadius = 8.dp,
    )
    // LCD: volle Farbe, Tiefe über Schatten, weichere Kanten.
    else -> DesignTokens(
        accent = if (dark) AccentVividDark else AccentVividLight,
        onAccent = if (dark) Color.Black else Color.White,
        usesShadows = true,
        cardElevation = 2.dp,
        cornerRadius = 12.dp,
    )
}
