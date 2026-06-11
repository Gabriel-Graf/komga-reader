package com.komgareader.plugin.host

import com.komgareader.plugin.ColorPresetSpec

/**
 * Ein installiertes, ABI-kompatibles **data-only** Color-Preset-Plugin (Typ c). Trägt die bereits
 * aus dem Asset geparsten Specs — es wird KEIN Plugin-Code geladen (kein Classloader, kein TOFU).
 */
data class DiscoveredPresetPlugin(
    val packageName: String,
    val displayName: String,
    val abiVersion: Int,
    val presets: List<ColorPresetSpec>,
)
