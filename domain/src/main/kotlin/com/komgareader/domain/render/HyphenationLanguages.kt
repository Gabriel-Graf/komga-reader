package com.komgareader.domain.render

/**
 * Canonical set of hyphenation language codes the app supports — the single source of truth
 * shared by [resolveHyphenationLang] (domain) and the render layer's pattern-file map
 * (ReflowCss.PATTERN_DICTS, keyed by exactly these codes; a render parity test guards the match).
 * Each code has a bundled crengine-ng `.pattern` dictionary. Base BCP-47 codes only.
 */
object HyphenationLanguages {
    val SUPPORTED: List<String> = listOf(
        "ar", "bg", "bn", "cs", "da", "de", "el", "en", "es", "fa", "fi", "fr",
        "gu", "hu", "it", "mr", "nl", "pa", "pl", "pt", "ru", "ta", "te", "uk",
    )
}

/** Which of the three hyphenation UI states a stored setting value represents. */
enum class HyphenationMode { AUTO, OFF, LANGUAGE }

/** Pure mapping of the stored setting value to its UI mode. */
fun hyphenationModeOf(value: String): HyphenationMode = when (value) {
    HYPHENATION_AUTO -> HyphenationMode.AUTO
    "" -> HyphenationMode.OFF
    else -> HyphenationMode.LANGUAGE
}
