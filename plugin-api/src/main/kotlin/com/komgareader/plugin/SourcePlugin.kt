package com.komgareader.plugin

import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.source.BrowsableSource

/**
 * Entry-Point eines Quellen-Plugin-APKs (Plugin-Typ a). Der Host instanziiert die im
 * Manifest deklarierte Klasse und ruft [create] mit den vom Nutzer eingegebenen Config-Werten.
 *
 * Classloader-Regel: Das Plugin linkt plugin-api COMPILE-ONLY — diese Klassen kommen zur
 * Laufzeit vom Host-Classloader (Parent), nie aus dem APK (sonst ClassCastException).
 */
interface SourcePlugin {
    val metadata: PluginMetadata
    fun configSchema(): ConfigSchema
    /** Erzeugt die laufende Quelle. Implementierungen dürfen zusätzlich SyncingSource implementieren. */
    fun create(config: Map<String, String>): BrowsableSource
}

data class PluginMetadata(
    val displayName: String,
    val kind: SourceKind = SourceKind.PLUGIN,
)
