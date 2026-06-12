package com.komgareader.data.plugin

import org.json.JSONObject

/** Eine entdeckte Sprach-Nutzlast (data-only Plugin-Kategorie LANGUAGE). */
data class LanguageSpec(
    val code: String,
    val name: String,
    val abiVersion: Int,
    val strings: Map<String, String>,
)

/**
 * Parst ein einzelnes Sprach-JSON-Objekt in [LanguageSpec]. Rein — kein Android/I/O. `null`, wenn
 * der Top-Level kein Objekt ist oder `code`/`name`/`strings` fehlen. Nicht-String-Werte im
 * `strings`-Objekt werden übersprungen. Fehlt `abiVersion`, erbt es [manifestAbi].
 */
fun parseLanguageSpec(json: String, manifestAbi: Int): LanguageSpec? {
    val obj = runCatching { JSONObject(json) }.getOrNull() ?: return null
    val code = obj.optString("code").takeIf { it.isNotBlank() } ?: return null
    val name = obj.optString("name").takeIf { it.isNotBlank() } ?: return null
    val stringsObj = obj.optJSONObject("strings") ?: return null
    val abi = if (obj.has("abiVersion")) obj.optInt("abiVersion", manifestAbi) else manifestAbi
    val strings = buildMap {
        stringsObj.keys().forEach { key ->
            if (stringsObj.get(key) is String) put(key, stringsObj.getString(key))
        }
    }
    return LanguageSpec(code, name, abi, strings)
}
