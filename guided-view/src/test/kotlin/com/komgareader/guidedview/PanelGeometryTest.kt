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
    fun `maxAreaFraction liefert den größten Flächenanteil`() {
        val a = NormRect(0f, 0f, 0.3f, 0.4f)
        val b = NormRect(0.5f, 0.5f, 0.4f, 0.5f)
        assertTrue(kotlin.math.abs(PanelGeometry.maxAreaFraction(listOf(a, b)) - 0.20f) < 1e-4f)
        assertEquals(0f, PanelGeometry.maxAreaFraction(emptyList()))
    }

    @Test
    fun `fitScale füllt das Panel im Viewport mit Rand`() {
        val panel = NormRect(0f, 0f, 0.5f, 0.5f)
        val s = PanelGeometry.fitScale(panel, contentW = 1000f, contentH = 1500f, viewportW = 1000f, viewportH = 1500f, marginFraction = 0.05f)
        assertTrue(kotlin.math.abs(s - 1.8f) < 1e-3f, "war $s")
    }

    @Test
    fun `fitScale berücksichtigt Letterbox (schmaleres Content-Rechteck)`() {
        val panel = NormRect(0f, 0f, 1.0f, 0.25f)
        val s = PanelGeometry.fitScale(panel, contentW = 800f, contentH = 1500f, viewportW = 1000f, viewportH = 1500f, marginFraction = 0f)
        assertTrue(kotlin.math.abs(s - 1.25f) < 1e-3f, "war $s")
    }
}
