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

    @Test
    fun `schwarz-umrandetes 2x2 ohne Weissgutter ergibt 4 Panels`() {
        val px = IntArray(1000 * 800) { 0xFFFFFFFF.toInt() }
        fun vline(x: Int) { for (y in 0 until 800) for (dx in 0..5) px[y * 1000 + (x + dx)] = 0xFF101010.toInt() }
        fun hline(y: Int) { for (x in 0 until 1000) for (dy in 0..5) px[(y + dy) * 1000 + x] = 0xFF101010.toInt() }
        hline(10); hline(395); hline(784); vline(10); vline(495); vline(984)
        for (q in 0..1) for (r in 0..1) {
            val ox = 60 + r * 485; val oy = 60 + q * 385
            for (y in oy until oy + 250) for (x in ox until ox + 350) px[y * 1000 + x] = 0xFF808080.toInt()
        }
        val page = com.komgareader.domain.render.RenderedPage(1000, 800, px)
        val out = PanelDetector().detect(page, ReadingDirection.LEFT_TO_RIGHT)
        assertEquals(4, out.size, "Erwarte 4 Panels (Rahmen-Split), war ${out.size}")
    }

    @Test
    fun `Sprechblase wird nicht als eigenes Panel gezählt`() {
        val panel = PanelRect(50, 50, 900, 700)
        val px = IntArray(1000 * 800) { 0xFFFFFFFF.toInt() }
        for (y in panel.y until panel.y + panel.height) for (x in panel.x until panel.x + panel.width)
            px[y * 1000 + x] = 0xFF303030.toInt()
        for (y in 300 until 450) for (x in 400 until 600) px[y * 1000 + x] = 0xFFFFFFFF.toInt()
        for (y in 360 until 380) for (x in 440 until 560) px[y * 1000 + x] = 0xFF101010.toInt()
        val page = com.komgareader.domain.render.RenderedPage(1000, 800, px)
        val out = PanelDetector().detect(page, ReadingDirection.LEFT_TO_RIGHT)
        assertEquals(1, out.size, "Blase darf kein eigenes Panel sein, war ${out.size}")
    }

    @Test
    fun `solider SFX-Blob wird verworfen, helles Panel bleibt`() {
        val px = IntArray(1000 * 800) { 0xFFFFFFFF.toInt() }
        // Helles, umrandetes Panel (Rahmen dunkel, Innen hell + etwas Inhalt) -> niedriger Dunkelanteil
        for (y in 50 until 450) for (x in 50 until 450) {
            val border = x < 56 || x >= 444 || y < 56 || y >= 444
            if (border) px[y * 1000 + x] = 0xFF101010.toInt()
        }
        for (y in 100 until 160) for (x in 100 until 300) px[y * 1000 + x] = 0xFF202020.toInt() // wenig Inhalt
        // Solider schwarzer SFX-Blob (klein, ~vollständig dunkel)
        for (y in 600 until 720) for (x in 600 until 740) px[y * 1000 + x] = 0xFF050505.toInt()
        val page = com.komgareader.domain.render.RenderedPage(1000, 800, px)
        val out = PanelDetector().detect(page, ReadingDirection.LEFT_TO_RIGHT)
        assertEquals(1, out.size, "Erwarte nur das helle Panel (SFX-Blob verworfen), war ${out.size}")
        assertTrue(out[0].x < 460 && out[0].y < 460, "Das verbliebene Panel ist das umrandete oben-links")
    }

    @Test
    fun `dunkle Seite mit schwarzem Hintergrund trennt helle Panels`() {
        // Seite überwiegend SCHWARZ; zwei hellere Panels nebeneinander, getrennt durch schwarzen Gutter
        val px = IntArray(1000 * 800) { 0xFF0A0A0A.toInt() }
        fun fillPanel(ox: Int) {
            for (y in 80 until 720) for (x in ox until ox + 380) px[y * 1000 + x] = 0xFFB0B0B0.toInt()
        }
        fillPanel(80)   // linkes Panel x=80..459
        fillPanel(540)  // rechtes Panel x=540..919 (schwarzer Gutter 460..539)
        val page = com.komgareader.domain.render.RenderedPage(1000, 800, px)
        val out = PanelDetector().detect(page, ReadingDirection.LEFT_TO_RIGHT)
        assertEquals(2, out.size, "Erwarte 2 helle Panels auf schwarzem Hintergrund, war ${out.size}")
    }

    @Test
    fun `eng benachbarte Fragmente werden zu einem Panel vereint`() {
        // Vier kleine helle Fragmente mit WINZIGEN dunklen Linien dazwischen (wie Figur-Details)
        // auf weißem Hintergrund -> sollten zu EINEM Panel mergen, nicht 4 bleiben.
        val px = IntArray(1000 * 800) { 0xFFFFFFFF.toInt() }
        // ein 400x400-Bereich, in 2x2 Fragmente durch 2px dunkle Linien geteilt
        for (y in 100 until 500) for (x in 100 until 500) px[y * 1000 + x] = 0xFF404040.toInt()
        for (y in 100 until 500) for (d in 0..1) px[y * 1000 + (300 + d)] = 0xFF000000.toInt() // vertikale 2px-Linie
        for (x in 100 until 500) for (d in 0..1) px[(300 + d) * 1000 + x] = 0xFF000000.toInt() // horizontale 2px-Linie
        val page = com.komgareader.domain.render.RenderedPage(1000, 800, px)
        val out = PanelDetector().detect(page, ReadingDirection.LEFT_TO_RIGHT)
        assertEquals(1, out.size, "Erwarte 1 vereintes Panel (winzige Lücken), war ${out.size}")
    }

}
