package com.komgareader.guidedview

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PanelGeometryTest {

    // Seite 1000x800; Panel links-oben 0..400 x 0..400
    private val panel = PanelRect(0, 0, 400, 400)

    @Test
    fun `normalize rechnet Detektions-Pixel in 0 bis 1`() {
        val n = PanelGeometry.normalize(panel, pageW = 1000, pageH = 800)
        assertEquals(0f, n.left); assertEquals(0f, n.top)
        assertEquals(0.4f, n.width); assertEquals(0.5f, n.height)
    }

    @Test
    fun `hitTest trifft das Panel das den Punkt enthält`() {
        val a = PanelGeometry.normalize(PanelRect(0, 0, 400, 800), 1000, 800)   // linke Spalte
        val b = PanelGeometry.normalize(PanelRect(600, 0, 400, 800), 1000, 800) // rechte Spalte
        // Punkt bei x=0.1,y=0.5 liegt in a (links)
        assertEquals(0, PanelGeometry.hitTest(0.1f, 0.5f, listOf(a, b)))
        // Punkt bei x=0.8 liegt in b (rechts)
        assertEquals(1, PanelGeometry.hitTest(0.8f, 0.5f, listOf(a, b)))
    }

    @Test
    fun `hitTest im Gutter trifft kein Panel`() {
        val a = PanelGeometry.normalize(PanelRect(0, 0, 400, 800), 1000, 800)
        val b = PanelGeometry.normalize(PanelRect(600, 0, 400, 800), 1000, 800)
        // x=0.5 liegt im Gutter zwischen 0.4 und 0.6
        assertNull(PanelGeometry.hitTest(0.5f, 0.5f, listOf(a, b)))
    }

    @Test
    fun `zoomScale füllt das größere Panel-Maß abzüglich Rand`() {
        val n = PanelGeometry.normalize(panel, 1000, 800) // width 0.4, height 0.5
        // größeres Maß = 0.5; Rand 0.05 -> Scale = (1 - 2*0.05) / 0.5 = 1.8
        val s = PanelGeometry.zoomScale(n, marginFraction = 0.05f)
        assertTrue(kotlin.math.abs(s - 1.8f) < 0.001f, "Scale war $s")
    }
}
