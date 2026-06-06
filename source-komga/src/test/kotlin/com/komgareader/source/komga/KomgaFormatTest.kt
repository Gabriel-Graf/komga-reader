package com.komgareader.source.komga

import com.komgareader.domain.model.BookFormat
import kotlin.test.Test
import kotlin.test.assertEquals

class KomgaFormatTest {

    @Test
    fun `zip ergibt CBZ`() {
        assertEquals(BookFormat.CBZ, mediaTypeToFormat("application/zip"))
    }

    @Test
    fun `rar in allen Varianten ergibt CBR`() {
        assertEquals(BookFormat.CBR, mediaTypeToFormat("application/x-rar-compressed"))
        assertEquals(BookFormat.CBR, mediaTypeToFormat("application/x-rar-compressed; version=5"))
    }

    @Test
    fun `pdf ergibt PDF`() {
        assertEquals(BookFormat.PDF, mediaTypeToFormat("application/pdf"))
    }

    @Test
    fun `epub ergibt EPUB`() {
        assertEquals(BookFormat.EPUB, mediaTypeToFormat("application/epub+zip"))
    }

    @Test
    fun `unbekannter Typ faellt auf CBZ zurueck`() {
        assertEquals(BookFormat.CBZ, mediaTypeToFormat("application/octet-stream"))
    }
}
