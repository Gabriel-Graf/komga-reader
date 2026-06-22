package com.komgareader.plugin

/** ABI gate: two integers (not a semver string) — plugin plan decision 2.
 *  VERSION 4 added the NUMBER FieldType + data-only plugin config.
 *
 *  CAVEAT — the integer gate cannot catch a JVM signature break. It only compares a declared
 *  ABI_VERSION against [MIN_SUPPORTED]..[VERSION]; a plugin that passes the gate but was compiled
 *  against an incompatible class signature still crashes when instantiated. The VERSION 4 change to
 *  ConfigField was such a break (a constructor parameter was added), which a plain "additive" claim
 *  hid. It is healed source-side by the explicit legacy constructor on [ConfigField] (binary compat
 *  restored), so [MIN_SUPPORTED] stays 1 and existing v1 source/preset plugins keep loading. The
 *  rule going forward: keep the SDK binary-compatible (compat overloads), because raising
 *  MIN_SUPPORTED can only lock old plugins out, never make a broken one run. The host also isolates
 *  per-plugin load failures (PluginHost.discoverPlugins) so one incompatible plugin never hides the
 *  rest. */
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
