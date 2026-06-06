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
