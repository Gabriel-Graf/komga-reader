package com.komgareader.plugin

/** Generic config schema declared by a plugin to the host (plugin plan: per-plugin settings seed). */
data class ConfigSchema(val fields: List<ConfigField>)

data class ConfigField(
    /** Storage key in ServerConfig.extras or the plugincfg KV store. */
    val key: String,
    /** Label supplied by the plugin (already localised). */
    val label: String,
    val type: FieldType,
    val required: Boolean = true,
    val default: String = "",
    /** NUMBER only: slider bounds and step size. null for other types. */
    val min: Double? = null,
    val max: Double? = null,
    val step: Double? = null,
)

enum class FieldType { TEXT, SECRET, URL, BOOL, NUMBER }
