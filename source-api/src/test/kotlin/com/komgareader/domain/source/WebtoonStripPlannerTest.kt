package com.komgareader.domain.source

import com.komgareader.domain.reader.WebtoonChapter
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pinnt die **pure** Webtoon-Strip-Planung: aus den Kapiteln (remoteId + Seitenzahl), dem
 * geöffneten Buch und dessen lokalem Startseiten-Index entsteht der kapitelübergreifende
 * Strip, die flache Seiten-Liste und der globale Startindex — ohne I/O. Genau die Logik,
 * die vorher im God-`ReaderViewModel.loadWebtoonStrip` steckte.
 */
class WebtoonStripPlannerTest {

    private fun chapter(remoteId: String, pageCount: Int) = WebtoonChapter(remoteId, pageCount)

    @Test
    fun `flacher Seiten-Strip haengt alle Kapitel nahtlos aneinander`() {
        val plan = buildWebtoonStrip(
            chapters = listOf(chapter("b1", 2), chapter("b2", 3)),
            openedBookRemoteId = "b1",
            localStartPage = 0,
        )

        assertEquals(5, plan.pages.size)
        assertEquals(PageRef(index = 0, bookRemoteId = "b1", pageNumber = 1, url = ""), plan.pages.first())
        assertEquals(PageRef(index = 0, bookRemoteId = "b2", pageNumber = 1, url = ""), plan.pages[2])
        assertEquals(PageRef(index = 2, bookRemoteId = "b2", pageNumber = 3, url = ""), plan.pages.last())
    }

    @Test
    fun `Strip bildet Kapitel und Seitenzahlen ab`() {
        val plan = buildWebtoonStrip(
            chapters = listOf(chapter("b1", 2), chapter("b2", 3)),
            openedBookRemoteId = "b1",
            localStartPage = 0,
        )
        assertEquals(2, plan.strip.chapters.size)
        assertEquals(5, plan.strip.totalPages)
    }

    @Test
    fun `Startindex ist global - Startseite im geoeffneten Kapitel plus dessen Offset`() {
        // Geöffnet: b2 (Offset 2), lokale Startseite 1 → globaler Index 3.
        val plan = buildWebtoonStrip(
            chapters = listOf(chapter("b1", 2), chapter("b2", 3)),
            openedBookRemoteId = "b2",
            localStartPage = 1,
        )
        assertEquals(3, plan.initialGlobalIndex)
    }

    @Test
    fun `unbekanntes geoeffnetes Buch faellt auf das erste Kapitel zurueck`() {
        val plan = buildWebtoonStrip(
            chapters = listOf(chapter("b1", 2), chapter("b2", 3)),
            openedBookRemoteId = "unknown",
            localStartPage = 0,
        )
        assertEquals(0, plan.initialGlobalIndex)
    }

    @Test
    fun `leerer Strip hat Startindex 0 und keine Seiten`() {
        val plan = buildWebtoonStrip(
            chapters = emptyList(),
            openedBookRemoteId = "b1",
            localStartPage = 0,
        )
        assertEquals(0, plan.initialGlobalIndex)
        assertEquals(emptyList(), plan.pages)
    }
}
