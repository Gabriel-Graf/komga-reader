package com.komgareader.domain.usecase

import com.komgareader.domain.model.ContentType
import com.komgareader.domain.model.Series
import com.komgareader.domain.model.Shelf
import com.komgareader.domain.model.ShelfSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ResolveShelfContentTypeTest {

    private val resolve = ResolveShelfContentType()

    private fun series(sourceId: Long = 1, libraryId: String? = "L1") =
        Series(id = 0, sourceId = sourceId, remoteId = "S", title = "t", libraryId = libraryId)

    private fun shelf(type: ContentType?, sources: List<ShelfSource>) =
        Shelf(id = 1, name = "Regal", sources = sources, defaultContentType = type)

    @Test
    fun `Serie in Regal mit passender Library erbt dessen Typ`() {
        val shelves = listOf(shelf(ContentType.COMIC, listOf(ShelfSource(sourceId = 1, containerIds = listOf("L1", "L2")))))
        assertEquals(ContentType.COMIC, resolve(series(), shelves))
    }

    @Test
    fun `leere containerIds = ganze Quelle matcht jede Library der Quelle`() {
        val shelves = listOf(shelf(ContentType.WEBTOON, listOf(ShelfSource(sourceId = 1, containerIds = emptyList()))))
        assertEquals(ContentType.WEBTOON, resolve(series(libraryId = "egal"), shelves))
    }

    @Test
    fun `andere Quelle matcht nicht`() {
        val shelves = listOf(shelf(ContentType.COMIC, listOf(ShelfSource(sourceId = 99, containerIds = emptyList()))))
        assertNull(resolve(series(sourceId = 1), shelves))
    }

    @Test
    fun `Library nicht in containerIds matcht nicht`() {
        val shelves = listOf(shelf(ContentType.COMIC, listOf(ShelfSource(sourceId = 1, containerIds = listOf("L9")))))
        assertNull(resolve(series(libraryId = "L1"), shelves))
    }

    @Test
    fun `Regal ohne defaultContentType wird übersprungen, nächstes passendes gewinnt`() {
        val shelves = listOf(
            shelf(null, listOf(ShelfSource(sourceId = 1, containerIds = emptyList()))),
            Shelf(id = 2, name = "Manga", sources = listOf(ShelfSource(1, listOf("L1"))), defaultContentType = ContentType.MANGA),
        )
        assertEquals(ContentType.MANGA, resolve(series(libraryId = "L1"), shelves))
    }

    @Test
    fun `keine Regale ergibt null`() {
        assertNull(resolve(series(), emptyList()))
    }

    @Test
    fun `unbekannte libraryId matcht nur Regale mit leeren containerIds`() {
        val shelves = listOf(shelf(ContentType.NOVEL, listOf(ShelfSource(sourceId = 1, containerIds = listOf("L1")))))
        assertNull(resolve(series(libraryId = null), shelves))
    }
}
