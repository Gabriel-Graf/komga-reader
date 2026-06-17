package com.komgareader.app.ui.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderPrefetchTest {

    @Test
    fun `liefert die naechsten drei Seiten vorwaerts`() {
        assertEquals(listOf(6, 7, 8), prefetchIndices(current = 5, count = 20, ahead = 3))
    }

    @Test
    fun `clippt am Ende des Buchs`() {
        // Auf der vorletzten Seite (Index 18 von 20) gibt es nur noch eine Folgeseite.
        assertEquals(listOf(19), prefetchIndices(current = 18, count = 20, ahead = 3))
    }

    @Test
    fun `letzte Seite liefert nichts`() {
        assertEquals(emptyList(), prefetchIndices(current = 19, count = 20, ahead = 3))
    }

    @Test
    fun `ist vorwaerts-biased und liefert keine vorherigen Seiten`() {
        // Kein negativer Index, nichts hinter current.
        assertEquals(listOf(1, 2, 3), prefetchIndices(current = 0, count = 20, ahead = 3))
    }
}
