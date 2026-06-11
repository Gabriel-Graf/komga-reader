package com.komgareader.plugin

/** Generisches Config-Schema, das ein Plugin dem Host deklariert (Plugin-Plan: Settings-per-Plugin-Keim). */
data class ConfigSchema(val fields: List<ConfigField>)

data class ConfigField(
    /** Speicher-Schlüssel in ServerConfig.extras. */
    val key: String,
    /** Vom Plugin geliefertes (bereits lokalisiertes) Label. */
    val label: String,
    val type: FieldType,
    val required: Boolean = true,
    val default: String = "",
)

enum class FieldType { TEXT, SECRET, URL, BOOL }
