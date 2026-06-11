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
}
