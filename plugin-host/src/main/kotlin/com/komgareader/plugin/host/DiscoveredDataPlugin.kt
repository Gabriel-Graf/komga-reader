package com.komgareader.plugin.host

import com.komgareader.plugin.PluginCategory

/**
 * An installed, ABI-compatible **data-only** plugin of any category. Carries the already-read
 * asset JSON string — NO plugin code is loaded (asset via `createPackageContext(pkg, 0)`,
 * flags 0 = resources only). Category-specific interpretation (parsing/clamping) happens above
 * (e.g. [parsePresetSpecs] for [PluginCategory.COLOR_PRESET]).
 *
 * No `signatureSha256`: data-only plugins never execute code → no TOFU/signature pinning needed.
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
 * Metadata of an installed, ABI-compatible data-only plugin WITHOUT reading the asset.
 * For categories with large binary assets (e.g. PANEL_MODEL/ONNX, multiple MB): listings/UI only
 * need identity + ABI + asset name, not the bytes. Bytes are read lazily via [PluginHost.binaryDataPluginBytes].
 */
data class DataPluginInfo(
    val packageName: String,
    val category: PluginCategory,
    val abiVersion: Int,
    val assetName: String,
    val displayName: String,
    /** Asset name of the optional config schema (DATA_CONFIG), null when the plugin declares none. */
    val configAssetName: String? = null,
)
