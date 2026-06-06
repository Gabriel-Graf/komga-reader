package com.komgareader.guidedview

import com.komgareader.domain.render.RenderedPage
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit-Tests für den PanelDetector (rekursiver XY-Cut).
 *
 * Alle Tests verwenden eine 600×800-Seite mit klaren 40px-Guttern zwischen
 * massiv schwarzen Panel-Blöcken. Das macht die Erkennung eindeutig.
 */
class PanelDetectorTest {

    private val detector = PanelDetector()

    /** Baut eine RenderedPage (weiß), füllt gegebene Rects dunkel (schwarz). */
    private fun page(w: Int, h: Int, panels: List<PanelRect>): RenderedPage {
        val px = IntArray(w * h) { 0xFFFFFFFF.toInt() }
        for (p in panels)
            for (y in p.y until p.y + p.height)
                for (x in p.x until p.x + p.width)
                    px[y * w + x] = 0xFF000000.toInt()
        return RenderedPage(w, h, px)
    }

    // -----------------------------------------------------------------------
    // Test 1: Eine ganzseitige Fläche → genau 1 Panel, ~Vollseite
    // -----------------------------------------------------------------------
    @Test
    fun `einzelner Vollseiten-Block ergibt genau ein Panel`() {
        val w = 600; val h = 800
        // Schmale Ränder (20px) freilassen, Rest dunkel
        val darkRect = PanelRect(20, 20, w - 40, h - 40)
        val pg = page(w, h, listOf(darkRect))

        val panels = detector.detect(pg, ReadingDirection.LEFT_TO_RIGHT)

        assertEquals(1, panels.size, "Erwarte genau 1 Panel für eine einzelne Fläche")
        val p = panels[0]
        // Das erkannte Panel sollte grob die gesamte dunkle Fläche umfassen
        assertTrue(p.x <= 30, "Panel-X sollte nahe 0 sein, war ${p.x}")
        assertTrue(p.y <= 30, "Panel-Y sollte nahe 0 sein, war ${p.y}")
        assertTrue(p.width >= w - 80, "Panel-Breite sollte groß sein, war ${p.width}")
        assertTrue(p.height >= h - 80, "Panel-Höhe sollte groß sein, war ${p.height}")
    }

    // -----------------------------------------------------------------------
    // Test 2: 2×2-Raster LTR → 4 Panels, Reihenfolge oben-links, oben-rechts,
    //         unten-links, unten-rechts
    // -----------------------------------------------------------------------
    @Test
    fun `2x2-Raster wird LTR in korrekter Reihenfolge erkannt`() {
        val w = 600; val h = 800
        val gutter = 40
        val panelW = (w - gutter) / 2       // 280
        val panelH = (h - gutter) / 2       // 380

        val topLeft     = PanelRect(0,              0,              panelW, panelH)
        val topRight    = PanelRect(panelW + gutter, 0,              panelW, panelH)
        val bottomLeft  = PanelRect(0,              panelH + gutter, panelW, panelH)
        val bottomRight = PanelRect(panelW + gutter, panelH + gutter, panelW, panelH)

        val pg = page(w, h, listOf(topLeft, topRight, bottomLeft, bottomRight))
        val panels = detector.detect(pg, ReadingDirection.LEFT_TO_RIGHT)

        assertEquals(4, panels.size, "Erwarte 4 Panels im 2×2-Raster")

        // Reihenfolge LTR: oben-links, oben-rechts, unten-links, unten-rechts
        val centers = panels.map { it.centerX to it.centerY }
        // oben-links hat kleinstes Y und kleinstes X
        assertTrue(centers[0].second < h / 2, "Panel[0] sollte oben sein, Y-Mitte=${centers[0].second}")
        assertTrue(centers[0].first  < w / 2, "Panel[0] sollte links sein, X-Mitte=${centers[0].first}")
        // oben-rechts
        assertTrue(centers[1].second < h / 2, "Panel[1] sollte oben sein, Y-Mitte=${centers[1].second}")
        assertTrue(centers[1].first  > w / 2, "Panel[1] sollte rechts sein, X-Mitte=${centers[1].first}")
        // unten-links
        assertTrue(centers[2].second > h / 2, "Panel[2] sollte unten sein, Y-Mitte=${centers[2].second}")
        assertTrue(centers[2].first  < w / 2, "Panel[2] sollte links sein, X-Mitte=${centers[2].first}")
        // unten-rechts
        assertTrue(centers[3].second > h / 2, "Panel[3] sollte unten sein, Y-Mitte=${centers[3].second}")
        assertTrue(centers[3].first  > w / 2, "Panel[3] sollte rechts sein, X-Mitte=${centers[3].first}")
    }

