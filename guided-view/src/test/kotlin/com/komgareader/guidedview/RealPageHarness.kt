package com.komgareader.guidedview

import com.komgareader.domain.render.RenderedPage
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * MANUELLER Harness (nicht in CI): läuft PanelDetector gegen echte Seiten in
 * guided-view/realpages/ (gitignored), skaliert wie ComicPageLoader auf ~1000px und schreibt
 * Overlay-PNGs mit Panel-Boxen + Reihenfolge nach guided-view/realpages/_out/.
 * Aktivieren: @Disabled entfernen und `./gradlew :guided-view:test --tests "*RealPageHarness*"`.
 */
@Disabled("Manueller Harness — lokal aktivieren")
class RealPageHarness {

    private val detector = PanelDetector()
    private val detectionWidth = 1000

    @Test
    fun `Panels auf echten Seiten visualisieren`() {
        val root = File("realpages")
        val out = File(root, "_out").apply { mkdirs() }
        val files = root.walkTopDown().filter {
            it.isFile && it.extension.lowercase() in setOf("jpg", "jpeg", "png") && !it.path.contains("_out")
        }.sorted().toList()
        println("Harness: ${files.size} Seiten")
        for (f in files) {
            val src = ImageIO.read(f) ?: continue
            val scaled = downscale(src)
            val page = toRenderedPage(scaled)
            val panels = detector.detect(page, ReadingDirection.LEFT_TO_RIGHT)
            drawOverlay(scaled, panels, File(out, f.nameWithoutExtension + "_panels.png"))
            println("${f.name}: ${panels.size} Panels")
        }
    }

    private fun downscale(src: BufferedImage): BufferedImage {
        if (src.width <= detectionWidth) return src
        val r = detectionWidth.toDouble() / src.width
        val h = (src.height * r).toInt()
        val dst = BufferedImage(detectionWidth, h, BufferedImage.TYPE_INT_ARGB)
        val g = dst.createGraphics()
        g.drawImage(src.getScaledInstance(detectionWidth, h, java.awt.Image.SCALE_AREA_AVERAGING), 0, 0, null)
        g.dispose()
        return dst
    }

    private fun toRenderedPage(b: BufferedImage): RenderedPage {
        val px = IntArray(b.width * b.height)
        b.getRGB(0, 0, b.width, b.height, px, 0, b.width)
        return RenderedPage(b.width, b.height, px)
    }

    private fun drawOverlay(base: BufferedImage, panels: List<PanelRect>, dest: File) {
        val img = BufferedImage(base.width, base.height, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.drawImage(base, 0, 0, null)
        g.color = Color.RED
        panels.forEachIndexed { i, p ->
            g.drawRect(p.x, p.y, p.width - 1, p.height - 1)
            g.drawString("#$i", p.x + 4, p.y + 16)
        }
        g.dispose()
        ImageIO.write(img, "png", dest)
    }
}
