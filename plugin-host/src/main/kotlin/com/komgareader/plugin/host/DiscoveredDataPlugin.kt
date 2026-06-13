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
