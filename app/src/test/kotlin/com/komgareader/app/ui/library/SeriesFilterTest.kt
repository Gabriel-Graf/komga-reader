package com.komgareader.app.ui.library

import com.komgareader.domain.model.ContentType
import com.komgareader.domain.model.Series
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SeriesFilterTest {

    private fun series(title: String, type: ContentType? = null) =
        Series(id = 0, sourceId = 1, remoteId = title, title = title, contentTypeOverride = type)

    private val all = listOf(
        series("Berserk", ContentType.MANGA),
        series("Saga", ContentType.COMIC),
        series("Tower", ContentType.WEBTOON),
        series("Roman ohne Typ", null),
    )

    @Test fun `empty query and empty filter returns all`() {
        assertEquals(all, filterSeries(all, "", emptySet()))
    }

    @Test fun `single type filter keeps only that type`() {
        assertEquals(listOf("Berserk"), filterSeries(all, "", setOf(ContentType.MANGA)).map { it.title })
    }

    @Test fun `multi type filter keeps all selected types`() {
        assertEquals(
            listOf("Berserk", "Saga"),
            filterSeries(all, "", setOf(ContentType.MANGA, ContentType.COMIC)).map { it.title },
        )
    }

    @Test fun `filter excludes untyped series`() {
        assertEquals(
            emptyList<String>(),
            filterSeries(all, "", setOf(ContentType.NOVEL)).map { it.title },
        )
    }

    @Test fun `all untyped with active filter returns empty`() {
        val untyped = listOf(series("A"), series("B"))
        assertEquals(emptyList<Series>(), filterSeries(untyped, "", setOf(ContentType.MANGA)))
    }

    @Test fun `query and type combine`() {
        assertEquals(
            listOf("Berserk"),
            filterSeries(all, "ber", setOf(ContentType.MANGA)).map { it.title },
        )
    }

    @Test fun `query is case insensitive`() {
        assertEquals(listOf("Saga"), filterSeries(all, "SAG", emptySet()).map { it.title })
    }
}
