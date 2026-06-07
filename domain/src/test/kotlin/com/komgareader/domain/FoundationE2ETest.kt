package com.komgareader.domain

import com.komgareader.domain.model.Book
import com.komgareader.domain.model.BookFormat
import com.komgareader.domain.model.ContentType
import com.komgareader.domain.model.ReadProgress
import com.komgareader.domain.model.Series
import com.komgareader.domain.model.Shelf
import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.model.ViewerType
import com.komgareader.domain.source.MediaSource
import com.komgareader.domain.source.SourceId
import com.komgareader.domain.source.SourceManager
import com.komgareader.domain.source.StubSource
import com.komgareader.domain.usecase.ResolveProgressConflict
import com.komgareader.domain.usecase.ResolveViewerType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeKomga(override val id: Long, override val name: String) : MediaSource {
    override val kind: SourceKind = SourceKind.KOMGA
}

/**
 * End-to-End-Verdrahtung der Foundation: deterministische Quellen-ID → Registry →
 * Viewer-Auflösung über ein Regal → Offline-Sync-Konfliktregel. Beweist, dass die
 * Domain-Bausteine als Pipeline zusammenspielen, nicht nur isoliert.
 */
class FoundationE2ETest {

    @Test
    fun `kompletter Domain-Fluss von Quelle bis Fortschritt`() {
        // 1. Deterministische, stabile ID für einen Komga-Server
        val komgaId = SourceId.of("Mein Komga", SourceKind.KOMGA, "https://nas.local:25600")
        assertTrue(komgaId >= 0L)

        // 2. Quelle in der reaktiven Registry registrieren und wiederfinden
        val manager = SourceManager()
        manager.register(FakeKomga(id = komgaId, name = "Mein Komga"))
        assertEquals("Mein Komga", manager.get(komgaId)?.name)

        // Fehlende Quelle bricht die Bibliothek nicht — Stub statt null
        val missing = manager.getOrStub(id = 999, name = "Verschwunden")
        assertTrue(missing is StubSource)

        // 3. Regal deklariert COMIC → Viewer ist deterministisch COMIC
        val shelf = Shelf(id = 1, name = "Comics", sources = emptyList(), defaultContentType = ContentType.COMIC)
        val series = Series(id = 10, sourceId = komgaId, remoteId = "s-1", title = "Berserk")
        val defaultBook = Book(id = 0, sourceId = komgaId, seriesId = 10, remoteId = "b-1", title = "Berserk #1", format = BookFormat.CBZ, pageCount = 200)
        val resolveViewer = ResolveViewerType()
        assertEquals(ViewerType.COMIC, resolveViewer(series, defaultBook, fallback = shelf.defaultContentType))

        // Eine als Webtoon markierte Serie überschreibt den Regal-Typ
        val webtoonSeries = series.copy(contentTypeOverride = ContentType.WEBTOON)
        assertEquals(ViewerType.WEBTOON, resolveViewer(webtoonSeries, defaultBook, fallback = shelf.defaultContentType))

        // 4. Offline-first-Sync: lokal weiter gelesen (neuer) gewinnt gegen Server
        val resolveConflict = ResolveProgressConflict()
        val local = ReadProgress(bookId = 10, page = 80, totalPages = 200, dirty = true, updatedAt = 5000)
        val remote = ReadProgress(bookId = 10, page = 60, totalPages = 200, updatedAt = 3000)
        assertEquals(80, resolveConflict(local, remote).page)
    }
}
