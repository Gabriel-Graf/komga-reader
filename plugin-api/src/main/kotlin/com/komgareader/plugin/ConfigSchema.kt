package com.komgareader.plugin

/** Generisches Config-Schema, das ein Plugin dem Host deklariert (Plugin-Plan: Settings-per-Plugin-Keim). */
data class ConfigSchema(val fields: List<ConfigField>)

data class ConfigField(
    /** Speicher-Schlüssel in ServerConfig.extras bzw. plugincfg-KV. */
    val key: String,
    /** Vom Plugin geliefert (bereits lokalisiertes) Label. */
    val label: String,
    val type: FieldType,
    val required: Boolean = true,
    val default: String = "",
    /** Nur NUMBER: Slider-Grenzen/Schrittweite. null für andere Typen. */
    val min: Double? = null,
    val max: Double? = null,
    val step: Double? = null,
)

enum class FieldType { TEXT, SECRET, URL, BOOL, NUMBER }
