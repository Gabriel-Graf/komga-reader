package com.komgareader.app.ui.reader

import androidx.compose.foundation.layout.Arrangement
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
import com.komgareader.app.ui.components.EinkInfoDialog
import com.komgareader.app.ui.components.EinkOutlinedButton
import com.komgareader.app.ui.components.PanelSectionHeader
import com.komgareader.app.ui.components.StepperRow
import com.komgareader.app.ui.theme.EinkTokens
import com.komgareader.domain.render.Hyphenation
import com.komgareader.domain.render.NovelFonts
import com.komgareader.domain.render.NovelSettings
import com.komgareader.domain.render.ReflowConfig
import com.komgareader.domain.render.TextAlign

/**
 * Roman-Typografie-Panel im Onyx-Look ([EinkInfoDialog]). Jede Änderung schreibt in die
 * globalen Settings; der [NovelReaderViewModel] schichtet das Dokument **live** um und
 * hält die Leseposition (Anker) — daher kein Bestätigen/Abbrechen.
 *
 * Gegliedert in Sektionen mit **prominentem** [PanelSectionHeader] (größer/stärker als die
 * Setting-Zeilen) und Hairline-[PanelDivider] dazwischen; enges Spacing. Steppers tragen
 * ihr Label selbst — kein redundanter Kopf darüber.
 *
 * **Keine Animation:** reine Sofort-State-Wechsel (`animation-gating`). Texte über [LocalStrings].
 */
@Composable
fun NovelTypoPanel(
    config: ReflowConfig,
    onFontSizeEm: (Float) -> Unit,
    onLineHeight: (Float) -> Unit,
    onMargin: (String) -> Unit,
    onFontFamily: (String) -> Unit,
    onTextAlign: (String) -> Unit,
    onHyphenation: (String) -> Unit,
    onFontWeight: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current

    EinkInfoDialog(
        title = strings.novelTypography,
        onDismiss = onDismiss,
        closeLabel = strings.close,
        contentSpacing = 4.dp,
    ) {
        // Schrift: Größe + Zeilenabstand (Steppers tragen ihr Label selbst).
        StepperRow(
            label = strings.novelFontSize,
            valueText = "${(config.fontSizeEm * 100).toInt()} %",
            onDecrement = { onFontSizeEm((config.fontSizeEm - FONT_STEP).coerceAtLeast(FONT_MIN)) },
            onIncrement = { onFontSizeEm((config.fontSizeEm + FONT_STEP).coerceAtMost(FONT_MAX)) },
        )
        StepperRow(
            label = strings.novelLineHeight,
            valueText = "${(config.lineHeight * 100).toInt()} %",
            onDecrement = { onLineHeight((config.lineHeight - LINE_STEP).coerceAtLeast(LINE_MIN)) },
            onIncrement = { onLineHeight((config.lineHeight + LINE_STEP).coerceAtMost(LINE_MAX)) },
        )
        // Schriftstärke: dickere Glyphen (E-Ink-Lesbarkeit), in Stufen.
        StepperRow(
            label = strings.novelFontWeight,
            valueText = "+${(config.fontWeight - NovelSettings.FONT_WEIGHT_MIN) / NovelSettings.FONT_WEIGHT_STEP}",
            onDecrement = {
                onFontWeight((config.fontWeight - NovelSettings.FONT_WEIGHT_STEP).coerceAtLeast(NovelSettings.FONT_WEIGHT_MIN))
            },
            onIncrement = {
                onFontWeight((config.fontWeight + NovelSettings.FONT_WEIGHT_STEP).coerceAtMost(NovelSettings.FONT_WEIGHT_MAX))
            },
        )

        PanelDivider()
        PanelSectionHeader(strings.novelMargin)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val current = currentMarginPreset(config)
            MarginChip(strings.novelMarginNarrow, NovelSettings.MARGIN_NARROW, current, onMargin)
            MarginChip(strings.novelMarginNormal, NovelSettings.MARGIN_NORMAL, current, onMargin)
            MarginChip(strings.novelMarginWide, NovelSettings.MARGIN_WIDE, current, onMargin)
        }

        PanelDivider()
        PanelSectionHeader(strings.novelTextAlign)
        ChoiceRow(
            label = strings.novelAlignLeft,
            selected = config.textAlign == TextAlign.LEFT,
            dense = true,
            onSelect = { onTextAlign("LEFT") },
        )
        ChoiceRow(
            label = strings.novelAlignJustify,
            selected = config.textAlign == TextAlign.JUSTIFY,
            dense = true,
            onSelect = { onTextAlign("JUSTIFY") },
        )

        PanelDivider()
        PanelSectionHeader(strings.novelHyphenation)
        ChoiceRow(
            label = strings.novelHyphenationOff,
            selected = config.hyphenation == Hyphenation.Off,
            dense = true,
            onSelect = { onHyphenation("") },
        )
        ChoiceRow(
            label = strings.novelHyphenationDe,
            selected = config.hyphenation == Hyphenation.Language("de"),
            dense = true,
            onSelect = { onHyphenation("de") },
        )
        ChoiceRow(
            label = strings.novelHyphenationEn,
            selected = config.hyphenation == Hyphenation.Language("en"),
            dense = true,
            onSelect = { onHyphenation("en") },
        )

        PanelDivider()
        PanelSectionHeader(strings.novelFontFamily)
        // Schriftart: alle gebündelten Lese-Schriften aus der zentralen Registry ([NovelFonts]).
        NovelFonts.ALL.forEach { font ->
            ChoiceRow(
                label = font.label,
                selected = config.fontFamily == font.family,
                dense = true,
                onSelect = { onFontFamily(font.family) },
            )
        }
    }
}

/** Hairline-Trennlinie zwischen Panel-Sektionen (eink-ui: ≥1.5dp, outlineVariant). */
@Composable
internal fun PanelDivider() {
    HorizontalDivider(
        thickness = EinkTokens.hairline,
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(vertical = 6.dp),
    )
}

private fun currentMarginPreset(config: ReflowConfig): String = when (config.margin) {
    NovelSettings.marginFor(NovelSettings.MARGIN_NARROW) -> NovelSettings.MARGIN_NARROW
    NovelSettings.marginFor(NovelSettings.MARGIN_WIDE) -> NovelSettings.MARGIN_WIDE
    else -> NovelSettings.MARGIN_NORMAL
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
