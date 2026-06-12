package com.komgareader.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.komgareader.domain.model.DisplayBehavior

/**
 * Ein **UI-Pack** ist der vollständige Look einer Geräteklasse: ColorScheme (hell+dunkel), Skin-
 * [DesignTokens], Shapes und Typografie. Der erste echte Vertrag der modularen UI
 * (`big-picture-and-goals.md` → ui-modularity, „Theme zuerst, Layout danach"): die drei Plattformen
 * sind **drei Packs hinter einer Naht**, kein Fork des UI-Baums. Ein späteres Community-/Plugin-Pack
 * implementiert genau dieses Interface; der Host ([KomgaReaderTheme]) wählt und rendert — die
 * E-Ink-Invarianten (Bewegung/Schatten) bleiben **host-erzwungen**, nie pack-bestimmt.
 *
 * Der Vertrag lebt jetzt im Modul `:ui-api` (A1), ist aber **noch nicht ABI-eingefroren/versioniert** —
 * das Einfrieren (Gegenstück zu `plugin-api`) kommt mit dem externen Pack-Lader (L1/L2).
 */
interface UiPack {
    /** Stabile Kennung (für Auswahl/Registry; später Pack-Identität). */
    val id: String

    /** Material-Farbschema der Klasse. mono = reines S/W; Kaleido = Papier + gedämpfter Akzent; LCD = volles Schema. */
    fun colorScheme(dark: Boolean): ColorScheme

    /** Skin-Tokens (Akzent, Schatten/Elevation, Kanten) — siehe [DesignTokens]. */
    fun designTokens(dark: Boolean): DesignTokens

    /** Eckenformen — E-Ink knapp, LCD weicher. */
    val shapes: Shapes

    /** Typografie — E-Ink hebt Gewichte (Lesbarkeit ohne Sub-Pixel); LCD darf leichter sein. */
    val typography: Typography
}

// ── Shapes ──────────────────────────────────────────────────────────────────

private val EinkShapes = Shapes(
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
)

private val SoftShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
)

// ── Typografie ──────────────────────────────────────────────────────────────

private val Base = Typography()

/**
 * E-Ink-Typografie: Material-Default (400) wirkt auf E-Ink bei kleiner Schrift zu blass — Gewichte
 * zentral anheben. Auf LCD (Sub-Pixel-Smoothing) ist das nicht nötig → dort [Base].
 */
private val EinkTypography = Base.copy(
    bodyLarge = Base.bodyLarge.copy(fontWeight = FontWeight.Medium),
    bodyMedium = Base.bodyMedium.copy(fontWeight = FontWeight.Medium),
    bodySmall = Base.bodySmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.Bold),
    labelMedium = Base.labelMedium.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = Base.labelSmall.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
)

// ── Mono E-Ink ────────────────────────────────────────────────────────────────

private val MonoLight = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFE8E8E8),
    onSurfaceVariant = Color(0xFF222222),
    outline = Color.Black,
    outlineVariant = Color(0xFF777777),
)

private val MonoDark = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFFDDDDDD),
    outline = Color.White,
    outlineVariant = Color(0xFF8A8A8A),
)

/**
 * mono E-Ink: maximaler Kontrast, nur Schwarz/Weiß/Grau, keine Akzentfarbe, flach (Tiefe über
 * Border). Pflicht-Look auf mono-Geräten (`eink-design-language.md`).
 */
val MonoEinkPack: UiPack = object : UiPack {
    override val id = "mono-eink"
    override fun colorScheme(dark: Boolean) = if (dark) MonoDark else MonoLight
    override fun designTokens(dark: Boolean) =
        designTokensFor(DisplayBehavior(allowsMotion = false, allowsAccentColor = false), dark)
    override val shapes = EinkShapes
    override val typography = EinkTypography
}

// ── Farb-E-Ink (Kaleido) ──────────────────────────────────────────────────────

// Papier-Look wie mono (weiße/schwarze Flächen, starke Border — E-Ink braucht Kontrast), aber mit
// gedämpftem Akzent als Primärfarbe + sehr leichtem Container. Flach (Kaleido ghostet bei Bewegung).
private val KaleidoLight = MonoLight.copy(
    primary = AccentMuted,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE1F0),
    onPrimaryContainer = Color(0xFF101A2E),
    secondary = AccentMuted,
    onSecondary = Color.White,
    tertiary = AccentMuted,
    onTertiary = Color.White,
)

