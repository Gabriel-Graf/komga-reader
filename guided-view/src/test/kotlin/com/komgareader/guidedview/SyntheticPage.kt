package com.komgareader.guidedview

import com.komgareader.domain.render.RenderedPage

/** Baut synthetische Comicseiten als RenderedPage für Detektor-Tests. */
object SyntheticPage {
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val BLACK = 0xFF101010.toInt()

    /**
     * Weiße Seite [w]x[h]; jedes Rechteck in [panels] wird dunkel gefüllt (Panel-Inhalt).
     * [holes] werden danach wieder weiß gefüllt (z. B. Sprechblasen-Inseln).
     */
    fun of(w: Int, h: Int, panels: List<PanelRect>, holes: List<PanelRect> = emptyList()): RenderedPage {
        val px = IntArray(w * h) { WHITE }
        fun fill(r: PanelRect, color: Int) {
            for (y in r.y until r.y + r.height) for (x in r.x until r.x + r.width) {
                if (x in 0 until w && y in 0 until h) px[y * w + x] = color
            }
        }
        panels.forEach { fill(it, BLACK) }
        holes.forEach { fill(it, WHITE) }
        return RenderedPage(w, h, px)
    }
}
