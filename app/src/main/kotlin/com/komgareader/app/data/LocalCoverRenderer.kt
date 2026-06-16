package com.komgareader.app.data

import android.graphics.Bitmap
import com.komgareader.app.data.coil.SourceCover
import com.komgareader.domain.render.DocumentFactory
import com.komgareader.domain.repository.DownloadRepository
import com.komgareader.domain.source.SourceId
import kotlinx.coroutines.flow.first
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Produces a cover for LOCAL works whose format the **renderer-free** `:source-local` cannot extract.
 * `LocalSource.coverBytes` only zips out the first image of a **CBZ**; PDF/EPUB/CBR need a render
 * engine, which by module rule lives only in the app layer ([DocumentFactory]/MuPDF), not in
 * `:source-local`. So the cover for those formats is rendered here, as a fallback in the Coil cover
 * path ([com.komgareader.app.data.coil.SourceCoverFetcher]).
 *
 * Returns `null` for non-local works, for CBZ (already covered by the source), or on any failure —
 * the caller then keeps the (empty) primary bytes (no cover, unchanged behaviour for those cases).
 */
@Singleton
class LocalCoverRenderer @Inject constructor(
    private val downloads: DownloadRepository,
    private val coverStore: LocalCoverStore,
) {
    suspend fun render(model: SourceCover): ByteArray? {
        if (model.sourceId != SourceId.LOCAL) return null
        val local = downloads.downloads.first().filter { it.sourceId == SourceId.LOCAL }
        // Series cover = first book of that series; book cover = that book.
        val book = if (model.isSeries) {
            local.firstOrNull { it.seriesRemoteId == model.remoteId }
        } else {
            local.firstOrNull { it.bookRemoteId == model.remoteId }
        } ?: return null
        // Reads the precomputed cover if present, renders + persists on a cold miss (CBZ → null,
        // already covered by LocalSource.coverBytes). See [LocalCoverStore].
        return coverStore.get(book)
    }
}

/**
 * Opens [bytes] with [factory] and renders page 0 to PNG cover bytes; `null` if the document has no
 * pages or yields an empty raster. Pure render glue (no repository / content-URI lookup) so it can be
 * exercised directly on a device/emulator (MuPDF renders pdf/cbr/epub — see the render-core E2E).
 */
fun renderFirstPageCover(factory: DocumentFactory, bytes: ByteArray, formatHint: String): ByteArray? =
    factory.open(bytes, formatHint).use { doc ->
        if (doc.pageCount() < 1) return null
        val page = doc.renderPage(index = 0, zoom = 1f, rotation = 0)
        if (page.width <= 0 || page.height <= 0) return null
        val bmp = Bitmap.createBitmap(page.pixels, page.width, page.height, Bitmap.Config.ARGB_8888)
        ByteArrayOutputStream().use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            bmp.recycle()
            out.toByteArray()
        }
    }