    // -----------------------------------------------------------------------
    // Test 3: 2×2-Raster RTL → oben-rechts vor oben-links
    // -----------------------------------------------------------------------
    @Test
    fun `2x2-Raster wird RTL in korrekter Reihenfolge erkannt`() {
        val w = 600; val h = 800
        val gutter = 40
        val panelW = (w - gutter) / 2
        val panelH = (h - gutter) / 2

        val topLeft     = PanelRect(0,              0,              panelW, panelH)
        val topRight    = PanelRect(panelW + gutter, 0,              panelW, panelH)
        val bottomLeft  = PanelRect(0,              panelH + gutter, panelW, panelH)
        val bottomRight = PanelRect(panelW + gutter, panelH + gutter, panelW, panelH)

        val pg = page(w, h, listOf(topLeft, topRight, bottomLeft, bottomRight))
        val panels = detector.detect(pg, ReadingDirection.RIGHT_TO_LEFT)

        assertEquals(4, panels.size, "Erwarte 4 Panels im 2×2-Raster (RTL)")

        val centers = panels.map { it.centerX to it.centerY }
        // RTL: oben-rechts zuerst
        assertTrue(centers[0].second < h / 2, "Panel[0] sollte oben sein")
        assertTrue(centers[0].first  > w / 2, "Panel[0] sollte rechts sein (RTL)")
        // dann oben-links
        assertTrue(centers[1].second < h / 2, "Panel[1] sollte oben sein")
        assertTrue(centers[1].first  < w / 2, "Panel[1] sollte links sein (RTL)")
    }

    // -----------------------------------------------------------------------
    // Test 4: 3 horizontale Streifen → 3 Panels von oben nach unten
    // -----------------------------------------------------------------------
    @Test
    fun `drei horizontale Streifen werden von oben nach unten erkannt`() {
        val w = 600; val h = 800
        val gutter = 40
        val stripeH = (h - 2 * gutter) / 3   // ~240

        val stripe1 = PanelRect(0, 0,                   w, stripeH)
        val stripe2 = PanelRect(0, stripeH + gutter,    w, stripeH)
        val stripe3 = PanelRect(0, 2 * (stripeH + gutter), w, stripeH)

        val pg = page(w, h, listOf(stripe1, stripe2, stripe3))
        val panels = detector.detect(pg, ReadingDirection.LEFT_TO_RIGHT)

        assertEquals(3, panels.size, "Erwarte 3 Panels für 3 horizontale Streifen")

        // Reihenfolge oben → unten anhand Y-Mittelpunkt
        assertTrue(panels[0].centerY < panels[1].centerY, "Panel[0] sollte über Panel[1] liegen")
        assertTrue(panels[1].centerY < panels[2].centerY, "Panel[1] sollte über Panel[2] liegen")
    }

    // -----------------------------------------------------------------------
    // Test 5: Leere/weiße Seite → leere Liste
    // -----------------------------------------------------------------------
    @Test
    fun `leere weisse Seite ergibt eine leere Panel-Liste`() {
        val pg = page(600, 800, emptyList())
        val panels = detector.detect(pg, ReadingDirection.LEFT_TO_RIGHT)
        assertTrue(panels.isEmpty(), "Leere Seite sollte keine Panels liefern, war $panels")
    }
}
