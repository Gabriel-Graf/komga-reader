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

    @Test fun `query matches untyped series when filter is empty`() {
        assertEquals(listOf("Roman ohne Typ"), filterSeries(all, "Roman", emptySet()).map { it.title })
    }

    // --- typeOf-Injektion: Filter nutzt den hereingereichten effektiven Typ (= das Tag) ---

    @Test fun `filter uses injected effective type not the override field`() {
        // Serien OHNE contentTypeOverride, Typ kommt von außen (z. B. Bibliotheks-Default).
        val list = listOf(series("Tower of God"), series("Berserk"))
        val effective = mapOf("Tower of God" to ContentType.WEBTOON, "Berserk" to ContentType.MANGA)
        assertEquals(
            listOf("Tower of God"),
            filterSeries(list, "", setOf(ContentType.WEBTOON), typeOf = { effective[it.remoteId] }).map { it.title },
        )
    }

    @Test fun `series with unknown effective type falls out under active filter`() {
        val list = listOf(series("Ohne Tag"))
        assertEquals(
            emptyList<String>(),
            filterSeries(list, "", setOf(ContentType.WEBTOON), typeOf = { null }).map { it.title },
        )
    }

    // --- Heruntergeladen-Filter ---

    @Test fun `downloaded filter keeps only locally available series`() {
        val downloaded = setOf("Berserk")
        assertEquals(
            listOf("Berserk"),
            filterSeries(all, "", emptySet(), downloadedOnly = true) { it.remoteId in downloaded }
                .let { result -> result.map { it.title } },
        )
    }

    @Test fun `downloaded filter inactive keeps all`() {
        assertEquals(all.size, filterSeries(all, "", emptySet(), downloadedOnly = false).size)
    }

    @Test fun `downloaded combines with type and query`() {
        val downloaded = setOf("Berserk", "Saga")
        // Typ Manga + heruntergeladen → nur Berserk (Saga ist Comic).
        assertEquals(
            listOf("Berserk"),
            filterSeries(
                all, "", setOf(ContentType.MANGA),
                downloadedOnly = true,
                isDownloaded = { it.remoteId in downloaded },
            ).map { it.title },
        )
    }

    @Test fun `downloaded filter with nothing local returns empty`() {
        assertEquals(
            emptyList<String>(),
            filterSeries(all, "", emptySet(), downloadedOnly = true) { false }.map { it.title },
        )
    }
}
