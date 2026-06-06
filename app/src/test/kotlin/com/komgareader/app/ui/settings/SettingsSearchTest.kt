package com.komgareader.app.ui.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SettingsSearchTest {

    @Test fun `matchRanges returns empty when no occurrence`() {
        assertEquals(emptyList<IntRange>(), matchRanges("Hello world", "xyz"))
    }

    @Test fun `matchRanges finds single occurrence`() {
        assertEquals(listOf(6..10), matchRanges("Hello world", "world"))
    }

    @Test fun `matchRanges finds multiple occurrences`() {
        assertEquals(listOf(0..1, 5..6), matchRanges("abxxxab", "ab"))
    }

    @Test fun `matchRanges is case insensitive`() {
        assertEquals(listOf(0..3), matchRanges("Dunkel", "dunk"))
    }

    @Test fun `matchRanges blank query returns empty`() {
        assertEquals(emptyList<IntRange>(), matchRanges("Hello", ""))
        assertEquals(emptyList<IntRange>(), matchRanges("Hello", "   "))
    }

    @Test fun `matchRanges query longer than text returns empty`() {
        assertEquals(emptyList<IntRange>(), matchRanges("ab", "abc"))
    }

    @Test fun `sectionMatches true when a term contains query`() {
        assertTrue(sectionMatches(listOf("Darstellung", "Theme Hell Dunkel"), "dunkel"))
    }

    @Test fun `sectionMatches false when no term contains query`() {
        assertFalse(sectionMatches(listOf("Sprache", "Deutsch English"), "theme"))
    }

    @Test fun `sectionMatches blank query is false`() {
        assertFalse(sectionMatches(listOf("Anything"), "  "))
    }
}
