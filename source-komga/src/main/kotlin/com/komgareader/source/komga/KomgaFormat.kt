package com.komgareader.source.komga

import com.komgareader.domain.model.BookFormat

/**
 * Bildet Komgas `mediaType` auf das interne [BookFormat] ab. CBR meldet je nach
 * RAR-Version Suffixe (`; version=5`), daher Präfix-Match. Unbekanntes wird als
 * CBZ behandelt (häufigster Bild-Container).
 */
fun mediaTypeToFormat(mediaType: String): BookFormat = when {
    mediaType.startsWith("application/x-rar-compressed") -> BookFormat.CBR
    mediaType.startsWith("application/pdf") -> BookFormat.PDF
    mediaType.startsWith("application/epub+zip") -> BookFormat.EPUB
    else -> BookFormat.CBZ
}
