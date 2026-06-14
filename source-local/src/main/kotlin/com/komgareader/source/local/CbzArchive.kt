package com.komgareader.source.local

import java.io.File
import java.util.zip.ZipFile

/**
 * Random-access reader over a CBZ (zip) file. Image entries are exposed in
 * natural-sorted order; extraction returns the stored bytes verbatim (no decode).
 */
class CbzArchive(private val file: File) {

    private val imageNames: List<String> by lazy {
        ZipFile(file).use { zip ->
            zip.entries().asSequence()
                .map { it.name }
                .filter { isImageEntry(it) }
                .sortedWith(naturalOrder)
                .toList()
        }
    }

    fun pageCount(): Int = imageNames.size

    fun pageBytes(index: Int): ByteArray {
        val name = imageNames[index]
        return ZipFile(file).use { zip -> zip.getInputStream(zip.getEntry(name)).readBytes() }
    }

    /** First image entry bytes (cover), or empty if none. */
    fun coverBytes(): ByteArray = if (imageNames.isEmpty()) ByteArray(0) else pageBytes(0)

    /** ComicInfo.xml content if present (case-insensitive), else null. */
    fun comicInfoXml(): String? = ZipFile(file).use { zip ->
        val entry = zip.entries().asSequence()
            .firstOrNull { it.name.substringAfterLast('/').equals("ComicInfo.xml", ignoreCase = true) }
            ?: return null
        zip.getInputStream(entry).readBytes().decodeToString()
    }
}
