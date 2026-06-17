package com.komgareader.source.opds

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Live-Integrationstest gegen die echten lokalen Test-Server (Komga + Kavita). **Nur** aktiv,
 * wenn `OPDS_LIVE=1` gesetzt ist — die normale Suite überspringt ihn (kein Netz/CI-Zwang).
 *
 * Beweist die hierarchische OPDS-Navigation (Serien-Feed → Subsection → Bücher-Feed) + PSE-Streaming
 * end-to-end durch [OpdsSource] gegen die zwei realen Server-Formen:
 * - Komga `/opds/v1.2/series` (Basic-Auth, `pse:`-Prefix, Platzhalter im Pfad)
 * - Kavita `/api/opds/<key>/libraries/1` (Key im Pfad, `p5:`-Prefix, Platzhalter im Query)
 */
class OpdsLiveTest {

    @Test
    fun `Komga - browse navigiert zu Buch und streamt PSE-Seite`() = runTest {
        assumeTrue(System.getenv("OPDS_LIVE") == "1")
        val source = OpdsSourceFactory.create(
            name = "Komga-Live",
            catalogUrl = "http://localhost:25600/opds/v1.2/series",
            username = "admin@test.local",
            password = "testpass123",
        )

        // Pagination: alle Seiten über den next-Cursor durchblättern (wie browseAllSeries).
        val filter = com.komgareader.domain.source.SourceFilter()
        val allSeries = mutableListOf<com.komgareader.domain.model.Series>()
        var page = 0
        var page0Size = 0
        while (page < 50) {
            val r = source.browse(page, filter)
            if (page == 0) page0Size = r.items.size
            allSeries += r.items
            if (!r.hasNextPage) break
            page++
        }
        assertTrue(page > 0, "Komga: browse sollte mehr als eine Seite haben (paginiert nicht?)")
        assertTrue(allSeries.size > page0Size, "Pagination brachte keine zusätzlichen Serien")
        // Berserk liegt alphabetisch jenseits Seite 0 — beweist, dass Pagination es erreicht.
        assertTrue(allSeries.any { it.title.contains("Berserk", ignoreCase = true) }, "Berserk (Seite 1+) nicht gefunden")

        // Attack on Titan (Seite 0, stabiler Mount) für den Lese-/PSE-Pfad.
        val aot = allSeries.first { it.title.contains("Attack on Titan", ignoreCase = true) }

        val books = source.books(aot.remoteId)
        assertTrue(books.isNotEmpty(), "Komga: keine Bücher in Serie ${aot.title}")
        val book = books.first { it.pageCount > 0 }

        val refs = source.pages(book.remoteId)
        assertTrue(refs.size == book.pageCount, "Komga: pages() != pseCount (${refs.size} vs ${book.pageCount})")
        val bytes = source.openPage(refs.first())
        assertTrue(bytes.size > 100, "Komga: PSE-Seite 0 lieferte ${bytes.size} Bytes")
        println("KOMGA OK: ${aot.title} -> ${book.remoteId}, ${refs.size} Seiten, Seite0=${bytes.size}B")
    }

    @Test
    fun `Kavita - browse navigiert zu Kapitel und streamt PSE-Seite`() = runTest {
        assumeTrue(System.getenv("OPDS_LIVE") == "1")
        // Kein Token im Repo: der Kavita-OPDS-Key muss als Env-Var kommen (sonst Skip).
        val key = System.getenv("KAVITA_KEY")
        assumeTrue(key != null && key.isNotBlank())
        val source = OpdsSourceFactory.create(
            name = "Kavita-Live",
            catalogUrl = "http://localhost:5001/api/opds/$key/libraries/1",
        )

        val series = source.browse(0, com.komgareader.domain.source.SourceFilter()).items
        assertTrue(series.isNotEmpty(), "Kavita: keine Serien aus dem OPDS-Feed")

        val books = source.books(series.first().remoteId)
        assertTrue(books.isNotEmpty(), "Kavita: keine Kapitel in ${series.first().title}")
        val book = books.first { it.pageCount > 0 }

        val refs = source.pages(book.remoteId)
        assertTrue(refs.size == book.pageCount, "Kavita: pages() != pseCount (${refs.size} vs ${book.pageCount})")
        val bytes = source.openPage(refs.first())
        assertTrue(bytes.size > 100, "Kavita: PSE-Seite 0 lieferte ${bytes.size} Bytes")
        println("KAVITA OK: ${series.first().title} -> ${book.remoteId}, ${refs.size} Seiten, Seite0=${bytes.size}B")
    }
}
