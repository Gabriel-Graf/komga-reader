package com.komgareader.plugin

/**
 * Kategorien für **data-only** Plugins (kein Code, nur JSON-Asset). Code-Quellen-Plugins
 * laufen über [com.komgareader.plugin.host.PluginManifestKeys.ENTRY_CLASS] und haben hier
 * KEINEN Eintrag. Neue Kategorie hinzufügen = additiv, kein ABI-Bruch.
 */
enum class PluginCategory { COLOR_PRESET, READER_PRESET, LANGUAGE, UI_PACK, PANEL_MODEL }
