package com.komgareader.domain.eink

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Geräteunabhängige, deterministische Refresh-Entscheidung: PARTIAL beim Blättern, FULL als
 * Ghosting-Promotion nach N PARTIALs **und** sofort bei bewusstem Bildwechsel (Frame/Panel).
 * Ersetzt die fragile, pro-Reader unterschiedliche Index-Modulo-Logik durch EINE getestete Quelle.
 */
class RefreshSchedulerTest {

    @Test fun `blaettern liefert PARTIAL bis zur Promotion, dann FULL`() {
        val s = RefreshScheduler(ghostClearInterval = 3)
        assertEquals(RefreshMode.PARTIAL, s.onContentChange()) // 1
        assertEquals(RefreshMode.PARTIAL, s.onContentChange()) // 2
        assertEquals(RefreshMode.FULL, s.onContentChange())    // 3 → Promotion
        assertEquals(RefreshMode.PARTIAL, s.onContentChange()) // Zähler zurückgesetzt
    }

    @Test fun `bewusster Bildwechsel erzwingt sofort FULL und setzt den Promotion-Zaehler zurueck`() {
        val s = RefreshScheduler(ghostClearInterval = 3)
        s.onContentChange() // 1 partial
        s.onContentChange() // 2 partials
        assertEquals(RefreshMode.FULL, s.onContentChange(forceFull = true)) // sofort FULL, reset
        // Nach dem reset zählt es wieder von vorn — nicht sofort wieder FULL.
        assertEquals(RefreshMode.PARTIAL, s.onContentChange())
        assertEquals(RefreshMode.PARTIAL, s.onContentChange())
        assertEquals(RefreshMode.FULL, s.onContentChange())
    }

    @Test fun `reset setzt den Promotion-Zaehler zurueck`() {
        val s = RefreshScheduler(ghostClearInterval = 2)
        s.onContentChange()
        s.reset()
        assertEquals(RefreshMode.PARTIAL, s.onContentChange())
    }

    @Test fun `mergeRegions bildet die Bounding-Box, leer ist null`() {
        assertNull(RefreshScheduler.mergeRegions(emptyList()))
        assertEquals(
            Region(10, 20, 30, 40),
            RefreshScheduler.mergeRegions(listOf(Region(10, 20, 30, 40))),
        )
        // zwei disjunkte Rechtecke → umschließende Box
        assertEquals(
            Region(0, 0, 100, 50),
            RefreshScheduler.mergeRegions(listOf(Region(0, 0, 40, 10), Region(80, 30, 20, 20))),
        )
    }
}
