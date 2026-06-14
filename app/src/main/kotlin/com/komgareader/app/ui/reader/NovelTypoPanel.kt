package com.komgareader.app.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.EinkInfoDialog
import com.komgareader.domain.render.Hyphenation
import com.komgareader.domain.render.NovelFont
import com.komgareader.domain.render.NovelFonts
import com.komgareader.domain.render.NovelSettings
import com.komgareader.domain.render.ReflowConfig
import com.komgareader.domain.render.TextAlign
import java.io.File

/**
 * Roman-Typografie-Panel im Onyx-Look ([EinkInfoDialog]). Jede Änderung schreibt in die
 * globalen Settings; der [NovelReaderViewModel] schichtet das Dokument **live** um und
 * hält die Leseposition (Anker) — daher kein Bestätigen/Abbrechen.
 *
 * Der Panel ist nur noch der Dialog-Rahmen um die geteilte, stateless
 * [NovelTypographyControls] — dieselbe Komponente steckt auch in den Haupt-Settings, beide
 * schreiben gegen denselben `SettingsRepository` (DRY). Der hier hereingereichte
 * [ReflowConfig] (bereits gemappt) wird auf die persistierten Primitive zurückübersetzt,
 * mit denen die Controls arbeiten.
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
    availableFonts: List<NovelFont> = NovelFonts.ALL,
    fontFiles: Map<String, File> = emptyMap(),
) {
    val strings = LocalStrings.current

    EinkInfoDialog(
        title = strings.novelTypography,
        onDismiss = onDismiss,
        closeLabel = strings.close,
        contentSpacing = 4.dp,
    ) {
        NovelTypographyControls(
            fontSizeEm = config.fontSizeEm,
            onFontSize = onFontSizeEm,
            lineHeight = config.lineHeight,
            onLineHeight = onLineHeight,
            fontWeight = config.fontWeight,
            onFontWeight = onFontWeight,
            marginPreset = config.marginPreset(),
            onMargin = onMargin,
            textAlign = if (config.textAlign == TextAlign.LEFT) "LEFT" else "JUSTIFY",
            onTextAlign = onTextAlign,
            hyphenationLang = config.hyphenationLang(),
            onHyphenation = onHyphenation,
            fontFamily = config.fontFamily,
            onFontFamily = onFontFamily,
            availableFonts = availableFonts,
            fontFiles = fontFiles,
        )
    }
}

/** Reverse-Map: konkrete [ReflowConfig]-Ränder → Preset-String, den die Controls erwarten. */
private fun ReflowConfig.marginPreset(): String = when (margin) {
    NovelSettings.marginFor(NovelSettings.MARGIN_NARROW) -> NovelSettings.MARGIN_NARROW
    NovelSettings.marginFor(NovelSettings.MARGIN_WIDE) -> NovelSettings.MARGIN_WIDE
    else -> NovelSettings.MARGIN_NORMAL
}

/** Reverse-Map: [Hyphenation] → Sprachcode-String ("" = aus), den die Controls erwarten. */
private fun ReflowConfig.hyphenationLang(): String = when (val h = hyphenation) {
    is Hyphenation.Language -> h.lang
    Hyphenation.Off -> ""
}
