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
    // 25 statt 24: crengine-ng (propsUpdateDefaults/limitValueList) erlaubt nur gelistete
    // px-Werte und setzt nicht gelistete still auf 8 zurück. 25 ist gelistet, 24 nicht.
    companion object { val NORMAL = Margins(25, 25, 25, 25) }
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
