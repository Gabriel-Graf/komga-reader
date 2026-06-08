package com.komgareader.domain.render

/**
 * Eine gebündelte Lese-Schrift als engine-neutrale Beschreibung. Single Source of
 * Truth für die UI (Auswahlliste + Anzeigename) **und** für die Engine-Anbindung
 * (welche Asset-Datei registriert wird, unter welchem Familiennamen sie wählbar
 * ist).
 *
 * Wichtig: [family] ist der **registrierte FreeType-Familienname** der TTF (z. B.
 * `"DejaVu Sans"` mit Leerzeichen, nicht `"DejaVuSans"`). Genau dieser String wird
 * in den Settings persistiert und als `font.face.default` an crengine-ng gereicht;
 * crengine-ng wählt die Schrift per exaktem Familiennamen-Abgleich. [label] ist der
 * (verbatim anzeigbare) Name in der Auswahl, [asset] der Pfad im `assets/`-Baum.
 */
data class NovelFont(
    val family: String,
    val label: String,
    val asset: String,
)

/**
 * Registry der gebündelten Lese-Schriften (OFL/permissiv, alle DE-fähig). Reihenfolge
 * = Reihenfolge in der Auswahl. Der erste Eintrag ist der Default ([DEFAULT]).
 *
 * - **DejaVu Sans** (Sans, Bitstream-Vera/Public-Domain) — gut lesbarer Grotesk.
 * - **Literata** (Serif, OFL-1.1) — für Langform-Lesen entworfene Antiqua.
 * - **Bitter** (Slab-Serif, OFL-1.1) — kräftige Slab-Serife, E-Ink-freundlich.
 *
 * Provenance/Lizenzen: `render-crengine/native/PROVENANCE.md`, `NOTICE`.
 */
object NovelFonts {
    val ALL: List<NovelFont> = listOf(
        NovelFont(family = "DejaVu Sans", label = "DejaVu Sans", asset = "fonts/DejaVuSans.ttf"),
        NovelFont(family = "Literata", label = "Literata", asset = "fonts/Literata.ttf"),
        NovelFont(family = "Bitter", label = "Bitter", asset = "fonts/Bitter.ttf"),
    )

    /** Default-Schriftfamilie (registrierter Familienname der ersten Schrift). */
    val DEFAULT: String = ALL.first().family

    /** Liefert die [NovelFont] zum registrierten Familiennamen oder die Default-Schrift. */
    fun byFamily(family: String): NovelFont =
        ALL.firstOrNull { it.family == family } ?: ALL.first()
}
