package com.komgareader.data.plugin

import com.komgareader.domain.render.FontSpec
import org.json.JSONArray

/**
 * Parses a data-only FONT plugin's JSON asset (a JSONArray of font entries) into [FontSpec]s.
 * Returns null when the top-level JSON is not an array. Entries missing `family` or `asset`
 * are skipped; `label` falls back to `family`. Mirrors [parseReaderPresetSpecs]. [manifestAbi]
 * is carried for signature symmetry with the sibling parsers (not further evaluated in P2).
 */
fun parseFontSpecs(json: String, manifestAbi: Int): List<FontSpec>? {
    val array = runCatching { JSONArray(json) }.getOrNull() ?: return null
    val out = mutableListOf<FontSpec>()
    for (i in 0 until array.length()) {
        val obj = array.optJSONObject(i) ?: continue
        val family = obj.optString("family").takeIf { it.isNotBlank() } ?: continue
        val asset = obj.optString("asset").takeIf { it.isNotBlank() } ?: continue
        val label = obj.optString("label").takeIf { it.isNotBlank() } ?: family
        val license = obj.optString("license")
        out.add(FontSpec(family = family, label = label, asset = asset, license = license))
    }
    return out
}
