package com.komgareader.app.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.komgareader.app.ui.components.SectionHeader
import com.komgareader.app.ui.components.StepperRow
import com.komgareader.domain.render.Hyphenation
import com.komgareader.domain.render.NovelFonts
import com.komgareader.domain.render.NovelSettings
import com.komgareader.domain.render.ReflowConfig
import com.komgareader.domain.render.TextAlign

/**
 * Roman-Typografie-Panel im Onyx-Look ([EinkInfoDialog] — ein Modal über dem
 * Reader, Hardware-Back/X schließt). Jede Änderung schreibt in die globalen
 * Settings; der [NovelReaderViewModel] schichtet das Dokument **live** um und
 * hält dabei die Leseposition (Anker). Daher kein Bestätigen/Abbrechen — die
 * Wirkung ist sofort sichtbar.
 *
 * **Keine Animation:** reine Sofort-State-Wechsel (Steppers, Häkchen-Auswahl),
 * konform zu `animation-gating` (E-Ink). Alle Texte über [LocalStrings] (DE+EN).
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
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current

    EinkInfoDialog(
        title = strings.novelTypography,
        onDismiss = onDismiss,
        closeLabel = strings.close,
    ) {
        // Schriftgröße: 0.7–2.5 em in 0.1-Schritten.
        StepperRow(
            label = strings.novelFontSize,
            valueText = "${(config.fontSizeEm * 100).toInt()} %",
            onDecrement = { onFontSizeEm((config.fontSizeEm - FONT_STEP).coerceAtLeast(FONT_MIN)) },
            onIncrement = { onFontSizeEm((config.fontSizeEm + FONT_STEP).coerceAtMost(FONT_MAX)) },
        )

        // Zeilenabstand: 0.8–2.0 in 0.1-Schritten.
        StepperRow(
            label = strings.novelLineHeight,
            valueText = "${(config.lineHeight * 100).toInt()} %",
            onDecrement = { onLineHeight((config.lineHeight - LINE_STEP).coerceAtLeast(LINE_MIN)) },
            onIncrement = { onLineHeight((config.lineHeight + LINE_STEP).coerceAtMost(LINE_MAX)) },
        )

        SectionHeader(strings.novelMargin)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val current = currentMarginPreset(config)
            MarginChip(strings.novelMarginNarrow, NovelSettings.MARGIN_NARROW, current, onMargin)
            MarginChip(strings.novelMarginNormal, NovelSettings.MARGIN_NORMAL, current, onMargin)
            MarginChip(strings.novelMarginWide, NovelSettings.MARGIN_WIDE, current, onMargin)
        }

        SectionHeader(strings.novelTextAlign)
        ChoiceRow(
            label = strings.novelAlignLeft,
            selected = config.textAlign == TextAlign.LEFT,
            onSelect = { onTextAlign("LEFT") },
        )
        ChoiceRow(
            label = strings.novelAlignJustify,
            selected = config.textAlign == TextAlign.JUSTIFY,
            onSelect = { onTextAlign("JUSTIFY") },
        )

        SectionHeader(strings.novelHyphenation)
        ChoiceRow(
            label = strings.novelHyphenationOff,
            selected = config.hyphenation == Hyphenation.Off,
            onSelect = { onHyphenation("") },
        )
        ChoiceRow(
            label = strings.novelHyphenationDe,
            selected = config.hyphenation == Hyphenation.Language("de"),
            onSelect = { onHyphenation("de") },
        )
        ChoiceRow(
            label = strings.novelHyphenationEn,
            selected = config.hyphenation == Hyphenation.Language("en"),
            onSelect = { onHyphenation("en") },
        )

        // Schriftart: alle gebündelten Lese-Schriften aus der zentralen Registry
        // ([NovelFonts]). Persistiert wird der registrierte Familienname; die Anzeige
        // nutzt das Label verbatim (Schriftnamen werden nicht übersetzt).
        SectionHeader(strings.novelFontFamily)
        NovelFonts.ALL.forEach { font ->
            ChoiceRow(
                label = font.label,
                selected = config.fontFamily == font.family,
                onSelect = { onFontFamily(font.family) },
            )
        }
    }
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
