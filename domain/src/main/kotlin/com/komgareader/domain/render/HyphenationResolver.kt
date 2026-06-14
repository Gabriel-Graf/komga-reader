package com.komgareader.domain.render

/** Hyphenation languages with a real pattern dictionary (mirrors render layer's PATTERN_DICTS). */
private val SUPPORTED_HYPHENATION = setOf("de", "en")

/** Sentinel meaning "derive the language from the document". */
const val HYPHENATION_AUTO = "auto"

/**
 * Resolves the effective hyphenation language. For [HYPHENATION_AUTO], normalizes the document
 * language (e.g. "de-DE" -> "de") and returns it only if a pattern exists, else "" (off).
 * Any explicit value ("", "de", "en") is returned unchanged. Pure.
 */
fun resolveHyphenationLang(setting: String, docLanguage: String): String {
    if (setting != HYPHENATION_AUTO) return setting
    val base = docLanguage.substringBefore('-').lowercase()
    return if (base in SUPPORTED_HYPHENATION) base else ""
}
