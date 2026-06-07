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
        val lightBg = ImageBinarization.isLightBackground(page)
        val background = ImageBinarization.backgroundMask(page, threshold, lightBg)
        val flooded = GutterFill.floodFromEdges(background, page.width, page.height)
        val regions = RegionLabeling.labelRegions(flooded, page.width, page.height)

        val minArea = page.width.toLong() * page.height * minPanelAreaFraction
        val filtered = regions.filter { it.width.toLong() * it.height >= minArea }

        // Dunkle Maske: immer echte dunkle Pixel (Otsu-Klasse [0..T], unabhängig von der Hintergrund-Polarität),
        // da Rahmengitter stets dunkle Linien sind.
        val darkMask = BooleanArray(background.size) { ImageBinarization.luminance(page.pixels[it]) <= threshold }
        // Seitenüberspannende Komponenten (schwarze Rahmengitter ohne Weißgutter) aufteilen
        val expanded = filtered.flatMap { box ->
            val wide = box.width > page.width * 0.6 && box.height > page.height * 0.5
            val bandlike = box.width > page.width * 0.85 || box.height > page.height * 0.85
            if (wide || bandlike) BorderLineSplit.split(darkMask, page.width, page.height, box) else listOf(box)
        }
        val sized = expanded.filter { it.width.toLong() * it.height >= minArea }
        val deSolid = dropSolidBlobs(sized, darkMask, page.width, page.height)
        val deBubbled = dropContainedSmall(deSolid, page.width, page.height)
        val merged = dropContained(deBubbled)
        return ReadingOrder.sort(merged, direction)
    }

    /**
     * Verwirft kleine, fast vollständig dunkle Boxen (Soundeffekt-Buchstaben wie „BLAM",
     * solide Silhouetten) — die sind keine Panels. Echte (auch kleine) Panels haben helle
     * Flächen/Sprechblasen, ihr Dunkelanteil bleibt unter [minDarkFill]. Flächen-gegatet
     * ([maxAreaFraction]), damit nur kleine Blobs betroffen sind, nie ganze Panels.
     */
    private fun dropSolidBlobs(
        boxes: List<PanelRect>, dark: BooleanArray, pageW: Int, pageH: Int,
        maxAreaFraction: Double = 0.10, minDarkFill: Double = 0.72,
    ): List<PanelRect> {
        val pageArea = pageW.toLong() * pageH
        return boxes.filterNot { b ->
            val area = b.width.toLong() * b.height
            if (area >= pageArea * maxAreaFraction) return@filterNot false
            var d = 0L
            val x1 = (b.x + b.width).coerceAtMost(pageW); val y1 = (b.y + b.height).coerceAtMost(pageH)
            for (y in b.y until y1) for (x in b.x until x1) if (dark[y * pageW + x]) d++
            val fill = d.toDouble() / area.coerceAtLeast(1)
            fill >= minDarkFill
        }
    }

    /**
     * Verwirft kleine Boxen (< [smallAreaFraction] Seitenfläche), die vollständig in einer
     * größeren liegen (Sprechblasen).
     */
    private fun dropContainedSmall(
        boxes: List<PanelRect>, pageW: Int, pageH: Int, smallAreaFraction: Double = 0.06,
    ): List<PanelRect> {
        val pageArea = pageW.toLong() * pageH
        return boxes.filterNot { b ->
            val small = b.width.toLong() * b.height < pageArea * smallAreaFraction
            small && boxes.any { o ->
                o !== b && o.width.toLong() * o.height > b.width.toLong() * b.height &&
                    b.x >= o.x && b.y >= o.y && b.x + b.width <= o.x + o.width && b.y + b.height <= o.y + o.height
            }
        }
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
