package com.komgareader.domain.render

/**
 * Persistierte Roman-Typografie als reine Primitive (Strings/Floats), wie sie in
 * den App-Settings liegen. Engine- und UI-neutral; [toReflowConfig] ist der reine
 * Mapper auf die [ReflowConfig], die die Reflow-Engine (Naht B) versteht.
 *
 * Die Defaults entsprechen den Settings-Defaults (em 1.0, lineHeight 1.0, Rand
 * NORMAL, Schrift DejaVuSans, Ausrichtung JUSTIFY, Trennung aus).
 */
data class NovelSettings(
    val fontSizeEm: Float = 1.0f,
    val lineHeight: Float = 1.0f,
    val marginPreset: String = MARGIN_NORMAL,
    val fontFamily: String = "DejaVuSans",
    val textAlign: String = "JUSTIFY",
    val hyphenationLang: String = "",
) {
    /** Reiner Mapper: persistierte Primitive → [ReflowConfig] (engine-neutral). */
    fun toReflowConfig(): ReflowConfig = ReflowConfig(
        fontSizeEm = fontSizeEm,
        lineHeight = lineHeight,
        margin = marginFor(marginPreset),
        fontFamily = fontFamily,
        textAlign = if (textAlign == "LEFT") TextAlign.LEFT else TextAlign.JUSTIFY,
        hyphenation = hyphenationLang.ifBlank { null }
            ?.let { Hyphenation.Language(it) }
            ?: Hyphenation.Off,
    )

    companion object {
        const val MARGIN_NARROW = "NARROW"
        const val MARGIN_NORMAL = "NORMAL"
        const val MARGIN_WIDE = "WIDE"

        /** Preset-String → konkrete [Margins]; unbekannt fällt auf NORMAL zurück. */
        fun marginFor(preset: String): Margins = when (preset) {
            MARGIN_NARROW -> Margins(12, 12, 12, 12)
            MARGIN_WIDE -> Margins(48, 48, 48, 48)
            else -> Margins(24, 24, 24, 24)
        }
    }
}
