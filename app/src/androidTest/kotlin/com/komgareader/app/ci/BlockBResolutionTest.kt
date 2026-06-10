package com.komgareader.app.ci

import com.komgareader.domain.source.SourceFilter
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/** Spec §9 Block B — Werk-Auflösung pro Quelle (multi-source, get(sourceId) statt current()). */
@RunWith(AndroidJUnit4::class)
class BlockBResolutionTest {

    private lateinit var stack: CiSourceStack

    @Before fun setUp() { stack = CiSourceStack() }
    @After fun tearDown() { stack.close() }

    /**
     * B7: Bei zwei aktiven Quellen wird das Werk der ZWEITEN Quelle (Webtoon aus B) über
     * get(sourceId) aufgelöst — nicht über „die erste/aktive". Beweist multi-source pro Werk.
     */
    @Test fun b7_werk_der_zweiten_quelle_ueber_get_aufgeloest() = runTest {
        stack.register(CiKomga.A, CiKomga.B)
        val all = stack.activeSource.all()

        // Die Webtoon-Serie gehört zu B. Ihre sourceId aus der Aggregation ziehen …
        val webtoon = all
            .flatMap { it.browse(0, SourceFilter()).items }
            .first { it.title == CiFixtures.WEBTOON_SERIES }

        // … und exakt diese Quelle über get(sourceId) auflösen.
        val resolved = stack.activeSource.get(webtoon.sourceId)
        assertTrue("get(sourceId) muss die Quelle des Werks liefern", resolved != null)
        // Die aufgelöste Quelle muss die Bücher der Webtoon-Serie liefern können.
        val books = resolved!!.books(webtoon.remoteId)
        assertTrue("Webtoon-Serie muss mind. ein Buch haben", books.isNotEmpty())
        assertEquals("Buch trägt die sourceId von B", webtoon.sourceId, books.first().sourceId)
    }

    /**
     * B8: Seiten- und Cover-Bytes fließen durch die Naht (openPage/coverBytes) — kein direkter
     * URL/Auth-Pfad. Manga-Serie aus A: erste Seite des ersten Buchs muss > 1 KiB liefern.
     */
    @Test fun b8_seiten_und_cover_durch_die_naht() = runTest {
        stack.register(CiKomga.A)
        val source = stack.activeSource.all().first()
        val manga = source.browse(0, SourceFilter()).items.first { it.title == CiFixtures.MANGA_SERIES }

        val books = source.books(manga.remoteId)
        assertTrue("Manga muss Bücher haben", books.isNotEmpty())
        val pages = source.pages(books.first().remoteId)
        assertTrue("Buch muss Seiten haben", pages.isNotEmpty())

        val pageBytes = source.openPage(pages.first())
        assertTrue("Seiten-Bytes > 1 KiB (durch openPage, nicht direkt-URL)", pageBytes.size > 1024)

        val cover = source.coverBytes(manga.remoteId, isSeriesCover = true)
        assertTrue("Cover-Bytes > 1 KiB (durch coverBytes)", cover.size > 1024)
    }
}
