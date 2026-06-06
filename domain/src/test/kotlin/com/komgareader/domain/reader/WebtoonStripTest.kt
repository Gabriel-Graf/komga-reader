package com.komgareader.domain.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class WebtoonStripTest {

    @Test
    fun `leerer Strip hat null Seiten`() {
        val strip = WebtoonStrip(emptyList())
        assertEquals(0, strip.totalPages)
    }

    @Test
    fun `ein Kapitel — totalPages und Lokalisierung`() {
        val strip = WebtoonStrip(listOf(WebtoonChapter("a", 3)))
        assertEquals(3, strip.totalPages)
        assertEquals(0, strip.chapterStart(0))
        assertEquals(StripPosition(0, 0, "a"), strip.locate(0))
        assertEquals(StripPosition(0, 2, "a"), strip.locate(2))
        assertEquals(1, strip.globalIndex(0, 1))
    }

    @Test
    fun `mehrere Kapitel — nahtlose globale Indizes`() {
        // Kapitel: a=3, b=2, c=4 → globale Indizes 0..8
        val strip = WebtoonStrip(
            listOf(WebtoonChapter("a", 3), WebtoonChapter("b", 2), WebtoonChapter("c", 4)),
        )
        assertEquals(9, strip.totalPages)
        assertEquals(0, strip.chapterStart(0))
        assertEquals(3, strip.chapterStart(1))
        assertEquals(5, strip.chapterStart(2))

        // Grenzübergänge ohne Naht
        assertEquals(StripPosition(0, 2, "a"), strip.locate(2))
        assertEquals(StripPosition(1, 0, "b"), strip.locate(3))
        assertEquals(StripPosition(1, 1, "b"), strip.locate(4))
        assertEquals(StripPosition(2, 0, "c"), strip.locate(5))
        assertEquals(StripPosition(2, 3, "c"), strip.locate(8))

        assertEquals(8, strip.globalIndex(2, 3))
        assertEquals(5, strip.globalIndex(2, 0))
    }

    @Test
    fun `Kapitel mit null Seiten werden uebersprungen`() {
        val strip = WebtoonStrip(
            listOf(WebtoonChapter("a", 2), WebtoonChapter("leer", 0), WebtoonChapter("c", 1)),
        )
        assertEquals(3, strip.totalPages)
        assertEquals(StripPosition(0, 1, "a"), strip.locate(1))
        assertEquals(StripPosition(2, 0, "c"), strip.locate(2))
    }

    @Test
    fun `locate klemmt ausserhalb des Bereichs`() {
        val strip = WebtoonStrip(listOf(WebtoonChapter("a", 2), WebtoonChapter("b", 2)))
        assertEquals(StripPosition(0, 0, "a"), strip.locate(-5))
        assertEquals(StripPosition(1, 1, "b"), strip.locate(99))
    }
}
