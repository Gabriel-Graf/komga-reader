package com.komgareader.source.opds

import com.komgareader.domain.model.BookFormat

/**
 * Bildet den MIME-Typ aus einem OPDS-Acquisition-Link auf ein [BookFormat] ab.
 * Unbekannte Typen fallen auf CBZ zurück.
 */
fun opdsTypeToFormat(type: String?): BookFormat = when {
    type == null -> BookFormat.CBZ
    type == "application/x-cbz" || type == "application/zip" -> BookFormat.CBZ
    type == "application/x-cbr" || type == "application/x-rar-compressed" -> BookFormat.CBR
    type == "application/pdf" -> BookFormat.PDF
    type == "application/epub+zip" -> BookFormat.EPUB
    else -> BookFormat.CBZ
}
