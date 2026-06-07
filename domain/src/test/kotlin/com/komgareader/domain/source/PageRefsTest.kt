package com.komgareader.domain.source

import kotlin.test.Test
import kotlin.test.assertEquals

class PageRefsTest {
    @Test fun `buildPageRefs erzeugt 1-basierte pageNumbers und 0-basierte indizes`() {
        val refs = buildPageRefs(bookRemoteId = "b1", pageCount = 3)
        assertEquals(3, refs.size)
        assertEquals(PageRef(index = 0, bookRemoteId = "b1", pageNumber = 1, url = ""), refs[0])
        assertEquals(PageRef(index = 2, bookRemoteId = "b1", pageNumber = 3, url = ""), refs[2])
    }

    @Test fun `buildPageRefs bei 0 Seiten ist leer`() {
        assertEquals(emptyList(), buildPageRefs("b1", 0))
    }
}
