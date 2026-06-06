package com.komgareader.render.mupdf

import com.artifex.mupdf.fitz.android.AndroidDrawDevice
import com.komgareader.domain.render.Document
import com.komgareader.domain.render.DocumentFactory
import com.komgareader.domain.render.PageSize
import com.komgareader.domain.render.RenderedPage
import com.artifex.mupdf.fitz.Document as FitzDocument

/**
 * MuPDF-Implementierung der Render-Naht. Hält ein natives Fitz-Dokument; rendert
 * Seiten in eine Android-Bitmap und extrahiert die Pixel in ein [RenderedPage],
 * damit das Domain-Modell Android-frei bleibt. Nicht thread-sicher — pro Lesefluss
 * eine Instanz; [close] gibt die nativen Ressourcen frei.
 */
class MupdfDocument(bytes: ByteArray, formatHint: String) : Document {

    private val doc: FitzDocument = FitzDocument.openDocument(bytes, formatHint)

    override fun pageCount(): Int = doc.countPages()

    override fun pageSize(index: Int): PageSize {
        val page = doc.loadPage(index)
        try {
            val b = page.bounds
            return PageSize(width = (b.x1 - b.x0).toInt(), height = (b.y1 - b.y0).toInt())
        } finally {
            page.destroy()
        }
    }

    override fun renderPage(index: Int, zoom: Float, rotation: Int): RenderedPage {
        val page = doc.loadPage(index)
        try {
            val bitmap = AndroidDrawDevice.drawPage(page, 72f * zoom, rotation)
            try {
                val w = bitmap.width
                val h = bitmap.height
                val pixels = IntArray(w * h)
                bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
                return RenderedPage(width = w, height = h, pixels = pixels)
            } finally {
                bitmap.recycle()
            }
        } finally {
            page.destroy()
        }
    }

    override fun close() = doc.destroy()
}

/** Öffnet MuPDF-Dokumente aus Bytes. */
class MupdfDocumentFactory : DocumentFactory {
    override fun open(bytes: ByteArray, formatHint: String): Document =
        MupdfDocument(bytes, formatHint)
}
