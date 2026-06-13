package com.komgareader.data.plugin

import com.komgareader.domain.model.ReaderPreset
import com.komgareader.domain.model.ReaderPresetOverrides
import org.json.JSONArray
import org.json.JSONObject

/**
 * Parst ein JSON-Array benannter Reader-Preset-Einträge (data-only Plugin-Kategorie READER_PRESET)
 * in [ReaderPreset]. Rein — kein Android/I/O; das JSON kommt vom Host als String. `null` wenn der
 * Top-Level kein Array ist. Einträge ohne `name` werden übersprungen. Im `settings`-Objekt werden
 * unbekannte oder typ-falsche Keys übersprungen (gültige bleiben) → robuste Teil-Snapshots.
 *
 * Fehlt einem Eintrag `abiVersion`, erbt er die im Manifest deklarierte [manifestAbi]. ABI-/Wert-
 * Validierung beim Anwenden ist Sache der UI/des Hosts (Setter clampen selbst).
 */
fun parseReaderPresetSpecs(json: String, manifestAbi: Int): List<ReaderPreset>? {
    val array = runCatching { JSONArray(json) }.getOrNull() ?: return null
    val presets = mutableListOf<ReaderPreset>()
    for (i in 0 until array.length()) {
        val obj = array.optJSONObject(i) ?: continue
        val name = obj.optString("name").takeIf { it.isNotBlank() } ?: continue
        val abi = if (obj.has("abiVersion")) obj.optInt("abiVersion", manifestAbi) else manifestAbi
        val settings = obj.optJSONObject("settings") ?: JSONObject()
        presets.add(ReaderPreset(name, abi, settings.toOverrides()))
    }
    return presets
}

private fun JSONObject.toOverrides(): ReaderPresetOverrides = ReaderPresetOverrides(
    displayMode = optStringOrNull("displayMode"),
    webtoonOverlapPercent = optIntOrNull("webtoonOverlapPercent"),
    novelFontSizeEm = optFloatOrNull("novelFontSizeEm"),
    novelLineHeight = optFloatOrNull("novelLineHeight"),
    novelMarginPreset = optStringOrNull("novelMarginPreset"),
    novelFontFamily = optStringOrNull("novelFontFamily"),
    novelTextAlign = optStringOrNull("novelTextAlign"),
    novelHyphenationLang = optStringOrNull("novelHyphenationLang"),
    novelFontWeight = optIntOrNull("novelFontWeight"),
    guidedPanelOverlay = optBoolOrNull("guidedPanelOverlay"),
)

private fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && get(key) is String) getString(key).takeIf { it.isNotBlank() } else null
private fun JSONObject.optBoolOrNull(key: String): Boolean? =
    if (has(key) && get(key) is Boolean) getBoolean(key) else null
private fun JSONObject.optIntOrNull(key: String): Int? =
    if (has(key) && get(key) is Number) getInt(key) else null
private fun JSONObject.optFloatOrNull(key: String): Float? =
    if (has(key) && get(key) is Number) getDouble(key).toFloat() else null
