package com.komgareader.data.plugin

import com.komgareader.plugin.ConfigField
import com.komgareader.plugin.ConfigSchema
import com.komgareader.plugin.FieldType
import org.json.JSONObject

/**
 * Pure parser for a data-only plugin config schema (org.json, analogous to [parsePresetSpecs]).
 * Unknown/invalid field types are skipped; `min/max/step` are only read for NUMBER fields.
 * Returns null for unparseable JSON.
 */
fun parseConfigSchema(json: String): ConfigSchema? {
    val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
    val arr = root.optJSONArray("fields") ?: return ConfigSchema(emptyList())
    val fields = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val type = runCatching { FieldType.valueOf(o.optString("type")) }.getOrNull() ?: continue
            val key = o.optString("key")
            if (key.isEmpty()) continue
            add(
                ConfigField(
                    key = key,
                    label = o.optString("label", key),
                    type = type,
                    required = o.optBoolean("required", false),
                    default = o.optString("default", ""),
                    min = if (o.has("min")) o.optDouble("min") else null,
                    max = if (o.has("max")) o.optDouble("max") else null,
                    step = if (o.has("step")) o.optDouble("step") else null,
                ),
            )
        }
    }
    return ConfigSchema(fields)
}
