package com.komgareader.domain.usecase

import com.komgareader.domain.model.BookFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DetectBookFormatTest {
    @Test fun mime_epub() = assertEquals(BookFormat.EPUB, detectBookFormat("application/epub+zip", null))
    @Test fun mime_pdf() = assertEquals(BookFormat.PDF, detectBookFormat("application/pdf", "x"))
    @Test fun mime_cbz_zip() = assertEquals(BookFormat.CBZ, detectBookFormat("application/zip", null))
    @Test fun mime_cbz_comicbook() = assertEquals(BookFormat.CBZ, detectBookFormat("application/vnd.comicbook+zip", null))
    @Test fun mime_cbr() = assertEquals(BookFormat.CBR, detectBookFormat("application/x-cbr", null))
    @Test fun mime_cbr_rar() = assertEquals(BookFormat.CBR, detectBookFormat("application/x-rar-compressed", null))
    @Test fun octet_falls_back_to_extension() =
        assertEquals(BookFormat.CBZ, detectBookFormat("application/octet-stream", "Vol. 1.cbz"))
    @Test fun null_mime_uses_extension() = assertEquals(BookFormat.EPUB, detectBookFormat(null, "book.EPUB"))
    @Test fun extension_pdf() = assertEquals(BookFormat.PDF, detectBookFormat(null, "doc.pdf"))
    @Test fun extension_cbr() = assertEquals(BookFormat.CBR, detectBookFormat(null, "a.cbr"))
    @Test fun unknown_returns_null() = assertNull(detectBookFormat("text/plain", "notes.txt"))
    @Test fun unknown_no_hints_null() = assertNull(detectBookFormat(null, null))
}
