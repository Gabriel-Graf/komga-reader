package com.komgareader.guidedview

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PanelDetectorTest {
    private val det = PanelDetector()

    @Test
    fun `sauberes 3x2-Raster ergibt 6 Panels in LTR-Reihenfolge`() {
        val panels = mutableListOf<PanelRect>()
        val pw = 300; val ph = 370; val gx = 20; val gy = 20; val m = 20
        for (row in 0..1) for (col in 0..2) {
            panels.add(PanelRect(m + col * (pw + gx), m + row * (ph + gy), pw, ph))
        }
        val page = SyntheticPage.of(1000, 800, panels)
        val out = det.detect(page, ReadingDirection.LEFT_TO_RIGHT)
        assertEquals(6, out.size, "Erwarte 6 Panels, war ${out.size}")
        assertTrue(out[0].x < out[1].x && out[1].x < out[2].x)
        assertTrue(out[0].y < out[3].y)
    }

    @Test
    fun `Sprechblase im Panel bleibt ein Panel`() {
        val panel = PanelRect(50, 50, 900, 700)
        val bubble = PanelRect(400, 300, 200, 150)
        val page = SyntheticPage.of(1000, 800, listOf(panel), holes = listOf(bubble))
        val out = det.detect(page, ReadingDirection.LEFT_TO_RIGHT)
        assertEquals(1, out.size)
    }

    @Test
    fun `Full-Bleed bis zur Kante bleibt erhalten`() {
        val left = PanelRect(0, 0, 460, 800)
        val right = PanelRect(500, 20, 480, 760)
        val page = SyntheticPage.of(1000, 800, listOf(left, right))
        val out = det.detect(page, ReadingDirection.LEFT_TO_RIGHT)
        assertEquals(2, out.size)
    }

    @Test
    fun `Blank-Seite ergibt keine Panels`() {
        val page = SyntheticPage.of(1000, 800, emptyList())
        assertEquals(0, det.detect(page, ReadingDirection.LEFT_TO_RIGHT).size)
    }

    @Test
    fun `Einzelnes Vollseiten-Panel ergibt genau ein Panel`() {
        val page = SyntheticPage.of(1000, 800, listOf(PanelRect(20, 20, 960, 760)))
        assertEquals(1, det.detect(page, ReadingDirection.LEFT_TO_RIGHT).size)
    }

    @Test
    fun `Mini-Fleck unter Min-Fläche wird verworfen`() {
        val panel = PanelRect(40, 40, 900, 700)
        val speck = PanelRect(10, 10, 8, 8)
        val page = SyntheticPage.of(1000, 800, listOf(panel, speck))
        assertEquals(1, det.detect(page, ReadingDirection.LEFT_TO_RIGHT).size)
    }

    @Test
    fun `Art ragt in die Gasse - Panels trennen trotzdem`() {
        // Zwei Spalten mit 30px-Gasse (x=460..489). Ein Vorsprung aus dem linken Panel
        // ragt bis x=474 in die Gasse, blockt sie aber NICHT ganz (weiß bleibt x=475..489).
        // Der frühere XY-Cut wäre an der Tinte in dieser Spalte gescheitert; der Flood-Fill
        // findet weiterhin einen weißen Durchgang und trennt.
        val left = PanelRect(20, 20, 440, 760)        // 20..459
        val right = PanelRect(490, 20, 490, 760)       // 490..979
        val intrusion = PanelRect(460, 380, 15, 40)    // ragt in die Gasse, hängt am linken Panel
        val page = SyntheticPage.of(1000, 800, listOf(left, right, intrusion))
        val out = det.detect(page, ReadingDirection.LEFT_TO_RIGHT)
        assertEquals(2, out.size, "Erwarte 2 getrennte Panels trotz Gassen-Vorsprung, war ${out.size}")
    }

    @Test
    fun `Otsu segmentiert auch bei nicht-weißem Hintergrund`() {
        // Hellgrauer Seitenhintergrund statt reinweiß: eine feste Schwelle (128) würde noch
        // funktionieren, aber dies prüft, dass Otsu die Trennung adaptiv hinbekommt.
        val panels = listOf(PanelRect(20, 20, 440, 760), PanelRect(500, 20, 480, 760))
        val page = SyntheticPage.of(1000, 800, panels, bg = 0xFFD0D0D0.toInt())
        val out = det.detect(page, ReadingDirection.LEFT_TO_RIGHT)
        assertEquals(2, out.size, "Erwarte 2 Panels bei grauem Hintergrund, war ${out.size}")
    }
}
