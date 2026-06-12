package com.komgareader.data.plugin

import com.komgareader.domain.model.UiPackSpec
import com.komgareader.plugin.PluginAbi
import org.json.JSONObject

/**
 * Parst das `ui_pack.json`-Asset eines UI-Pack-Plugins (data-only Kategorie UI_PACK) in [UiPackSpec].
 * Rein — kein Android/I/O; das JSON kommt vom Host als String. Analog [parseLanguageSpec]/
 * [parseReaderPresetSpecs] (`org.json`); `data` braucht **keinen** neuen Modul-Dep (nur `domain` +
 * `org.json` + `plugin-api` für das ABI-Gate).
 *
 * - `null`, wenn das JSON kaputt ist (kein Top-Level-Objekt) **oder** die ABI-Version außerhalb der
 *   unterstützten Spanne ([PluginAbi.MIN_SUPPORTED]..[PluginAbi.VERSION]) liegt.
 * - Fehlt `abiVersion` im JSON, erbt es die im Manifest deklarierte [manifestAbi].
 * - Alle drei Sektionen (shell/icons/theme) sind optional und tolerant: fehlend → null/leer. Der
 *   Aufrufer filtert wirkungslose Packs über [UiPackSpec.hasAnyOverride].
 * - Ungültige Einträge in der Icon-Remap-Map (Nicht-String-Werte) werden übersprungen; die Gültigkeit
 *   der IconKey-Namen prüft erst der app-seitige Konverter (`UiPackSpec.toIconPack`).
 */
fun parseUiPackSpec(
    json: String,
    packageName: String,
    displayName: String,
    manifestAbi: Int,
): UiPackSpec? {
    val obj = runCatching { JSONObject(json) }.getOrNull() ?: return null
    val abi = if (obj.has("abiVersion")) obj.optInt("abiVersion", manifestAbi) else manifestAbi
    if (abi !in PluginAbi.MIN_SUPPORTED..PluginAbi.VERSION) return null

    val shell = obj.optJSONObject("shell")
    val navStyle = shell?.optString("navStyle")?.takeIf { it.isNotBlank() }

    val iconsObj = obj.optJSONObject("icons")
    val iconRemap = buildMap {
        iconsObj?.keys()?.forEach { key ->
            val value = iconsObj.get(key)
            if (value is String && value.isNotBlank()) put(key, value)
        }
    }

    val theme = obj.optJSONObject("theme")
    val accentHex = theme?.optString("accent")?.takeIf { it.isNotBlank() }
    val cornerRadiusDp = theme?.takeIf { it.has("cornerRadius") }?.optInt("cornerRadius")

    return UiPackSpec(
        packageName = packageName,
        displayName = displayName,
        abiVersion = abi,
        navStyle = navStyle,
        iconRemap = iconRemap,
        accentHex = accentHex,
        cornerRadiusDp = cornerRadiusDp,
    )
}
