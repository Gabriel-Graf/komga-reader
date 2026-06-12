package com.komgareader.app.i18n

import com.komgareader.data.plugin.LanguageSpec

/**
 * Löst den gespeicherten Sprach-Code in die aktive [Strings] auf. Built-in `de`/`en` zuerst; sonst ein
 * installiertes Sprach-Plugin mit diesem Code → [MapBackedStrings] mit EN-Fallback; sonst [StringsEn]
 * (sicherer Default, nie null). Spiegelt das StubSource-/Default-Prinzip der data-only Nähte.
 */
fun resolveStrings(code: String, installed: List<LanguageSpec>): Strings = when (code) {
    "de" -> StringsDe
    "en" -> StringsEn
    else -> installed.firstOrNull { it.code == code }
        ?.let { MapBackedStrings(it.strings, StringsEn) }
        ?: StringsEn
}
