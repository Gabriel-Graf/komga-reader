package com.komgareader.app.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.core.graphics.scale
import coil.ImageLoader
import coil.request.ImageRequest
import com.komgareader.domain.render.RenderedPage
import com.komgareader.guidedview.PanelDetector
import com.komgareader.guidedview.PanelRect
import com.komgareader.guidedview.ReadingDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Beschafft die Panel-Rechtecke einer Comic-Seite: Coil dekodiert das Seitenbild,
 * es wird auf [detectionWidth] runterskaliert (Flood-Fill/CC braucht keine volle Auflösung),
 * der [PanelDetector] liefert die in Leserichtung sortierten Panels.
 *
 * Panel-Koordinaten liegen im Downscale-Raum; die Compose-Schicht normalisiert sie
 * über die tatsächlichen Detektions-Maße (siehe [PageDetection]).
 */
class ComicPageLoader(
    context: Context,
    private val imageLoader: ImageLoader,
    private val detector: PanelDetector = PanelDetector(),
    private val detectionWidth: Int = 1000,
) {
    private val context: Context = context.applicationContext

    data class PageDetection(val panels: List<PanelRect>, val pageWidth: Int, val pageHeight: Int)

    suspend fun detect(pageUrl: String, headers: Map<String, String>): PageDetection =
        withContext(Dispatchers.Default) {
            val bitmap = decode(pageUrl, headers) ?: return@withContext PageDetection(emptyList(), 0, 0)
            val scaled = downscale(bitmap)
            val page = toRenderedPage(scaled)
            val panels = detector.detect(page, ReadingDirection.LEFT_TO_RIGHT)
            PageDetection(panels, page.width, page.height)
        }

    private suspend fun decode(url: String, headers: Map<String, String>): Bitmap? =
        withContext(Dispatchers.IO) {
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .apply { headers.forEach { addHeader(it.key, it.value) } }
                .build()
            val result = imageLoader.execute(request)
            (result.drawable as? BitmapDrawable)?.bitmap
        }

    private fun downscale(src: Bitmap): Bitmap {
        if (src.width <= detectionWidth) return src
        val ratio = detectionWidth.toFloat() / src.width
        return src.scale(detectionWidth, (src.height * ratio).toInt())
    }

    private fun toRenderedPage(bmp: Bitmap): RenderedPage {
        val pixels = IntArray(bmp.width * bmp.height)
        bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        return RenderedPage(bmp.width, bmp.height, pixels)
    }
}
