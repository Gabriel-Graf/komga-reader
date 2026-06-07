package com.komgareader.domain.render

/** Textausrichtung im Reflow-Layout. */
enum class TextAlign { LEFT, JUSTIFY }

/** Silbentrennung: aus oder für eine konkrete Sprache (BCP-47, z.B. "de"). */
sealed interface Hyphenation {
    data object Off : Hyphenation
    data class Language(val lang: String) : Hyphenation
}

/** Seitenränder in Pixeln. */
data class Margins(val top: Int, val bottom: Int, val left: Int, val right: Int) {
    companion object { val NORMAL = Margins(24, 24, 24, 24) }
}

/**
 * Typografie-Einstellungen für das Re-Layout eines reflowbaren Dokuments.
 * Engine-neutral: die jeweilige Engine setzt sie in ihr eigenes Layout um.
 */
data class ReflowConfig(
    val fontSizeEm: Float = 1.0f,
    val lineHeight: Float = 1.0f,
    val margin: Margins = Margins.NORMAL,
    val fontFamily: String = "Literata",
    val textAlign: TextAlign = TextAlign.JUSTIFY,
    val hyphenation: Hyphenation = Hyphenation.Off,
) {
    companion object { val DEFAULT = ReflowConfig() }
}
