package com.komgareader.source.opds

import org.w3c.dom.Element
import org.w3c.dom.NodeList
import javax.xml.parsers.DocumentBuilderFactory

private const val ATOM_NS = "http://www.w3.org/2005/Atom"
private const val REL_THUMBNAIL = "http://opds-spec.org/image/thumbnail"
private const val REL_IMAGE = "http://opds-spec.org/image"
private const val REL_ACQUISITION = "http://opds-spec.org/acquisition"

/** Setzt ein Parser-Feature tolerant — vom jeweiligen Parser nicht unterstützte Features werden ignoriert. */
private fun DocumentBuilderFactory.trySetFeature(name: String, value: Boolean) {
    runCatching { setFeature(name, value) }
}

/**
 * Parst einen OPDS-Atom-Feed (XML-String) und liefert die enthaltenen Einträge.
 * Verwendet namespace-aware DOM-Parsing (JVM built-in).
 */
class OpdsFeedParser {

    fun parse(xml: String): List<OpdsEntry> {
        // XXE-Härtung: OPDS-Feeds kommen von fremden Servern → DTDs und externe
        // Entitäten deaktivieren (verhindert Datei-Leak/SSRF). Die Feature-Namen sind
        // parser-abhängig: Androids Harmony-Parser kennt z. B. `disallow-doctype-decl`
        // NICHT und wirft sonst — deshalb jedes Feature einzeln und tolerant setzen.
        // Die für XXE entscheidenden External-Entity-Features greifen auf beiden Plattformen;
        // `isExpandEntityReferences = false` ist der plattformunabhängige Riegel.
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            trySetFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            trySetFeature("http://xml.org/sax/features/external-general-entities", false)
            trySetFeature("http://xml.org/sax/features/external-parameter-entities", false)
            trySetFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            // Auch diese Setter wirft Androids Parser z. T. (UnsupportedOperationException) — tolerant.
            runCatching { isXIncludeAware = false }
            runCatching { isExpandEntityReferences = false }
        }
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
