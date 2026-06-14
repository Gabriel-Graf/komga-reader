package com.komgareader.app.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.core.graphics.scale
import coil.ImageLoader
import coil.request.ImageRequest
import com.komgareader.app.data.coil.SourceImage
import com.panela.comiccutter.PanelRect
import com.panela.comiccutter.PanelSource
import com.panela.comiccutter.ReadingDirection
import com.panela.comiccutter.ReadingOrder
import com.panela.comiccutter.model.RenderedPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Obtains the panel rectangles of a comic page: Coil decodes the page image, it is downscaled to
 * [detectionWidth] (the detector needs no full resolution), and the supplied [PanelSource] detects
 * panels which are then sorted into left-to-right reading order via [ReadingOrder].
 *
 * The geometric source already returns panels in LTR order; the ML source returns them in confidence
 * (score) order after NMS. Sorting after detection is safe for both: re-sorting an already-ordered
 * list with the same key is idempotent (stable sort).
 *
 * Panel coordinates live in the downscale space; the Compose layer normalizes them against the
 * actual detection dimensions (see [PageDetection]).
 */
class ComicPageLoader(
    context: Context,
    private val imageLoader: ImageLoader,
    private val detectionWidth: Int = 1000,
) {
    private val context: Context = context.applicationContext

    data class PageDetection(val panels: List<PanelRect>, val pageWidth: Int, val pageHeight: Int)

    suspend fun detect(pageImage: SourceImage, panelSource: PanelSource): PageDetection =
        withContext(Dispatchers.Default) {
            val bitmap = decode(pageImage) ?: return@withContext PageDetection(emptyList(), 0, 0)
            val scaled = downscale(bitmap)
            val page = toRenderedPage(scaled)
            val raw = panelSource.detect(page)
            val panels = ReadingOrder.sort(raw, ReadingDirection.LEFT_TO_RIGHT)
            PageDetection(panels, page.width, page.height)
        }

    private suspend fun decode(pageImage: SourceImage): Bitmap? =
        withContext(Dispatchers.IO) {
            val request = ImageRequest.Builder(context)
                .data(pageImage)
                .allowHardware(false)
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
