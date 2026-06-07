package com.komgareader.guidedview

import com.komgareader.domain.render.RenderedPage

/**
 * Erkennt Panels via Hybrid Flood-Fill + Connected-Components (reines Kotlin, host-testbar):
 * Otsu-Binarisierung → Edge-Seed-Gutter-Flood → Component-Bounding-Boxes → Filter/Merge →
 * Lesereihenfolge. Das weiße Gutter-Netz ist vom Seitenrand zusammenhängend, daher trennt die
 * Flutung Panels auch bei in die Gasse ragender Art (anders als der frühere XY-Cut).
 *
 * @param minPanelAreaFraction  Boxen kleiner als dieser Seitenflächen-Anteil werden verworfen.
 * @param containmentFraction   Box gilt als „enthalten" wenn dieser Anteil ihrer Fläche in einer größeren liegt.
 */
class PanelDetector(
    private val minPanelAreaFraction: Double = 0.01,
    private val containmentFraction: Double = 0.8,
) {

    fun detect(page: RenderedPage, direction: ReadingDirection): List<PanelRect> {
        if (page.width <= 0 || page.height <= 0 || page.pixels.isEmpty()) return emptyList()
        val threshold = ImageBinarization.otsuThreshold(page)
        val background = ImageBinarization.backgroundMask(page, threshold)
        val flooded = GutterFill.floodFromEdges(background, page.width, page.height)
        val regions = RegionLabeling.labelRegions(flooded, page.width, page.height)

        val minArea = page.width.toLong() * page.height * minPanelAreaFraction
        val filtered = regions.filter { it.width.toLong() * it.height >= minArea }
        val merged = dropContained(filtered)
        return ReadingOrder.sort(merged, direction)
    }

    /** Entfernt Boxen, die zu [containmentFraction] in einer anderen (größeren) liegen. */
    private fun dropContained(boxes: List<PanelRect>): List<PanelRect> {
        val bySize = boxes.sortedByDescending { it.width.toLong() * it.height }
        val kept = mutableListOf<PanelRect>()
        for (b in bySize) {
            val area = b.width.toLong() * b.height
            val contained = kept.any { k ->
                val ix = maxOf(b.x, k.x); val iy = maxOf(b.y, k.y)
                val ax = minOf(b.x + b.width, k.x + k.width); val ay = minOf(b.y + b.height, k.y + k.height)
                val iw = ax - ix; val ih = ay - iy
                if (iw <= 0 || ih <= 0) false
                else iw.toLong() * ih >= area * containmentFraction
            }
            if (!contained) kept.add(b)
        }
        return kept
    }
}
