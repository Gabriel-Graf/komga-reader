package com.komgareader.app.ui.pack

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.komgareader.domain.model.ColorRolesSpec
import com.komgareader.domain.model.ThemeSpec
import com.komgareader.domain.model.UiPackSpec
import com.komgareader.ui.theme.DesignTokens
import com.komgareader.ui.theme.UiPack

/**
 * Übersetzt die deklarative [ThemeSpec] (domain, Primitive) in einen Runtime-[UiPack] — **nur hier in `:app`**
 * (domain/data bleiben Compose-frei). `null`, wenn der Pack keine Farb-Sektion liefert (dann greift weiter der
 * `tokenOverride`-Pfad für reine accent/cornerRadius-Packs). Fehlender Modus mirror't den anderen; fehlende
 * Einzel-Rolle bleibt Material-Default. E-Ink-Gate (Pack nur auf LCD) liegt host-seitig in `KomgaReaderTheme`.
 */
fun UiPackSpec.toUiPackOrNull(): UiPack? {
    val t = theme?.takeIf { it.hasColors } ?: return null
    val darkRoles = t.dark ?: t.light!!
    val lightRoles = t.light ?: t.dark!!
    val radius = (t.cornerRadiusDp ?: 16).coerceIn(0, 32).dp
    val shadows = t.elevation ?: true
    val packShapes = Shapes(
        small = RoundedCornerShape((radius.value - 4).coerceAtLeast(0f).dp),
        medium = RoundedCornerShape(radius),
        large = RoundedCornerShape((radius.value + 4).dp),
    )
    val packTypography = buildTypography(t)
    return object : UiPack {
        override val id = "external:$packageName"
        override fun colorScheme(dark: Boolean) = schemeOf(if (dark) darkRoles else lightRoles, dark)
        override fun designTokens(dark: Boolean): DesignTokens {
            val roles = if (dark) darkRoles else lightRoles
            return DesignTokens(
                accent = roles.accent.hexOr(Color(0xFF3D5AFE)),
                onAccent = roles.onAccent.hexOr(Color.White),
                usesShadows = shadows,
                cardElevation = if (shadows) 3.dp else 0.dp,
                cornerRadius = radius,
            )
        }
        override val shapes = packShapes
        override val typography = packTypography
    }
}

private fun schemeOf(r: ColorRolesSpec, dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val accent = r.accent.hexOr(base.primary)
    val onAccent = r.onAccent.hexOr(base.onPrimary)
    val onBg = r.onBackground.hexOr(base.onBackground)
    return base.copy(
        primary = accent, onPrimary = onAccent,
        background = r.background.hexOr(base.background), onBackground = onBg,
        surface = r.surface.hexOr(r.background.hexOr(base.surface)), onSurface = onBg,
        surfaceVariant = r.navDock.hexOr(base.surfaceVariant),
        onSurfaceVariant = r.onSurfaceVariant.hexOr(base.onSurfaceVariant),
        outline = r.outline.hexOr(base.outline),
    )
}

private fun buildTypography(t: ThemeSpec): Typography {
    val base = Typography()
    val hw = t.typography?.headlineWeight?.let { FontWeight(it.coerceIn(100, 900)) }
    val tw = t.typography?.titleWeight?.let { FontWeight(it.coerceIn(100, 900)) }
    val track = t.typography?.headlineTrackingEm
    return base.copy(
        headlineSmall = base.headlineSmall.copy(
            fontWeight = hw ?: base.headlineSmall.fontWeight,
            letterSpacing = track?.let { (it * 16).sp } ?: base.headlineSmall.letterSpacing,
        ),
        titleLarge = base.titleLarge.copy(fontWeight = tw ?: base.titleLarge.fontWeight),
    )
}

/** Parst `#RRGGBB` über das vorhandene [parseHexColor]; bei null/ungültig der [fallback]. */
private fun String?.hexOr(fallback: Color): Color = this?.let(::parseHexColor) ?: fallback
