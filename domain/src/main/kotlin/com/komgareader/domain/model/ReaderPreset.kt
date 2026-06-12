package com.komgareader.domain.model

/**
 * Ein benannter **Teil-**Snapshot der Reader-Einstellungen (data-only Plugin-Kategorie READER_PRESET).
 * Jedes Feld ist nullable — `null` = vom Preset NICHT gesetzt, beim Anwenden unberührt gelassen.
 * Generische, quellen-/geräte-neutrale Feldnamen (spiegeln die SettingsRepository-Setter).
 */
data class ReaderPresetOverrides(
    val displayMode: String? = null,
    val deviceManagedRefresh: Boolean? = null,
    val webtoonOverlapPercent: Int? = null,
    val novelFontSizeEm: Float? = null,
    val novelLineHeight: Float? = null,
    val novelMarginPreset: String? = null,
    val novelFontFamily: String? = null,
    val novelTextAlign: String? = null,
    val novelHyphenationLang: String? = null,
    val novelFontWeight: Int? = null,
    val guidedPanelOverlay: Boolean? = null,
)

data class ReaderPreset(
    val name: String,
    val abiVersion: Int,
    val overrides: ReaderPresetOverrides,
)
