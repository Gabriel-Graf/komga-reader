package com.komgareader.domain.usecase

import com.komgareader.domain.model.BookFormat

/**
 * Map an Android VIEW intent's MIME type and/or file name to a [BookFormat], or null
 * if it is not a supported book. MIME is authoritative when specific; the common
 * generic MIME `application/octet-stream` (and a null MIME) fall back to the file
 * name extension. CBZ/CBR are frequently delivered as zip/rar/octet-stream, so both
 * MIME and extension are accepted.
 */
fun detectBookFormat(mime: String?, fileName: String?): BookFormat? {
    fromMime(mime)?.let { return it }
    return fromExtension(fileName)
}

private fun fromMime(mime: String?): BookFormat? = when (mime?.lowercase()?.substringBefore(';')?.trim()) {
    "application/epub+zip" -> BookFormat.EPUB
    "application/pdf" -> BookFormat.PDF
    "application/zip", "application/x-cbz", "application/vnd.comicbook+zip" -> BookFormat.CBZ
    "application/x-cbr", "application/x-rar", "application/x-rar-compressed",
    "application/vnd.comicbook-rar", "application/vnd.rar" -> BookFormat.CBR
    else -> null
}

private fun fromExtension(fileName: String?): BookFormat? = when (fileName?.substringAfterLast('.', "")?.lowercase()) {
    "epub" -> BookFormat.EPUB
    "pdf" -> BookFormat.PDF
    "cbz" -> BookFormat.CBZ
    "cbr" -> BookFormat.CBR
    else -> null
}
