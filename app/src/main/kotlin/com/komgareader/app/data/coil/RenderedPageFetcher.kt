package com.komgareader.app.data.coil

import android.graphics.drawable.BitmapDrawable
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import com.komgareader.app.data.RenderedPageStore

/**
 * Coil-2 [Fetcher] that resolves a [RenderedPageImage] by rendering the page through the shared
 * [RenderedPageStore] (MuPDF, whole-file documents). Returns the already-decoded bitmap as a
 * [DrawableResult] — no re-encode round-trip — so both the comic panel detector
 * ([ComicPageLoader]) and the on-screen `AsyncImage` consume it the same way as a streamed page.
 */
class RenderedPageFetcher(
    private val model: RenderedPageImage,
    private val options: Options,
    private val store: RenderedPageStore,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val bitmap = store.render(model.sourceId, model.bookRemoteId, model.pageIndex, model.ext)
        return DrawableResult(
            drawable = BitmapDrawable(options.context.resources, bitmap),
            isSampled = false,
            dataSource = DataSource.DISK,
        )
    }

    /** Coil component: builds a [RenderedPageFetcher] for every [RenderedPageImage]. */
    class Factory(private val store: RenderedPageStore) : Fetcher.Factory<RenderedPageImage> {
        override fun create(data: RenderedPageImage, options: Options, imageLoader: ImageLoader): Fetcher =
            RenderedPageFetcher(data, options, store)
    }
}
