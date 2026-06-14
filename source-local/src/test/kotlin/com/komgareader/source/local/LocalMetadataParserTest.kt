package com.komgareader.source.local

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocalMetadataParserTest {
    private val parser = LocalMetadataParser()
    @Test fun `parses series title number summary genres`() {
        val xml = """
            <?xml version="1.0"?>
            <ComicInfo>
              <Series>Berserk</Series>
              <Number>3</Number>
              <Summary>Guts fights.</Summary>
              <Genre>Action, Dark Fantasy</Genre>
            </ComicInfo>
        """.trimIndent()
        val m = parser.parse(xml)!!
        assertEquals("Berserk", m.series)
        assertEquals("3", m.number)
        assertEquals("Guts fights.", m.summary)
        assertEquals(listOf("Action", "Dark Fantasy"), m.genres)
    }
    @Test fun `missing fields are null or empty`() {
        val m = parser.parse("<ComicInfo><Number>1</Number></ComicInfo>")!!
        assertNull(m.series)
        assertEquals("1", m.number)
        assertNull(m.summary)
        assertEquals(emptyList(), m.genres)
    }
    @Test fun `non-comicinfo or garbage returns null`() {
        assertNull(parser.parse("not xml at all"))
        assertNull(parser.parse("<other/>"))
    }
    @Test fun `xxe doctype payload does not resolve external entity`() {
        val payload = """
            <?xml version="1.0"?>
            <!DOCTYPE foo [ <!ENTITY xxe SYSTEM "file:///etc/passwd"> ]>
            <ComicInfo><Series>&xxe;</Series></ComicInfo>
        """.trimIndent()
        // disallow-doctype-decl makes this throw internally → parse returns null (no file read).
        assertNull(parser.parse(payload))
    }

    @Test fun `oversized input is rejected`() {
        val huge = "<ComicInfo><Series>" + "a".repeat(600_000) + "</Series></ComicInfo>"
        assertNull(parser.parse(huge))
    }
}
