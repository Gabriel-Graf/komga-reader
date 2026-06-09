package com.komgareader.plugin

/** ABI-Gate als zwei Integer (kein semver-String) — Plugin-Plan-Entscheidung 2. */
object PluginAbi {
    const val VERSION = 1
    const val MIN_SUPPORTED = 1
}

/**
 * Deklarative Beschreibung eines Color-Presets (Plugin-Typ c). Reine Daten — kein Code,
 * kein Classloader. Wird beim Import auf die ColorProfile-Wertebereiche geclampt.
 *
 * JSON-Parsing (kotlinx.serialization) liegt bewusst in der `app`-Schicht, damit
 * plugin-api keine Serialisierungs-Abhängigkeit mitschleppt und dünn bleibt.
 */
data class ColorPresetSpec(
    val abiVersion: Int,
    val name: String,
    val saturation: Float,
    val contrast: Float,
    val brightness: Float,
)
