package com.komgareader.source.opds

import org.w3c.dom.Element
import org.w3c.dom.NodeList
import javax.xml.parsers.DocumentBuilderFactory

private const val ATOM_NS = "http://www.w3.org/2005/Atom"
private const val REL_THUMBNAIL = "http://opds-spec.org/image/thumbnail"
private const val REL_IMAGE = "http://opds-spec.org/image"
private const val REL_ACQUISITION = "http://opds-spec.org/acquisition"

/**
 * Parst einen OPDS-Atom-Feed (XML-String) und liefert die enthaltenen Einträge.
 * Verwendet namespace-aware DOM-Parsing (JVM built-in).
 */
class OpdsFeedParser {

    fun parse(xml: String): List<OpdsEntry> {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val doc = factory.newDocumentBuilder().parse(xml.byteInputStream())
        val entryNodes: NodeList = doc.getElementsByTagNameNS(ATOM_NS, "entry")
        return (0 until entryNodes.length).map { i ->
            parseEntry(entryNodes.item(i) as Element)
        }
    }

    private fun parseEntry(entry: Element): OpdsEntry {
        val id = entry.getElementsByTagNameNS(ATOM_NS, "id").item(0)?.textContent.orEmpty()
        val title = entry.getElementsByTagNameNS(ATOM_NS, "title").item(0)?.textContent.orEmpty()

        val linkNodes = entry.getElementsByTagNameNS(ATOM_NS, "link")
        val links = (0 until linkNodes.length).map { linkNodes.item(it) as Element }

        val thumbnailHref = links.firstOrNull { it.getAttribute("rel") == REL_THUMBNAIL }
            ?.getAttribute("href")?.takeIf { it.isNotEmpty() }
        val imageHref = links.firstOrNull { it.getAttribute("rel") == REL_IMAGE }
            ?.getAttribute("href")?.takeIf { it.isNotEmpty() }
        val coverHref = thumbnailHref ?: imageHref

        val acquisitionLink = links.firstOrNull { it.getAttribute("rel") == REL_ACQUISITION }
        val acquisitionHref = acquisitionLink?.getAttribute("href")?.takeIf { it.isNotEmpty() }
        val acquisitionType = acquisitionLink?.getAttribute("type")?.takeIf { it.isNotEmpty() }

        return OpdsEntry(
            id = id,
            title = title,
            coverHref = coverHref,
            acquisitionHref = acquisitionHref,
            acquisitionType = acquisitionType,
        )
    }
}
