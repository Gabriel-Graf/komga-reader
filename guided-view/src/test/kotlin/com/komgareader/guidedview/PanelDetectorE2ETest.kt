package com.komgareader.guidedview

import com.komgareader.domain.render.RenderedPage
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * E2E-Test mit einem „realistischen" gemischten Layout:
 * - Oben ein breites Panel über die volle Seitenbreite
 * - Darunter zwei nebeneinanderliegende Panels (links + rechts)
 *
 * Erwartete Erkennung LTR: oben, dann unten-links, dann unten-rechts.
 */
class PanelDetectorE2ETest {

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

    @Test
    fun `gemischtes Layout - breites Top-Panel plus zwei Panels darunter - LTR`() {
        val w = 600
        val h = 800
        val gutter = 40

        // Oberes breites Panel: volle Breite, obere Hälfte (minus Rand)
        val topPanelH = 340
        val topPanel = PanelRect(0, 0, w, topPanelH)

        // Untere zwei Panels nebeneinander
        val bottomY    = topPanelH + gutter
        val bottomH    = h - bottomY
        val panelW     = (w - gutter) / 2   // 280
        val bottomLeft  = PanelRect(0,              bottomY, panelW, bottomH)
        val bottomRight = PanelRect(panelW + gutter, bottomY, panelW, bottomH)

        val pg = page(w, h, listOf(topPanel, bottomLeft, bottomRight))

        val panels = detector.detect(pg, ReadingDirection.LEFT_TO_RIGHT)

        assertEquals(3, panels.size, "Erwarte genau 3 Panels (oben + unten-links + unten-rechts)")

        // Panel[0] = oberes breites Panel → Mittelpunkt in oberer Hälfte
        assertTrue(
            panels[0].centerY < h / 2,
            "Panel[0] sollte in der oberen Hälfte liegen, centerY=${panels[0].centerY}"
        )

        // Panel[1] = unten-links → Mittelpunkt unten + links
        assertTrue(
            panels[1].centerY > h / 2,
            "Panel[1] sollte in der unteren Hälfte liegen, centerY=${panels[1].centerY}"
        )
        assertTrue(
            panels[1].centerX < w / 2,
            "Panel[1] sollte links sein (LTR), centerX=${panels[1].centerX}"
        )

        // Panel[2] = unten-rechts → Mittelpunkt unten + rechts
        assertTrue(
            panels[2].centerY > h / 2,
            "Panel[2] sollte in der unteren Hälfte liegen, centerY=${panels[2].centerY}"
        )
        assertTrue(
            panels[2].centerX > w / 2,
            "Panel[2] sollte rechts sein (LTR), centerX=${panels[2].centerX}"
        )
    }
}
