package com.komgareader.domain.usecase

import com.komgareader.domain.model.ReaderPresetOverrides

/**
 * Senke für [applyReaderPreset] — entkoppelt die reine Apply-Reihenfolge von der konkreten
 * SettingsRepository-Verdrahtung (testbar ohne Android). Die Shell (ViewModel) reicht die echten
 * Setter herein.
 */
class ReaderPresetSink(
    val setDisplayMode: (String) -> Unit,
    val setWebtoonOverlapPercent: (Int) -> Unit,
    val setNovelFontSizeEm: (Float) -> Unit,
    val setNovelLineHeight: (Float) -> Unit,
    val setNovelMarginPreset: (String) -> Unit,
    val setNovelFontFamily: (String) -> Unit,
    val setNovelTextAlign: (String) -> Unit,
    val setNovelHyphenationLang: (String) -> Unit,
    val setNovelFontWeight: (Int) -> Unit,
    val setGuidedPanelOverlay: (Boolean) -> Unit,
)

/** Wendet nur die gesetzten (nicht-null) Felder eines Presets über die [sink] an. */
fun applyReaderPreset(o: ReaderPresetOverrides, sink: ReaderPresetSink) {
    o.displayMode?.let(sink.setDisplayMode)
    o.webtoonOverlapPercent?.let(sink.setWebtoonOverlapPercent)
    o.novelFontSizeEm?.let(sink.setNovelFontSizeEm)
    o.novelLineHeight?.let(sink.setNovelLineHeight)
    o.novelMarginPreset?.let(sink.setNovelMarginPreset)
    o.novelFontFamily?.let(sink.setNovelFontFamily)
    o.novelTextAlign?.let(sink.setNovelTextAlign)
    o.novelHyphenationLang?.let(sink.setNovelHyphenationLang)
    o.novelFontWeight?.let(sink.setNovelFontWeight)
    o.guidedPanelOverlay?.let(sink.setGuidedPanelOverlay)
}
