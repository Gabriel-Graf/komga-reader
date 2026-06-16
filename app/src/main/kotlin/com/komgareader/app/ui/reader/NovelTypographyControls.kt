package com.komgareader.app.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.i18n.Strings
import com.komgareader.app.ui.components.EinkSliderRow
import com.komgareader.app.ui.components.PanelSectionHeader
import com.komgareader.app.ui.components.SegmentOption
import com.komgareader.app.ui.components.SegmentedChoiceRow
import com.komgareader.app.ui.components.SliderLandmark
import com.komgareader.ui.icons.AppIcons
import androidx.compose.ui.graphics.Color
import com.komgareader.ui.theme.EinkTokens
import com.komgareader.ui.theme.LocalDesignTokens
import com.komgareader.domain.render.NovelFont
import com.komgareader.domain.render.NovelFonts
import com.komgareader.domain.render.NovelSettings
import java.io.File
import kotlin.math.roundToInt

/**
 * Die 7 Roman-Typografie-Einstellungen (Schriftgröße, Zeilenabstand, Schriftstärke,
 * Seitenränder, Ausrichtung, Silbentrennung, Schriftart) als **stateless** Reader-Panel-
 * Komponente — reine UI (Werte rein, Callbacks raus), ohne Bezug auf ein ViewModel oder
 * einen Dialog-Rahmen. Genutzt im Typografie-Tab des Settings-Bottom-Sheets ([NovelSettingsSheet]).
 *
 * Arbeitet mit den **persistierten Primitiven** (Preset-/Code-Strings); Ranges und Presets
 * aus [NovelSettings] (SSOT — geteilt mit der Settings-Screen-Darstellung
 * `NovelTypographySettings`). Tuned for the wide bottom-sheet panel: the four numeric/preset
 * controls (margin, font size, line height, weight) are discrete [EinkSliderRow]s, alignment is
 * a [SegmentedChoiceRow]. **Keine Animation:** reine Sofort-State-Wechsel (`animation-gating`).
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
    availableFonts: List<NovelFont> = NovelFonts.ALL,
    fontFiles: Map<String, File> = emptyMap(),
) {
    val strings = LocalStrings.current

    Column(modifier.fillMaxWidth()) {
        // Margin: discrete slider over the ordered NovelSettings.MARGIN_STEPS with named
        // landmark ticks (Narrow / Normal / Wide / X-Wide). valueText = current preset's label.
        val marginPosition = NovelSettings.MARGIN_STEPS.indexOf(marginPreset).coerceAtLeast(0)
        EinkSliderRow(
            label = strings.novelMargin,
            valueText = marginPresetLabel(strings, marginPreset),
            position = marginPosition,
            stepCount = (NovelSettings.MARGIN_STEPS.size - 1).coerceAtLeast(0),
            onPosition = { onMargin(NovelSettings.MARGIN_STEPS[it.coerceIn(0, NovelSettings.MARGIN_STEPS.lastIndex)]) },
            landmarks = marginLandmarks(strings),
        )

        // Font size / line height / weight: each a discrete slider over its NovelSettings range.
        // position = round((value-min)/step), stepCount = round((max-min)/step). No landmark label
        // here — the value text already shows the %/step, so a "100 %"/"+0" tick under the track was
        // redundant and added an empty-looking gap row (request 2026-06-16). Adjacent to the margin
        // slider (no divider) so the whole numeric cluster reads as one tight group.
        val fontSizeSteps = stepCountOf(NovelSettings.FONT_SIZE_MIN, NovelSettings.FONT_SIZE_MAX, NovelSettings.FONT_SIZE_STEP)
        EinkSliderRow(
            label = strings.novelFontSize,
            valueText = "${(fontSizeEm * 100).roundToInt()} %",
            position = positionOf(fontSizeEm, NovelSettings.FONT_SIZE_MIN, NovelSettings.FONT_SIZE_STEP),
            stepCount = fontSizeSteps,
            onPosition = {
                onFontSize(valueOf(it, NovelSettings.FONT_SIZE_MIN, NovelSettings.FONT_SIZE_STEP, NovelSettings.FONT_SIZE_MAX))
            },
        )
        val lineHeightSteps = stepCountOf(NovelSettings.LINE_HEIGHT_MIN, NovelSettings.LINE_HEIGHT_MAX, NovelSettings.LINE_HEIGHT_STEP)
        EinkSliderRow(
            label = strings.novelLineHeight,
            valueText = "${(lineHeight * 100).roundToInt()} %",
            position = positionOf(lineHeight, NovelSettings.LINE_HEIGHT_MIN, NovelSettings.LINE_HEIGHT_STEP),
            stepCount = lineHeightSteps,
            onPosition = {
                onLineHeight(valueOf(it, NovelSettings.LINE_HEIGHT_MIN, NovelSettings.LINE_HEIGHT_STEP, NovelSettings.LINE_HEIGHT_MAX))
            },
        )
        val weightSteps = ((NovelSettings.FONT_WEIGHT_MAX - NovelSettings.FONT_WEIGHT_MIN) / NovelSettings.FONT_WEIGHT_STEP).coerceAtLeast(0)
        EinkSliderRow(
            label = strings.novelFontWeight,
            valueText = "+${(fontWeight - NovelSettings.FONT_WEIGHT_MIN) / NovelSettings.FONT_WEIGHT_STEP}",
            position = ((fontWeight - NovelSettings.FONT_WEIGHT_MIN) / NovelSettings.FONT_WEIGHT_STEP).coerceIn(0, weightSteps),
            stepCount = weightSteps,
            onPosition = {
                onFontWeight(
                    (NovelSettings.FONT_WEIGHT_MIN + it * NovelSettings.FONT_WEIGHT_STEP)
                        .coerceIn(NovelSettings.FONT_WEIGHT_MIN, NovelSettings.FONT_WEIGHT_MAX),
                )
            },
        )

        PanelDivider()
        PanelSectionHeader(strings.novelTextAlign)
        SegmentedChoiceRow(
            label = strings.novelTextAlign,
            options = listOf(
                SegmentOption("LEFT", strings.novelAlignLeft),
                SegmentOption("JUSTIFY", strings.novelAlignJustify),
            ),
            selectedKey = textAlign,
            onSelect = onTextAlign,
        )

        PanelDivider()
        HyphenationPicker(
            value = hyphenationLang,
            onValue = onHyphenation,
        )

        PanelDivider()
        PanelSectionHeader(strings.novelFontFamily)
        // Font picker: each option's label is rendered IN that font (live sample).
        // ChoiceRow uses HighlightText which has no fontFamily param, so we use a local
        // row composable that mirrors ChoiceRow's flat Onyx-look (selectable row, checkmark
        // on the right) while applying the font's own face to the label Text.
        val designTokens = LocalDesignTokens.current
        availableFonts.forEach { font ->
            val file = fontFiles[font.family]
            val sampleFamily = remember(file) {
                file?.let { runCatching { FontFamily(Font(it)) }.getOrNull() }
            }
            FontPickerRow(
                label = font.label,
                selected = fontFamily == font.family,
                sampleFamily = sampleFamily,
                accentTint = designTokens.accent,
                onSelect = { onFontFamily(font.family) },
            )
        }
    }
}

/** Localized display label for a margin preset key (covers landmark + intermediate steps). */
private fun marginPresetLabel(strings: Strings, preset: String): String = when (preset) {
    NovelSettings.MARGIN_NARROW -> strings.novelMarginNarrow
    NovelSettings.MARGIN_NORMAL -> strings.novelMarginNormal
    NovelSettings.MARGIN_WIDE -> strings.novelMarginWide
    NovelSettings.MARGIN_XWIDE -> strings.novelMarginXWide
    // Snug/Relaxed are unlabelled intermediates: show the page-margin px so the slider value
    // is never blank between named landmarks.
    else -> "${NovelSettings.marginFor(preset).left} px"
}

