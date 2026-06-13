package com.komgareader.data.plugin

import com.komgareader.domain.model.ColorRolesSpec
import com.komgareader.domain.model.ThemeSpec
import com.komgareader.domain.model.TypoSpec
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
    // optInt(key) gäbe bei einem nicht-parsebaren String (z. B. "vier") still 0 zurück → falscher
    // Eckradius ohne Nutzerabsicht. Sentinel-Default trennt „fehlt/unparsebar" sauber von einer echten 0.
    val cornerRadiusDp = theme?.optInt("cornerRadius", Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE }

    val themeSpec = theme?.let { t ->
        fun roles(key: String): ColorRolesSpec? = t.optJSONObject(key)?.let { o ->
            ColorRolesSpec(
                background = o.optString("background").takeIf { it.isNotBlank() },
                surface = o.optString("surface").takeIf { it.isNotBlank() },
                navDock = o.optString("navDock").takeIf { it.isNotBlank() },
                accent = o.optString("accent").takeIf { it.isNotBlank() },
                onAccent = o.optString("onAccent").takeIf { it.isNotBlank() },
                onBackground = o.optString("onBackground").takeIf { it.isNotBlank() },
                onSurfaceVariant = o.optString("onSurfaceVariant").takeIf { it.isNotBlank() },
                outline = o.optString("outline").takeIf { it.isNotBlank() },
            )
        }
        val light = roles("light")
        val dark = roles("dark")
        if (light == null && dark == null) {
            null
        } else {
            val typo = t.optJSONObject("typography")?.let { p ->
                TypoSpec(
                    headlineWeight = p.optInt("headlineWeight", Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE },
                    titleWeight = p.optInt("titleWeight", Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE },
                    // .isFinite() verwirft NaN/Infinity aus einem unparsebaren Wert (org.json gibt dann NaN) —
                    // konsistent mit dem Sentinel-Schutz oben, kein NaN-Tracking ins Rendering.
                    headlineTrackingEm = if (p.has("headlineTrackingEm")) p.optDouble("headlineTrackingEm").toFloat().takeIf { it.isFinite() } else null,
                )
            }
            ThemeSpec(
                light = light,
                dark = dark,
                cornerRadiusDp = cornerRadiusDp,
                elevation = if (t.has("elevation")) t.optBoolean("elevation") else null,
                typography = typo,
            )
        }
    }

    return UiPackSpec(
        packageName = packageName,
        displayName = displayName,
        abiVersion = abi,
        navStyle = navStyle,
        iconRemap = iconRemap,
        accentHex = accentHex,
        cornerRadiusDp = cornerRadiusDp,
        theme = themeSpec,
    )
}
