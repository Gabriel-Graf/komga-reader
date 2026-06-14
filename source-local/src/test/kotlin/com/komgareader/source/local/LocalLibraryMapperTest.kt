package com.komgareader.source.local

import com.komgareader.domain.model.BookFormat
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalLibraryMapperTest {
    private val mapper = LocalLibraryMapper()
    @Test fun `subfolder becomes a series with its files as natural-sorted books`() {
        val entries = listOf(
            ScannedEntry("Berserk", isDirectory = true),
            ScannedEntry("Berserk/v10.cbz", isDirectory = false),
            ScannedEntry("Berserk/v2.cbz", isDirectory = false),
            ScannedEntry("Berserk/notes.txt", isDirectory = false),
        )
        val index = mapper.map(entries)
        val s = index.series.single()
        assertEquals("Berserk", s.title)
        assertEquals("Berserk", s.remoteId)
        assertEquals(listOf("Berserk/v2.cbz", "Berserk/v10.cbz"), s.books.map { it.remoteId })
        assertEquals(BookFormat.CBZ, s.books.first().format)
    }
    @Test fun `loose root file becomes a single-volume series`() {
        val index = mapper.map(listOf(ScannedEntry("oneshot.pdf", isDirectory = false)))
        val s = index.series.single()
        assertEquals("oneshot", s.title)
        assertEquals("oneshot.pdf", s.remoteId)
        assertEquals(listOf("oneshot.pdf"), s.books.map { it.remoteId })
        assertEquals(BookFormat.PDF, s.books.single().format)
    }
    @Test fun `empty folders and unsupported files yield no series`() {
        val index = mapper.map(
            listOf(
                ScannedEntry("Empty", isDirectory = true),
                ScannedEntry("readme.txt", isDirectory = false),
            ),
        )
        assertEquals(emptyList(), index.series)
    }
    @Test fun `series are natural-sorted by title`() {
        val index = mapper.map(
            listOf(
                ScannedEntry("Series 10", isDirectory = true),
                ScannedEntry("Series 10/a.cbz", isDirectory = false),
                ScannedEntry("Series 2", isDirectory = true),
                ScannedEntry("Series 2/a.cbz", isDirectory = false),
            ),
        )
        assertEquals(listOf("Series 2", "Series 10"), index.series.map { it.title })
    }
}