/** Named landmark ticks for the margin slider, positioned by their index in [NovelSettings.MARGIN_STEPS]. */
private fun marginLandmarks(strings: Strings): List<SliderLandmark> =
    NovelSettings.MARGIN_STEPS.mapIndexedNotNull { index, preset ->
        if (preset in NovelSettings.MARGIN_LANDMARKS) {
            SliderLandmark(index, marginPresetLabel(strings, preset))
        } else {
            null
        }
    }

/** Number of discrete steps spanning a float range (inclusive), e.g. 0.7..2.5 step 0.1 -> 18. */
private fun stepCountOf(min: Float, max: Float, step: Float): Int =
    ((max - min) / step).roundToInt().coerceAtLeast(0)

/** Slider position for a float value: round((value-min)/step), clamped to the range. */
private fun positionOf(value: Float, min: Float, step: Float): Int =
    ((value - min) / step).roundToInt().coerceAtLeast(0)

/** Float value for a slider position: min + i*step, coerced into the range. */
private fun valueOf(position: Int, min: Float, step: Float, max: Float): Float =
    (min + position * step).coerceIn(min, max)

/** Hairline-Trennlinie zwischen den Typo-Sektionen (eink-ui: ≥1.5dp, outlineVariant). */
@Composable
internal fun PanelDivider() {
    HorizontalDivider(
        thickness = EinkTokens.hairline,
        color = MaterialTheme.colorScheme.outlineVariant,
        // Tighter than the old 6dp — the whole panel reads too far apart otherwise (request 2026-06-16).
        modifier = Modifier.padding(vertical = 3.dp),
    )
}

/**
 * A single font-picker row that mirrors [com.komgareader.app.ui.components.ChoiceRow]'s flat
 * Onyx-look (dense selectable row with a checkmark on the right) but renders the label [Text] in
 * [sampleFamily] so the user sees the font's own face. Falls back to the theme default when
 * [sampleFamily] is null (font file not yet loaded or built-in font without a file). No animation
 * — E-Ink invariant holds.
 */
@Composable
private fun FontPickerRow(
    label: String,
    selected: Boolean,
    sampleFamily: FontFamily?,
    accentTint: Color,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = sampleFamily,
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            if (selected) {
                Icon(
                    AppIcons.Check,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = accentTint,
                )
            }
        }
    }
}
