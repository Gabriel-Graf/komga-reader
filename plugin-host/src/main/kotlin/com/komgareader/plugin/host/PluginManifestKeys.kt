package com.komgareader.plugin.host

/** Manifest-Metadata-Schlüssel, die ein Plugin-APK deklariert. */
object PluginManifestKeys {
    const val ENTRY_CLASS = "com.komgareader.plugin.SOURCE"
    const val ABI_VERSION = "com.komgareader.plugin.ABI_VERSION"

    /**
     * Data-only Color-Preset-Plugin (Typ c): Wert = Asset-Dateiname (relativ zu `assets/`)
     * einer JSON-Liste von ColorPresetSpec. Anwesenheit dieses Keys → Preset-Plugin, nicht
     * Quellen-Plugin (das nennt [ENTRY_CLASS]). Der Host liest das Asset OHNE Plugin-Code.
     */
    const val COLOR_PRESETS = "com.komgareader.plugin.COLOR_PRESETS"

    /**
     * Generischer data-only Plugin-Kategorie-Key. Wert = einer von
     * [com.komgareader.plugin.PluginCategory] (`COLOR_PRESET`|`READER_PRESET`|`LANGUAGE`).
     * Zusammen mit [DATA_ASSET] der Nachfolger des kategorie-spezifischen [COLOR_PRESETS].
     */
    const val DATA_CATEGORY = "com.komgareader.plugin.DATA_CATEGORY"

    /** Asset-Dateiname (relativ zu `assets/`) der data-only JSON-Nutzlast. */
    const val DATA_ASSET = "com.komgareader.plugin.DATA_ASSET"
}
