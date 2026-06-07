package com.komgareader.guidedview

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GuidedNavigatorTest {

    // Seite 0: 3 Einheiten, Seite 1: 1 Einheit (Splash), Seite 2: 2 Einheiten
    private val units = listOf(3, 1, 2)
    private val pageCount = units.size
    private val unitsAt: (Int) -> Int = { units[it] }

    @Test
    fun `vorwärts innerhalb der Seite`() {
        assertEquals(GuidedPosition(0, 1), GuidedNavigator.next(GuidedPosition(0, 0), pageCount, unitsAt))
    }

    @Test
    fun `vorwärts über letztes Panel springt auf nächste Seite Einheit 0`() {
        assertEquals(GuidedPosition(1, 0), GuidedNavigator.next(GuidedPosition(0, 2), pageCount, unitsAt))
    }

    @Test
    fun `vorwärts am Buchende ergibt null`() {
        assertNull(GuidedNavigator.next(GuidedPosition(2, 1), pageCount, unitsAt))
    }

    @Test
    fun `rückwärts innerhalb der Seite`() {
        assertEquals(GuidedPosition(0, 1), GuidedNavigator.previous(GuidedPosition(0, 2), pageCount, unitsAt))
    }

    @Test
    fun `rückwärts vor Einheit 0 springt auf Vorseite letzte Einheit`() {
        assertEquals(GuidedPosition(0, 2), GuidedNavigator.previous(GuidedPosition(1, 0), pageCount, unitsAt))
    }

    @Test
    fun `rückwärts am Buchanfang ergibt null`() {
        assertNull(GuidedNavigator.previous(GuidedPosition(0, 0), pageCount, unitsAt))
    }
}
