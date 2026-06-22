package com.komgareader.app.data.coil

import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.komgareader.app.data.LocalCoverRenderer
import com.komgareader.data.cover.SourceCoverCache
import com.komgareader.domain.source.BrowsableSource
import com.komgareader.domain.source.SourceManager
import okio.Buffer

/**
 * Reine, testbare Kern-Logik: lädt die Cover-Bytes über die Quellen-Naht. Löst die Quelle
 * aus dem [SourceManager] auf und ruft [BrowsableSource.coverBytes] — die UI muss keine
 * quellen-spezifische Thumbnail-URL oder Auth-Header kennen.
 */
suspend fun loadCoverBytes(model: SourceCover, sources: SourceManager): ByteArray {
    val source = sources.get(model.sourceId) as? BrowsableSource
        ?: error("Quelle ${model.sourceId} nicht registriert")
    return source.coverBytes(model.remoteId, isSeriesCover = model.isSeries)
}

/**
 * Coil-2-[Fetcher], der ein [SourceCover] über die Quellen-Naht (Naht A) in Bytes auflöst,
 * analog zum [SourcePageFetcher] für Seiten. Hält Cover-Laden quellen-agnostisch.
 */
class SourceCoverFetcher(
    private val model: SourceCover,
    private val options: Options,
    private val sources: SourceManager,
    private val localCover: LocalCoverRenderer,
    private val coverCache: SourceCoverCache,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        // Primary: the source's own cover bytes (Komga thumbnail, CBZ first image, …) while online.
        val primary = runCatching { loadCoverBytes(model, sources) }.getOrDefault(ByteArray(0))
        val bytes = if (primary.isNotEmpty()) {
            // Write-through: persist every cover seen online so it survives offline (covers never go blank).
            coverCache.putIfAbsent(model.sourceId, model.remoteId, model.isSeries, primary)
            primary
        } else {
            // Offline / no source cover: render the downloaded file (its own work), else the sync-cached
            // source cover (e.g. a non-downloaded collection member prewarmed while online), else nothing.
            localCover.render(model)
                ?: coverCache.get(model.sourceId, model.remoteId, model.isSeries)
                ?: primary
        }
        return SourceResult(
            source = ImageSource(
                source = Buffer().apply { write(bytes) },
                context = options.context,
            ),
            mimeType = null,
            dataSource = DataSource.NETWORK,
        )
    }

    /** Coil-Komponente: erzeugt für jedes [SourceCover] einen [SourceCoverFetcher]. */
    class Factory(
        private val sources: SourceManager,
        private val localCover: LocalCoverRenderer,
        private val coverCache: SourceCoverCache,
    ) : Fetcher.Factory<SourceCover> {
        override fun create(data: SourceCover, options: Options, imageLoader: ImageLoader): Fetcher =
            SourceCoverFetcher(data, options, sources, localCover, coverCache)
    }
}
