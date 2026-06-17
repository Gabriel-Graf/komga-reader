package com.komgareader.plugin

/** ABI-Gate als zwei Integer (kein semver-String) — Plugin-Plan-Entscheidung 2.
 *  VERSION 4 = additive Erweiterung (NUMBER-FieldType + data-only Plugin-Config).
 *  MIN_SUPPORTED bleibt 1: color-preset-v1-APKs laden unverändert weiter. */
object PluginAbi {
    const val VERSION = 4
    const val MIN_SUPPORTED = 1
}

/**
 * Deklarative Beschreibung eines Color-Presets (Plugin-Typ c). Reine Daten — kein Code,
 * kein Classloader. Wird beim Import auf die ColorProfile-Wertebereiche geclampt.
 *
 * JSON-Parsing (org.json) liegt im `plugin-host` (`parsePresetSpecs`), damit plugin-api
 * keine Serialisierungs-Abhängigkeit mitschleppt und dünn bleibt.
 */
data class ColorPresetSpec(
    val abiVersion: Int,
    val name: String,
    val saturation: Float,
    val contrast: Float,
    val brightness: Float,
)
