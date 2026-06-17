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

        val series = source.browse(0, com.komgareader.domain.source.SourceFilter()).items
        assertTrue(series.isNotEmpty(), "Komga: keine Serien aus dem OPDS-Feed")
        // Auf Seite 0 (browse paginiert noch nicht) und mit stabilem Mount lesbar.
        val berserk = series.first { it.title.contains("Attack on Titan", ignoreCase = true) }

        val books = source.books(berserk.remoteId)
        assertTrue(books.isNotEmpty(), "Komga: keine Bücher in Serie ${berserk.title}")
        val book = books.first { it.pageCount > 0 }

        val refs = source.pages(book.remoteId)
        assertTrue(refs.size == book.pageCount, "Komga: pages() != pseCount (${refs.size} vs ${book.pageCount})")
        val bytes = source.openPage(refs.first())
        assertTrue(bytes.size > 100, "Komga: PSE-Seite 0 lieferte ${bytes.size} Bytes")
        println("KOMGA OK: ${berserk.title} -> ${book.remoteId}, ${refs.size} Seiten, Seite0=${bytes.size}B")
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
