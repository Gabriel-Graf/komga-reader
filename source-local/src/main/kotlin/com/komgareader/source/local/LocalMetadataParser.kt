package com.komgareader.source.local

import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** Subset of ComicInfo.xml relevant to the library. All fields optional. */
data class ComicInfoMeta(
    val series: String? = null,
    val number: String? = null,
    val summary: String? = null,
    val genres: List<String> = emptyList(),
    val status: String? = null,
)

class LocalMetadataParser {
    fun parse(xml: String): ComicInfoMeta? = runCatching {
        val doc = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = false }
            .newDocumentBuilder()
            .parse(xml.byteInputStream())
        val root = doc.documentElement ?: return null
        if (!root.nodeName.equals("ComicInfo", ignoreCase = true)) return null
        ComicInfoMeta(
            series = text(root, "Series"),
            number = text(root, "Number"),
            summary = text(root, "Summary"),
            genres = text(root, "Genre")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?: emptyList(),
            status = text(root, "Status"),
        )
    }.getOrNull()

    private fun text(root: Element, tag: String): String? =
        root.getElementsByTagName(tag).item(0)?.textContent?.trim()?.ifBlank { null }
}
