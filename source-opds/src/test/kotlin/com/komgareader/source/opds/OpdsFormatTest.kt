package com.komgareader.source.opds

import com.komgareader.domain.model.BookFormat
import kotlin.test.Test
import kotlin.test.assertEquals

class OpdsFormatTest {

    @Test
    fun `application-x-cbz wird zu CBZ`() {
        assertEquals(BookFormat.CBZ, opdsTypeToFormat("application/x-cbz"))
    }

    @Test
    fun `application-zip wird zu CBZ`() {
        assertEquals(BookFormat.CBZ, opdsTypeToFormat("application/zip"))
    }

    @Test
    fun `application-x-cbr wird zu CBR`() {
        assertEquals(BookFormat.CBR, opdsTypeToFormat("application/x-cbr"))
    }

    @Test
    fun `application-pdf wird zu PDF`() {
        assertEquals(BookFormat.PDF, opdsTypeToFormat("application/pdf"))
    }

    @Test
    fun `application-epub-zip wird zu EPUB`() {
        assertEquals(BookFormat.EPUB, opdsTypeToFormat("application/epub+zip"))
    }

    @Test
    fun `null faellt auf CBZ zurueck`() {
        assertEquals(BookFormat.CBZ, opdsTypeToFormat(null))
    }

    @Test
    fun `unbekannter Typ faellt auf CBZ zurueck`() {
        assertEquals(BookFormat.CBZ, opdsTypeToFormat("application/unknown"))
    }
}
