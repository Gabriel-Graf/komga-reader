package com.komgareader.app.data.coil

import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.komgareader.domain.source.BrowsableSource
import com.komgareader.domain.source.PageRef
import com.komgareader.domain.source.SourceManager
import okio.Buffer

/**
 * Reine, testbare Kern-Logik: lädt die Bytes einer Seite über die Quellen-Naht.
 * Löst die Quelle aus dem [SourceManager] anhand der [SourceImage.sourceId] auf und
 * ruft [BrowsableSource.openPage] — kein UI-Code muss URLs oder Auth-Header kennen.
 */
suspend fun loadPageBytes(model: SourceImage, sources: SourceManager): ByteArray {
    val source = sources.get(model.sourceId) as? BrowsableSource
        ?: error("Quelle ${model.sourceId} nicht registriert")
    return source.openPage(
        PageRef(
            index = model.pageNumber - 1,
            bookRemoteId = model.bookRemoteId,
            pageNumber = model.pageNumber,
            url = "",
        ),
    )
}

/**
 * Coil-2-[Fetcher], der ein [SourceImage] über die Quellen-Naht (Naht A) in Bytes
 * auflöst, statt eine quellenspezifische URL + Auth-Header an Coil zu reichen.
 * Die Byte-Auflösung steckt in der reinen [loadPageBytes]; [fetch] umhüllt sie nur
 * mit der Coil-Verpackung.
 */
class SourcePageFetcher(
    private val model: SourceImage,
    private val options: Options,
    private val sources: SourceManager,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val bytes = loadPageBytes(model, sources)
        return SourceResult(
            source = ImageSource(
                source = Buffer().apply { write(bytes) },
                context = options.context,
            ),
            mimeType = null,
            dataSource = DataSource.NETWORK,
        )
    }

    /** Coil-Komponente: erzeugt für jedes [SourceImage] einen [SourcePageFetcher]. */
    class Factory(private val sources: SourceManager) : Fetcher.Factory<SourceImage> {
        override fun create(data: SourceImage, options: Options, imageLoader: ImageLoader): Fetcher =
            SourcePageFetcher(data, options, sources)
    }
}
