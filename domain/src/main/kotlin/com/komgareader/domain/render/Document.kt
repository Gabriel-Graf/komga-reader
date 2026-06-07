package com.komgareader.domain.render

/** Pixelmaße einer Seite in nativer Auflösung. */
data class PageSize(val width: Int, val height: Int)

/**
 * Ein gerendertes Seitenbild: rohe ARGB_8888-Pixel + Maße. Bewusst Android-frei
 * (kein android.graphics.Bitmap), damit das Domain-Modul rein bleibt. Der
 * MuPDF-Wrapper in :render-core (Plan 2) füllt dies und konvertiert zu Bitmap.
 */
data class RenderedPage(val width: Int, val height: Int, val pixels: IntArray) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is RenderedPage &&
            width == other.width && height == other.height && pixels.contentEquals(other.pixels))

    override fun hashCode(): Int =
        (width * 31 + height) * 31 + pixels.contentHashCode()
}

/**
 * Naht B: gemeinsame Render-Abstraktion über alle Formate. MuPDF deckt
 * cbz/cbr/pdf und EPUB-Reflow ab; eine alternative Engine (z.B. crengine für
 * EPUB) kann später dahinter treten, ohne Reader/UI zu berühren.
 */
interface Document : AutoCloseable {
    fun pageCount(): Int
    fun pageSize(index: Int): PageSize
    /** Rendert Seite [index] skaliert um [zoom], rotiert um [rotation]° (0/90/180/270). */
    fun renderPage(index: Int, zoom: Float, rotation: Int): RenderedPage
}

/** Öffnet ein Dokument aus rohen Bytes. [formatHint] = Dateiendung (z.B. ".cbz"). */
interface DocumentFactory {
    fun open(bytes: ByteArray, formatHint: String): Document
}

/** Eintrag im Inhaltsverzeichnis: Titel, stabiler Anker, Verschachtelungstiefe (0 = oberste Ebene). */
data class Chapter(val title: String, val anchor: String, val depth: Int)

/** Treffer der Volltextsuche: stabiler Anker zur Fundstelle + Kontext-Schnipsel. */
data class SearchHit(val anchor: String, val snippet: String)

/**
 * Reflowbares Dokument (Roman): Re-Layout, TOC, stabile Anker, Suche. Engine-neutral,
 * sodass die konkrete EPUB-Engine später dahinter treten kann, ohne Reader/UI zu berühren.
 */
interface ReflowableDocument : Document {
    /** Wendet neue Typografie an und schichtet das Layout um. */
    fun applyLayout(cfg: ReflowConfig)
    /** Inhaltsverzeichnis als flache, tiefenmarkierte Liste. */
    fun chapters(): List<Chapter>
    /** Anker der aktuell sichtbaren Stelle — stabil über Re-Layouts hinweg. */
    fun currentAnchor(): String
    /** Springt zur durch [anchor] bezeichneten Stelle. */
    fun seekToAnchor(anchor: String)
    /** Springt an die relative Position [fraction] (0.0 = Anfang, 1.0 = Ende). */
    fun seekToProgress(fraction: Float)
    /** Sucht [query] im Volltext und liefert die Treffer in Lesereihenfolge. */
    fun search(query: String): List<SearchHit>
}