private val KaleidoDark = MonoDark.copy(
    primary = Color(0xFF9FB0D8),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF2A3550),
    onPrimaryContainer = Color(0xFFDDE1F0),
    secondary = Color(0xFF9FB0D8),
    onSecondary = Color.Black,
    tertiary = Color(0xFF9FB0D8),
    onTertiary = Color.Black,
)

/**
 * Farb-E-Ink (Boox Kaleido): Papier-Flächen wie mono, aber **gedämpfter** Akzent erlaubt — die
 * Kaleido-Schicht dämpft Sättigung ohnehin. Weiter flach + bewegungsfrei (E-Ink). Reales Zielgerät
 * (Go Color 7) fällt hierher.
 */
val KaleidoPack: UiPack = object : UiPack {
    override val id = "kaleido"
    override fun colorScheme(dark: Boolean) = if (dark) KaleidoDark else KaleidoLight
    override fun designTokens(dark: Boolean) =
        designTokensFor(DisplayBehavior(allowsMotion = false, allowsAccentColor = true), dark)
    override val shapes = EinkShapes
    override val typography = EinkTypography
}

// ── LCD (Phone/Tablet) ────────────────────────────────────────────────────────

// Volles, modernes Material-3-Indigo-Schema: getönte Surfaces/Container, weichere Kanten, leichtere
// Typo, Tiefe über Schatten. Hier glänzt der Bewegungs-/Farb-Pfad.
private val LcdLight = lightColorScheme(
    primary = AccentVividLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE1FF),
    onPrimaryContainer = Color(0xFF001257),
    secondary = Color(0xFF595E72),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDEE2F9),
    onSecondaryContainer = Color(0xFF161B2C),
    tertiary = Color(0xFF75546F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD7F5),
    onTertiaryContainer = Color(0xFF2C122A),
    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1A1B21),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1A1B21),
    surfaceVariant = Color(0xFFE2E1EC),
    onSurfaceVariant = Color(0xFF45464F),
    outline = Color(0xFF767680),
    outlineVariant = Color(0xFFC6C6D0),
)

private val LcdDark = darkColorScheme(
    primary = AccentVividDark,
    onPrimary = Color(0xFF002584),
    primaryContainer = Color(0xFF1B3CA8),
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFFC2C6DD),
    onSecondary = Color(0xFF2B3042),
    secondaryContainer = Color(0xFF424659),
    onSecondaryContainer = Color(0xFFDEE2F9),
    tertiary = Color(0xFFE3BADB),
    onTertiary = Color(0xFF432740),
    tertiaryContainer = Color(0xFF5C3F58),
    onTertiaryContainer = Color(0xFFFFD7F5),
    background = Color(0xFF121318),
    onBackground = Color(0xFFE3E1E9),
    surface = Color(0xFF121318),
    onSurface = Color(0xFFE3E1E9),
    surfaceVariant = Color(0xFF45464F),
    onSurfaceVariant = Color(0xFFC6C6D0),
    outline = Color(0xFF90909A),
    outlineVariant = Color(0xFF45464F),
)

/**
 * LCD-Phone/-Tablet: volles modernes Farbschema (getönte Surfaces/Container), weiche Kanten,
 * leichtere Typo, Tiefe über Schatten + Bewegung erlaubt. Der „auf dem Tablet darf's hübsch sein"-Look.
 */
val LcdPack: UiPack = object : UiPack {
    override val id = "lcd"
    override fun colorScheme(dark: Boolean) = if (dark) LcdDark else LcdLight
    override fun designTokens(dark: Boolean) =
        designTokensFor(DisplayBehavior(allowsMotion = true, allowsAccentColor = true), dark)
    override val shapes = SoftShapes
    override val typography = Base
}

// ── Auswahl ───────────────────────────────────────────────────────────────────

/**
 * Wählt das Built-in-Pack zur Geräteklasse (zwei orthogonale Achsen). Die sinnlose Kombi
 * `(motion, !accent)` fällt sicher auf mono. Pure Funktion → unit-testbar.
 */
fun packFor(behavior: DisplayBehavior): UiPack = when {
    !behavior.allowsAccentColor -> MonoEinkPack
    !behavior.allowsMotion -> KaleidoPack
    else -> AuroraPack // Aurora ist der moderne Mobile-Look für LCD; LcdPack bleibt als Fallback in der Registry
}

/** Aktives Pack, app-weit. Default = mono E-Ink (sicherster Fall vor Auflösung). */
val LocalUiPack = staticCompositionLocalOf { MonoEinkPack }
