package com.komgareader.source.opds

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OpdsFeedParserTest {

    private val parser = OpdsFeedParser()

    private val exampleFeed = """<?xml version="1.0"?>
<feed xmlns="http://www.w3.org/2005/Atom">
  <entry><title>Vinland Saga 01</title><id>urn:vs:1</id>
    <link rel="http://opds-spec.org/image/thumbnail" href="/cover/1.jpg" type="image/jpeg"/>
    <link rel="http://opds-spec.org/acquisition" href="/dl/1.cbz" type="application/x-cbz"/></entry>
  <entry><title>Mistborn</title><id>urn:mb:1</id>
    <link rel="http://opds-spec.org/acquisition" href="/dl/mb.epub" type="application/epub+zip"/></entry>
</feed>"""

    @Test
    fun `parse liefert zwei Einträge`() {
        val entries = parser.parse(exampleFeed)
        assertEquals(2, entries.size)
    }

    @Test
    fun `erster Eintrag hat Titel und ID`() {
        val entries = parser.parse(exampleFeed)
        assertEquals("Vinland Saga 01", entries[0].title)
        assertEquals("urn:vs:1", entries[0].id)
    }

    @Test
    fun `erster Eintrag hat Cover und Acquisition-Link`() {
        val entries = parser.parse(exampleFeed)
        assertEquals("/cover/1.jpg", entries[0].coverHref)
        assertEquals("/dl/1.cbz", entries[0].acquisitionHref)
        assertEquals("application/x-cbz", entries[0].acquisitionType)
    }

    @Test
    fun `zweiter Eintrag hat kein Cover`() {
        val entries = parser.parse(exampleFeed)
        assertNull(entries[1].coverHref)
        assertEquals("/dl/mb.epub", entries[1].acquisitionHref)
        assertEquals("application/epub+zip", entries[1].acquisitionType)
    }

    @Test
    fun `leerer Feed liefert leere Liste`() {
        val emptyFeed = """<?xml version="1.0"?><feed xmlns="http://www.w3.org/2005/Atom"></feed>"""
        val entries = parser.parse(emptyFeed)
        assertEquals(0, entries.size)
    }

    @Test
    fun `image-Link als Fallback wenn kein Thumbnail vorhanden`() {
        val feed = """<?xml version="1.0"?>
<feed xmlns="http://www.w3.org/2005/Atom">
  <entry><title>Test</title><id>urn:t:1</id>
    <link rel="http://opds-spec.org/image" href="/cover/full.jpg" type="image/jpeg"/>
    <link rel="http://opds-spec.org/acquisition" href="/dl/t.epub" type="application/epub+zip"/>
  </entry>
</feed>"""
        val entries = parser.parse(feed)
        assertEquals("/cover/full.jpg", entries[0].coverHref)
    }

    @Test
    fun `Thumbnail hat Vorrang vor image-Link`() {
        val feed = """<?xml version="1.0"?>
<feed xmlns="http://www.w3.org/2005/Atom">
  <entry><title>Test</title><id>urn:t:1</id>
    <link rel="http://opds-spec.org/image" href="/cover/full.jpg" type="image/jpeg"/>
    <link rel="http://opds-spec.org/image/thumbnail" href="/cover/thumb.jpg" type="image/jpeg"/>
    <link rel="http://opds-spec.org/acquisition" href="/dl/t.epub" type="application/epub+zip"/>
  </entry>
</feed>"""
        val entries = parser.parse(feed)
        assertEquals("/cover/thumb.jpg", entries[0].coverHref)
    }
}
