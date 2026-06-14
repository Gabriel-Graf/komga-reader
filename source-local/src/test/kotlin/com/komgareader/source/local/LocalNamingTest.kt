package com.komgareader.source.local

import com.komgareader.domain.model.BookFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocalNamingTest {
    @Test fun `natural sort orders v2 before v10`() {
        val input = listOf("v10.cbz", "v2.cbz", "v1.cbz")
        assertEquals(listOf("v1.cbz", "v2.cbz", "v10.cbz"), input.sortedWith(naturalOrder))
    }
    @Test fun `format from extension is case-insensitive`() {
        assertEquals(BookFormat.CBZ, formatOf("Berserk/v01.CBZ"))
        assertEquals(BookFormat.CBR, formatOf("a.cbr"))
        assertEquals(BookFormat.PDF, formatOf("a.pdf"))
        assertEquals(BookFormat.EPUB, formatOf("b.EPUB"))
        assertNull(formatOf("notes.txt"))
        assertNull(formatOf("folder"))
    }
    @Test fun `title strips extension and path`() {
        assertEquals("v01", titleOf("Berserk/v01.cbz"))
        assertEquals("oneshot", titleOf("oneshot.pdf"))
    }
    @Test fun `image entry filter keeps only images`() {
        assertEquals(true, isImageEntry("001.jpg"))
        assertEquals(true, isImageEntry("a/002.PNG"))
        assertEquals(false, isImageEntry("ComicInfo.xml"))
        assertEquals(false, isImageEntry("dir/"))
    }
}
