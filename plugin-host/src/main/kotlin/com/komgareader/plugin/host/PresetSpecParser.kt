package com.komgareader.plugin.host

import com.komgareader.plugin.ColorPresetSpec
import org.json.JSONArray

/**
 * Parst ein JSON-Array von Color-Preset-Einträgen (data-only Plugin-Typ c) in [ColorPresetSpec].
 * Rein — kein Android, kein I/O, kein Classloader; das JSON kommt vom Host als bereits gelesener
 * String. Gibt null zurück, wenn der Top-Level kein Array ist oder das JSON kaputt ist.
 * Einzelne kaputte Einträge (fehlendes Pflichtfeld) werden übersprungen, gültige bleiben.
 *
 * Fehlt einem Eintrag das `abiVersion`-Feld, erbt er die im Manifest deklarierte [manifestAbi].
 * Wert-Validierung (Clamp, ABI-Range, Endlichkeit) macht NICHT dieser Parser, sondern
 * `ColorPresetImporter.toProfileOrNull` beim Import.
 */
fun parsePresetSpecs(json: String, manifestAbi: Int): List<ColorPresetSpec>? {
    val array = runCatching { JSONArray(json) }.getOrNull() ?: return null
    val specs = mutableListOf<ColorPresetSpec>()
    for (i in 0 until array.length()) {
        val obj = array.optJSONObject(i) ?: continue
        val spec = runCatching {
            ColorPresetSpec(
                abiVersion = if (obj.has("abiVersion")) obj.getInt("abiVersion") else manifestAbi,
                name = obj.getString("name"),
                saturation = obj.getDouble("saturation").toFloat(),
                contrast = obj.getDouble("contrast").toFloat(),
                brightness = obj.getDouble("brightness").toFloat(),
            )
        }.getOrNull() ?: continue
        specs.add(spec)
    }
    return specs
}
