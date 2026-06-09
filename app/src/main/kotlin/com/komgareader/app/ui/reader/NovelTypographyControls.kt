package com.komgareader.app.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.ChoiceRow
import com.komgareader.app.ui.components.EinkOutlinedButton
import com.komgareader.app.ui.components.PanelSectionHeader
import com.komgareader.app.ui.components.StepperRow
import com.komgareader.app.ui.theme.EinkTokens
import com.komgareader.domain.render.NovelFonts
import com.komgareader.domain.render.NovelSettings

/**
 * Die 7 Roman-Typografie-Einstellungen (Schriftgröße, Zeilenabstand, Schriftstärke,
 * Seitenränder, Ausrichtung, Silbentrennung, Schriftart) als **stateless** Komponente —
 * reine UI (Werte rein, Callbacks raus), ohne Bezug auf ein ViewModel oder einen
 * Dialog-Rahmen. Single Source of Truth für die Steuerung dieser Settings; genutzt im
 * In-Reader-Panel ([NovelTypoPanel]) **und** in den Haupt-Settings (Reader-Sektion).
 * Beide Mount-Punkte schreiben über ihr jeweiliges ViewModel in denselben
 * `SettingsRepository` — eine Quelle, konsistente Werte (DRY).
 *
 * Arbeitet bewusst mit den **persistierten Primitiven** (Preset-/Code-Strings), nicht mit
 * der bereits gemappten `ReflowConfig`, damit beide Aufrufer dieselben Werte reichen
 * können. Steppers tragen ihr Label selbst; Sektionen mit prominentem [PanelSectionHeader]
 * und Hairline-Trenner. **Keine Animation:** reine Sofort-State-Wechsel (`animation-gating`).
 */
@Composable
fun NovelTypographyControls(
    fontSizeEm: Float,
    onFontSize: (Float) -> Unit,
    lineHeight: Float,
    onLineHeight: (Float) -> Unit,
    fontWeight: Int,
    onFontWeight: (Int) -> Unit,
    marginPreset: String,
    onMargin: (String) -> Unit,
    textAlign: String,
    onTextAlign: (String) -> Unit,
    hyphenationLang: String,
    onHyphenation: (String) -> Unit,
    fontFamily: String,
    onFontFamily: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current

    Column(modifier.fillMaxWidth()) {
        // Schrift: Größe + Zeilenabstand (Steppers tragen ihr Label selbst).
        StepperRow(
            label = strings.novelFontSize,
            valueText = "${(fontSizeEm * 100).toInt()} %",
            onDecrement = { onFontSize((fontSizeEm - FONT_STEP).coerceAtLeast(FONT_MIN)) },
            onIncrement = { onFontSize((fontSizeEm + FONT_STEP).coerceAtMost(FONT_MAX)) },
        )
        StepperRow(
            label = strings.novelLineHeight,
            valueText = "${(lineHeight * 100).toInt()} %",
            onDecrement = { onLineHeight((lineHeight - LINE_STEP).coerceAtLeast(LINE_MIN)) },
            onIncrement = { onLineHeight((lineHeight + LINE_STEP).coerceAtMost(LINE_MAX)) },
        )
        // Schriftstärke: dickere Glyphen (E-Ink-Lesbarkeit), in Stufen.
        StepperRow(
            label = strings.novelFontWeight,
            valueText = "+${(fontWeight - NovelSettings.FONT_WEIGHT_MIN) / NovelSettings.FONT_WEIGHT_STEP}",
            onDecrement = {
                onFontWeight((fontWeight - NovelSettings.FONT_WEIGHT_STEP).coerceAtLeast(NovelSettings.FONT_WEIGHT_MIN))
            },
            onIncrement = {
                onFontWeight((fontWeight + NovelSettings.FONT_WEIGHT_STEP).coerceAtMost(NovelSettings.FONT_WEIGHT_MAX))
            },
        )

        PanelDivider()
        PanelSectionHeader(strings.novelMargin)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MarginChip(strings.novelMarginNarrow, NovelSettings.MARGIN_NARROW, marginPreset, onMargin)
            MarginChip(strings.novelMarginNormal, NovelSettings.MARGIN_NORMAL, marginPreset, onMargin)
            MarginChip(strings.novelMarginWide, NovelSettings.MARGIN_WIDE, marginPreset, onMargin)
        }

        PanelDivider()
        PanelSectionHeader(strings.novelTextAlign)
        ChoiceRow(
            label = strings.novelAlignLeft,
            selected = textAlign == "LEFT",
            dense = true,
            onSelect = { onTextAlign("LEFT") },
        )
        ChoiceRow(
            label = strings.novelAlignJustify,
            selected = textAlign == "JUSTIFY",
            dense = true,
            onSelect = { onTextAlign("JUSTIFY") },
        )

        PanelDivider()
        PanelSectionHeader(strings.novelHyphenation)
        ChoiceRow(
            label = strings.novelHyphenationOff,
            selected = hyphenationLang.isBlank(),
            dense = true,
            onSelect = { onHyphenation("") },
        )
        ChoiceRow(
            label = strings.novelHyphenationDe,
            selected = hyphenationLang == "de",
            dense = true,
            onSelect = { onHyphenation("de") },
        )
        ChoiceRow(
            label = strings.novelHyphenationEn,
            selected = hyphenationLang == "en",
            dense = true,
            onSelect = { onHyphenation("en") },
        )

        PanelDivider()
        PanelSectionHeader(strings.novelFontFamily)
        // Schriftart: alle gebündelten Lese-Schriften aus der zentralen Registry ([NovelFonts]).
        NovelFonts.ALL.forEach { font ->
            ChoiceRow(
                label = font.label,
                selected = fontFamily == font.family,
                dense = true,
                onSelect = { onFontFamily(font.family) },
            )
        }
    }
}

/** Hairline-Trennlinie zwischen den Typo-Sektionen (eink-ui: ≥1.5dp, outlineVariant). */
@Composable
internal fun PanelDivider() {
    HorizontalDivider(
        thickness = EinkTokens.hairline,
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(vertical = 6.dp),
    )
}

@Composable
private fun RowScope.MarginChip(
    label: String,
    preset: String,
    current: String,
    onSelect: (String) -> Unit,
) {
    val selected = preset == current
    EinkOutlinedButton(
        onClick = { onSelect(preset) },
        modifier = Modifier.weight(1f),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

private const val FONT_MIN = 0.7f
private const val FONT_MAX = 2.5f
private const val FONT_STEP = 0.1f
private const val LINE_MIN = 0.8f
private const val LINE_MAX = 2.0f
private const val LINE_STEP = 0.1f
