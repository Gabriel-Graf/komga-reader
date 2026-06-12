package com.komgareader.plugin.host

import com.komgareader.plugin.PluginCategory

/**
 * Reine Ableitung der data-only Plugin-Identität aus den Manifest-Metadaten. Kein Android,
 * kein I/O — der Host reicht die drei Roh-Werte herein.
 *
 * - Explizites [PluginManifestKeys.DATA_CATEGORY] + [PluginManifestKeys.DATA_ASSET] hat Vorrang.
 * - Legacy: nur [PluginManifestKeys.COLOR_PRESETS] vorhanden → ([PluginCategory.COLOR_PRESET], dessen Wert).
 *   So bleiben alte Color-Preset-APKs ohne Neubau ladbar.
 *
 * Gibt `null`, wenn keine data-only Deklaration vorliegt, das Asset fehlt oder die Kategorie
 * unbekannt ist.
 */
fun resolveDataPluginManifest(
    dataCategory: String?,
    dataAsset: String?,
    legacyColorPresets: String?,
): Pair<PluginCategory, String>? {
    if (!dataCategory.isNullOrBlank()) {
        val asset = dataAsset?.takeIf { it.isNotBlank() } ?: return null
        val category = runCatching { PluginCategory.valueOf(dataCategory.trim()) }.getOrNull() ?: return null
        return category to asset
    }
    val legacy = legacyColorPresets?.takeIf { it.isNotBlank() } ?: return null
    return PluginCategory.COLOR_PRESET to legacy
}
