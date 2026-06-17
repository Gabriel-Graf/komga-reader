package com.komgareader.plugin.host

import com.komgareader.plugin.PluginCategory

/**
 * Ein installiertes, ABI-kompatibles **data-only** Plugin beliebiger Kategorie. Trägt den bereits
 * gelesenen Asset-JSON-String — KEIN Plugin-Code wird geladen (Asset via `createPackageContext(pkg, 0)`,
 * Flags 0 = nur Ressourcen). Die kategorie-spezifische Interpretation (Parsen/Clampen) passiert
 * darüber (z.B. [parsePresetSpecs] für [PluginCategory.COLOR_PRESET]).
 *
 * Kein `signatureSha256`: data-only Plugins führen nie Code aus → kein TOFU/Signatur-Pinning nötig.
 */
data class DiscoveredDataPlugin(
    val packageName: String,
    val category: PluginCategory,
    val abiVersion: Int,
    val assetName: String,
    val displayName: String,
    val assetJson: String,
    /** SPDX license identifier from the plugin manifest (empty when not declared). */
    val license: String = "",
    /** Android versionCode of the plugin APK; 0 when unavailable. */
    val versionCode: Long = 0,
)

/**
 * Metadaten eines installierten, ABI-kompatiblen data-only Plugins OHNE dass das Asset gelesen wird.
 * Für Kategorien mit großen Binär-Assets (z.B. PANEL_MODEL/ONNX, mehrere MB): Listen/UI brauchen nur
 * Identität + ABI + Asset-Name, nicht die Bytes. Bytes erst über [PluginHost.binaryDataPluginBytes].
 */
data class DataPluginInfo(
    val packageName: String,
    val category: PluginCategory,
    val abiVersion: Int,
    val assetName: String,
    val displayName: String,
    /** Asset-Name des optionalen Config-Schemas (DATA_CONFIG), null wenn das Plugin keins deklariert. */
    val configAssetName: String? = null,
)
