package com.komgareader.source.local

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * Extracts the **embedded cover image** from an EPUB ([epubBytes]) — the same full-bleed artwork a
 * server (Komga) surfaces — by following the OPF: EPUB3 `properties="cover-image"` first, else the
 * EPUB2 `<meta name="cover" content="ID"/>` → manifest item. `null` if no cover is declared.
 *
 * Pure zip + text parsing, **no render engine** — so it belongs here in the renderer-free local
 * source (alongside CBZ cover extraction), reached through the normal `coverBytes` seam. Formats
 * that genuinely need rasterizing a page (PDF/CBR) are rendered in the app layer instead.
 */
fun extractEpubCoverImage(epubBytes: ByteArray): ByteArray? {
    val container = readZipEntry(epubBytes, "META-INF/container.xml")?.decodeToString() ?: return null
    val opfPath = Regex("""full-path="([^"]+)"""").find(container)?.groupValues?.get(1) ?: return null
    val opf = readZipEntry(epubBytes, opfPath)?.decodeToString() ?: return null
    val coverHref = coverHrefFromOpf(opf) ?: return null
    val baseDir = opfPath.substringBeforeLast('/', missingDelimiterValue = "")
    return readZipEntry(epubBytes, resolveZipPath(baseDir, coverHref))
}

/** Resolves the cover image href from OPF text: EPUB3 cover-image property, else EPUB2 meta+item. */
internal fun coverHrefFromOpf(opf: String): String? {
    // EPUB3: <item ... properties="...cover-image..." href="cover.jpg"/> (property order varies).
    Regex("""<item\b[^>]*>""").findAll(opf).forEach { m ->
        val tag = m.value
        if (Regex("""properties="[^"]*\bcover-image\b[^"]*"""").containsMatchIn(tag)) {
            attr(tag, "href")?.let { return decodeHref(it) }
        }
    }
    // EPUB2: <meta name="cover" content="cover-id"/> → <item id="cover-id" href="cover.jpg"/>.
    val coverId = Regex("""<meta\b[^>]*\bname="cover"[^>]*\bcontent="([^"]+)"""").find(opf)?.groupValues?.get(1)
        ?: Regex("""<meta\b[^>]*\bcontent="([^"]+)"[^>]*\bname="cover"""").find(opf)?.groupValues?.get(1)
    if (coverId != null) {
        Regex("""<item\b[^>]*>""").findAll(opf).forEach { m ->
            if (attr(m.value, "id") == coverId) attr(m.value, "href")?.let { return decodeHref(it) }
        }
    }
    return null
}

/** Normalises a zip-relative path (`baseDir` + `href`, collapsing `..`/`.`) to a zip entry name. */
internal fun resolveZipPath(baseDir: String, href: String): String {
    val parts = ArrayDeque<String>()
    if (baseDir.isNotEmpty()) baseDir.split('/').forEach { if (it.isNotEmpty()) parts.addLast(it) }
    href.split('/').forEach { seg ->
        when (seg) {
            "", "." -> {}
            ".." -> if (parts.isNotEmpty()) parts.removeLast()
            else -> parts.addLast(seg)
        }
    }
    return parts.joinToString("/")
}

private fun attr(tag: String, name: String): String? =
    Regex("""\b$name="([^"]*)"""").find(tag)?.groupValues?.get(1)

private fun decodeHref(href: String): String = href.replace("%20", " ")

private fun readZipEntry(zipBytes: ByteArray, name: String): ByteArray? =
    ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
        var e = zis.nextEntry
        while (e != null) {
            if (e.name == name) return zis.readBytes()
            e = zis.nextEntry
        }
        null
    }
