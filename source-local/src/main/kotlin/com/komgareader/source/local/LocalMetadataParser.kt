package com.komgareader.source.local

import java.io.InputStream
import javax.xml.XMLConstants
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

    /** Parse from a UTF-8 string (convenience / tests). */
    fun parse(xml: String): ComicInfoMeta? {
        if (xml.length > MAX_CHARS) return null
        return parse(xml.byteInputStream())
    }

    /** Parse from raw bytes — lets the XML declaration drive the encoding (manga = non-UTF-8). */
    fun parse(bytes: ByteArray): ComicInfoMeta? {
        if (bytes.size > MAX_BYTES) return null
        return parse(bytes.inputStream())
    }

    private fun parse(input: InputStream): ComicInfoMeta? = runCatching {
        val doc = secureFactory().newDocumentBuilder().parse(input)
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

    /** XXE/Billion-Laughs hardened factory: no DOCTYPE, no external entities, secure processing. */
    private fun secureFactory(): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            runCatching {
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            } // not present on all impls — best-effort
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            isXIncludeAware = false
            isExpandEntityReferences = false
            isNamespaceAware = false
        }

    private fun text(root: Element, tag: String): String? =
        root.getElementsByTagName(tag).item(0)?.textContent?.trim()?.ifBlank { null }

    private companion object {
        const val MAX_BYTES = 512 * 1024      // ComicInfo.xml is realistically < a few KB
        const val MAX_CHARS = 512 * 1024
    }
}
