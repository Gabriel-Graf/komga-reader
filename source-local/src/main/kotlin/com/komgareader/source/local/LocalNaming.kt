package com.komgareader.source.local

import com.komgareader.domain.model.BookFormat

private val BOOK_EXTENSIONS: Map<String, BookFormat> = mapOf(
    "cbz" to BookFormat.CBZ,
    "cbr" to BookFormat.CBR,
    "pdf" to BookFormat.PDF,
    "epub" to BookFormat.EPUB,
)
private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")

private fun extOf(path: String): String =
    path.substringAfterLast('/').substringAfterLast('.', "").lowercase()

fun formatOf(path: String): BookFormat? =
    if (path.endsWith("/")) null else BOOK_EXTENSIONS[extOf(path)]

fun titleOf(path: String): String =
    path.substringAfterLast('/').substringBeforeLast('.')

fun isImageEntry(entryName: String): Boolean =
    !entryName.endsWith("/") && extOf(entryName) in IMAGE_EXTENSIONS

val naturalOrder: Comparator<String> = Comparator { a, b -> compareNatural(a, b) }

private fun compareNatural(a: String, b: String): Int {
    var i = 0; var j = 0
    while (i < a.length && j < b.length) {
        val ca = a[i]; val cb = b[j]
        if (ca.isDigit() && cb.isDigit()) {
            var ei = i; while (ei < a.length && a[ei].isDigit()) ei++
            var ej = j; while (ej < b.length && b[ej].isDigit()) ej++
            val na = a.substring(i, ei).trimStart('0').ifEmpty { "0" }
            val nb = b.substring(j, ej).trimStart('0').ifEmpty { "0" }
            val cmp = if (na.length != nb.length) na.length - nb.length else na.compareTo(nb)
            if (cmp != 0) return cmp
            i = ei; j = ej
        } else {
            val cmp = ca.lowercaseChar().compareTo(cb.lowercaseChar())
            if (cmp != 0) return cmp
            i++; j++
        }
    }
    return (a.length - i) - (b.length - j)
}
