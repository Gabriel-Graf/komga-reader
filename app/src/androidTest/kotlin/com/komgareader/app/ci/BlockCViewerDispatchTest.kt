package com.komgareader.app.ci

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.komgareader.domain.model.BookFormat
import com.komgareader.domain.model.ContentType
import com.komgareader.domain.model.ViewerType
import com.komgareader.domain.source.BrowsableSource
import com.komgareader.domain.source.SourceFilter
import com.komgareader.domain.usecase.ResolveViewerType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Spec §9 Block C — deterministischer Reader-Dispatch auf echten Fixture-Metadaten. */
@RunWith(AndroidJUnit4::class)
class BlockCViewerDispatchTest {

    private lateinit var stack: CiSourceStack
    private val resolve = ResolveViewerType()

    @Before fun setUp() { stack = CiSourceStack() }
    @After fun tearDown() { stack.close() }

    private suspend fun firstSeries(source: BrowsableSource, title: String) =
        source.browse(0, SourceFilter()).items.first { it.title == title }

    /** C9: Manga (CBZ) mit Shelf-Tag MANGA → der für MANGA definierte Viewer (paged-Familie). */
    @Test fun c9_manga_dispatcht_auf_manga_viewer() = runTest {
        stack.register(CiKomga.A)
        val source = stack.activeSource.all().first()
        val series = firstSeries(source, CiFixtures.MANGA_SERIES)
        val book = source.books(series.remoteId).first()
        assertEquals("Manga-Buch ist ein Archiv (CBZ)", BookFormat.CBZ, book.format)

        val viewer = resolve(series, book, fallback = ContentType.MANGA)
        assertEquals("MANGA-Shelf → MANGA-Viewer", resolve.forContentType(ContentType.MANGA), viewer)
    }

    /** C10: Webtoon (CBZ) mit Shelf-Tag WEBTOON → Webtoon-Viewer. */
    @Test fun c10_webtoon_dispatcht_auf_webtoon_viewer() = runTest {
        stack.register(CiKomga.B)
        val source = stack.activeSource.all().first()
        val series = firstSeries(source, CiFixtures.WEBTOON_SERIES)
        val book = source.books(series.remoteId).first()

        val viewer = resolve(series, book, fallback = ContentType.WEBTOON)
        assertEquals("WEBTOON-Shelf → WEBTOON-Viewer", ViewerType.WEBTOON, viewer)
    }

    /** C11: Novel (EPUB) → NOVEL-Viewer, unabhängig vom Shelf-Tag (Format schlägt Fallback). */
    @Test fun c11_novel_epub_dispatcht_auf_novel_viewer() = runTest {
        stack.register(CiKomga.A)
        val source = stack.activeSource.all().first()
        val series = firstSeries(source, CiFixtures.NOVELS_A.first()) // "Alpha-Novel"
        val book = source.books(series.remoteId).first()
        assertEquals("Novel-Buch ist EPUB", BookFormat.EPUB, book.format)

        // Selbst mit irreführendem Fallback MANGA muss EPUB → NOVEL gewinnen.
        val viewer = resolve(series, book, fallback = ContentType.MANGA)
        assertEquals("EPUB → NOVEL (Format schlägt Shelf-Fallback)", ViewerType.NOVEL, viewer)
    }

    /** C12: contentTypeOverride schlägt den Shelf-Fallback (kein Auto-Erkennen). */
    @Test fun c12_override_schlaegt_shelf_fallback() = runTest {
        stack.register(CiKomga.A)
        val source = stack.activeSource.all().first()
        val series = firstSeries(source, CiFixtures.MANGA_SERIES)
        val book = source.books(series.remoteId).first()

        // Manga-Serie künstlich auf NOVEL übersteuert → Override gewinnt gegen MANGA-Fallback.
        val overridden = series.copy(contentTypeOverride = ContentType.NOVEL)
        val viewer = resolve(overridden, book, fallback = ContentType.MANGA)
        assertEquals("Override NOVEL schlägt Fallback MANGA", ViewerType.NOVEL, viewer)
    }
}
