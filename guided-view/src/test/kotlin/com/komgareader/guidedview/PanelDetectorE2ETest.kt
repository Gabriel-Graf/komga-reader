package com.komgareader.guidedview

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

    @Test
    fun `gemischtes Layout - breites Top-Panel plus zwei Panels darunter - LTR`() {
        val w = 600
        val h = 800
        val gutter = 40

        val topPanelH = 340
        val topPanel = PanelRect(0, 0, w, topPanelH)

        val bottomY = topPanelH + gutter
        val bottomH = h - bottomY
        val panelW = (w - gutter) / 2
        val bottomLeft = PanelRect(0, bottomY, panelW, bottomH)
        val bottomRight = PanelRect(panelW + gutter, bottomY, panelW, bottomH)

        val pg = SyntheticPage.of(w, h, listOf(topPanel, bottomLeft, bottomRight))
        val panels = detector.detect(pg, ReadingDirection.LEFT_TO_RIGHT)

        assertEquals(3, panels.size, "Erwarte genau 3 Panels (oben + unten-links + unten-rechts)")

        assertTrue(
            panels[0].centerY < h / 2,
            "Panel[0] sollte in der oberen Hälfte liegen, centerY=${panels[0].centerY}"
        )
        assertTrue(
            panels[1].centerY > h / 2,
            "Panel[1] sollte in der unteren Hälfte liegen, centerY=${panels[1].centerY}"
        )
        assertTrue(
            panels[1].centerX < w / 2,
            "Panel[1] sollte links sein (LTR), centerX=${panels[1].centerX}"
        )
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
