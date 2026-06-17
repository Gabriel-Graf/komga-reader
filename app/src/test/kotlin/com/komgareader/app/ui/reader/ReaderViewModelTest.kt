package com.komgareader.app.ui.reader

import androidx.lifecycle.SavedStateHandle
import com.komgareader.app.data.ActiveSource
import com.komgareader.app.data.KomgaSourceProvider
import com.komgareader.app.data.SourceRegistration
import com.komgareader.plugin.host.PluginHost
import io.mockk.mockk
import com.komgareader.app.data.coil.SourceImage
import com.komgareader.app.eink.HardwareButtonBus
import com.komgareader.domain.eink.ButtonEvent
import com.komgareader.domain.eink.HardwareButton
import com.komgareader.data.download.LocalBookBytes
import com.komgareader.domain.model.Book
import com.komgareader.domain.model.BookFormat
import com.komgareader.domain.model.DownloadState
import com.komgareader.domain.model.ReadProgress
import com.komgareader.domain.model.Series
import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.repository.DownloadRepository
import com.komgareader.domain.repository.DownloadedBook
import com.komgareader.domain.repository.ServerConfig
import com.komgareader.domain.repository.ServerRepository
import com.komgareader.domain.eink.EinkContext
import com.komgareader.domain.eink.EinkContextProfile
import com.komgareader.domain.render.Document
import com.komgareader.domain.render.DocumentFactory
import com.komgareader.domain.repository.SettingsRepository
import com.komgareader.domain.source.BrowsableSource
import com.komgareader.domain.source.PageRef
import com.komgareader.domain.source.PagedResult
import com.komgareader.domain.source.SourceFilter
import com.komgareader.domain.source.SourceManager
import com.komgareader.domain.source.SyncingSource
import com.komgareader.domain.source.buildPageRefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Beweist, dass der [ReaderViewModel] **quellen-agnostisch** lädt: er kennt nur
 * [ActiveSource]/[BrowsableSource], baut [SourceImage]-Seiten (kein url/authHeaders)
 * und der Webtoon-Strip entsteht über `seriesIdOf` + `books` + `buildPageRefs`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {

    private class FakeSource(
        override val id: Long = 42L,
        private val pageCount: Int = 0,
        private val seriesId: String = "",
        private val booksInSeries: List<Book> = emptyList(),
    ) : BrowsableSource, SyncingSource {
        override val name = "Fake"
        override val kind = SourceKind.KOMGA

        override suspend fun pages(bookRemoteId: String): List<PageRef> =
            buildPageRefs(bookRemoteId, pageCount)
        override suspend fun seriesIdOf(bookRemoteId: String): String = seriesId
        override suspend fun coverBytes(remoteId: String, isSeriesCover: Boolean): ByteArray = error("not used")
        override suspend fun books(seriesRemoteId: String): List<Book> = booksInSeries
        override suspend fun pullProgress(bookRemoteId: String): ReadProgress? = null

        override suspend fun browse(page: Int, filter: SourceFilter): PagedResult<Series> = error("not used")
        override suspend fun search(query: String, page: Int): PagedResult<Series> = error("not used")
        override suspend fun seriesDetail(seriesRemoteId: String): Series? = error("not used")
        override suspend fun openPage(ref: PageRef): ByteArray = error("not used")
        override suspend fun downloadFile(bookRemoteId: String, onProgress: (Long, Long) -> Unit): ByteArray = error("not used")
        override suspend fun pushProgress(bookRemoteId: String, progress: ReadProgress) = error("not used")
        override suspend fun setRead(bookRemoteId: String, read: Boolean, pageCount: Int) = error("not used")
    }

    private class FakeServerRepository : ServerRepository {
        override val configs: Flow<List<ServerConfig>> = flowOf(emptyList())
        override val config: Flow<ServerConfig?> = flowOf(null)
        override suspend fun save(config: ServerConfig) = error("not used")
        override suspend fun remove(id: Long) = error("not used")
        override suspend fun clear() = error("not used")
    }

    /** Multi-source-fähig: löst Quellen per `id` auf, genau wie der echte [ActiveSource]. */
    private class FakeActiveSource(private val sources: List<BrowsableSource>) : ActiveSource(
        sources = SourceManager(),
        servers = FakeServerRepository(),
        registration = SourceRegistration(SourceManager(), KomgaSourceProvider(), mockk(relaxed = true), mockk(relaxed = true)),
    ) {
        constructor(source: BrowsableSource?) : this(listOfNotNull(source))
        override suspend fun current(): BrowsableSource? = sources.firstOrNull()
        override suspend fun get(sourceId: Long): BrowsableSource? = sources.firstOrNull { it.id == sourceId }
        override suspend fun all(): List<BrowsableSource> = sources
    }

    private class FakeDownloads(private val book: DownloadedBook? = null) : DownloadRepository {
        override val downloads: Flow<List<DownloadedBook>> = flowOf(emptyList())
        override suspend fun get(bookRemoteId: String): DownloadedBook? = book
        override suspend fun put(book: DownloadedBook) = error("not used")
        override suspend fun remove(bookRemoteId: String) = error("not used")
        override suspend fun removeBySourceId(sourceId: Long) = error("not used")
    }

    private class FakeLocalBookBytes(private val bytes: ByteArray = ByteArray(0)) : LocalBookBytes {
        override fun bytesOf(book: DownloadedBook): ByteArray = bytes
    }

    private class FakeDocument(private val pages: Int) : Document {
        override fun pageCount(): Int = pages
        override fun pageSize(index: Int) = error("not used")
        override fun renderPage(index: Int, zoom: Float, rotation: Int) = error("not used")
        override fun close() = Unit
    }

    /** Beweist, dass der VM die **injizierte** [DocumentFactory] (DIP) nutzt, nicht eine selbst gebaute. */
    private class FakeDocumentFactory(private val doc: Document) : DocumentFactory {
        var openedWith: String? = null
        override fun open(bytes: ByteArray, formatHint: String): Document {
            openedWith = String(bytes); return doc
        }
    }

    private class FakeSettings : SettingsRepository {
        override val themeMode: Flow<String> = flowOf("SYSTEM")
        override val language: Flow<String> = flowOf("de")
        override val displayMode: Flow<String> = flowOf("EINK")
        override val shellLayoutMode: Flow<String> = flowOf("AUTO")
        override val bookmarkMarkerStyle: Flow<String> = flowOf("UNDERLINE")
        override val externalOpenBehavior: Flow<String> = flowOf("ASK")
        override val downloadDir: Flow<String?> = flowOf(null)
        override val guidedPanelOverlay: Flow<Boolean> = flowOf(false)
        override val useMlDetection: Flow<Boolean> = flowOf(true)
        override val activeColorProfileId: Flow<Long?> = flowOf(null)
        override val webtoonOverlapPercent: Flow<Int> = flowOf(0)
        override val chapterViewMode: Flow<String> = flowOf("LIST")
        override val librariesViewMode: Flow<String> = flowOf("LIST")
        override val collectionsViewMode: Flow<String> = flowOf("LARGE_TILE")
        override val novelFontSizeEm: Flow<Float> = flowOf(1f)
        override val novelLineHeight: Flow<Float> = flowOf(1f)
        override val novelMarginPreset: Flow<String> = flowOf("NORMAL")
        override val novelFontFamily: Flow<String> = flowOf("")
        override val novelTextAlign: Flow<String> = flowOf("LEFT")
        override val novelHyphenationLang: Flow<String> = flowOf("")
        override val novelFontWeight: Flow<Int> = flowOf(400)
        override val officialRepoEnabled: Flow<Boolean> = flowOf(true)
        override val activeUiPack: Flow<String> = flowOf("")
        override val lastSeenVersion: Flow<String> = flowOf("")
        override val einkContextProfiles: Flow<Map<EinkContext, EinkContextProfile>> = flowOf(emptyMap())
        override val screenSaverMode: Flow<String> = flowOf("OFF")
        override val screenSaverCustomUri: Flow<String> = flowOf("")
        override val screenSaverFillCrop: Flow<Boolean> = flowOf(false)
        override suspend fun setThemeMode(value: String) = error("not used")
        override suspend fun setLanguage(value: String) = error("not used")
        override suspend fun setDisplayMode(value: String) = error("not used")
        override suspend fun setShellLayoutMode(value: String) = error("not used")
        override suspend fun setBookmarkMarkerStyle(value: String) = error("not used")
        override suspend fun setExternalOpenBehavior(value: String) = error("not used")
        override suspend fun setDownloadDir(uri: String?) = error("not used")
        override suspend fun setGuidedPanelOverlay(value: Boolean) = error("not used")
        override suspend fun setUseMlDetection(value: Boolean) = error("not used")
        override suspend fun setActiveColorProfileId(id: Long) = error("not used")
        override suspend fun setWebtoonOverlapPercent(percent: Int) = error("not used")
        override suspend fun setChapterViewMode(mode: String) = error("not used")
        override suspend fun setLibrariesViewMode(mode: String) = error("not used")
        override suspend fun setCollectionsViewMode(mode: String) = error("not used")
        override suspend fun setNovelFontSizeEm(value: Float) = error("not used")
        override suspend fun setNovelLineHeight(value: Float) = error("not used")
        override suspend fun setNovelMarginPreset(preset: String) = error("not used")
        override suspend fun setNovelFontFamily(family: String) = error("not used")
        override suspend fun setNovelTextAlign(align: String) = error("not used")
        override suspend fun setNovelHyphenationLang(lang: String) = error("not used")
        override suspend fun setNovelFontWeight(value: Int) = error("not used")
        override suspend fun setOfficialRepoEnabled(enabled: Boolean) = error("not used")
        override suspend fun setActiveUiPack(packageName: String) = error("not used")
        override suspend fun setLastSeenVersion(version: String) = error("not used")
        override suspend fun setEinkContextProfile(context: EinkContext, profile: EinkContextProfile) = error("not used")
        override suspend fun setScreenSaverMode(value: String) = error("not used")
        override suspend fun setScreenSaverCustomUri(uri: String) = error("not used")
        override suspend fun setScreenSaverFillCrop(value: Boolean) = error("not used")
    }

    private fun book(remoteId: String, pageCount: Int) = Book(
        id = 0, sourceId = 42L, seriesId = 0, remoteId = remoteId, title = remoteId,
        format = BookFormat.CBZ, pageCount = pageCount, downloadState = DownloadState.REMOTE,
    )

    private fun readerVm(
        source: BrowsableSource?,
        mode: String,
        stream: Boolean = true,
        sourceId: Long = 42L,
        active: ActiveSource = FakeActiveSource(source),
        downloadBook: DownloadedBook? = null,
        localBytes: ByteArray = ByteArray(0),
        documentFactory: DocumentFactory = FakeDocumentFactory(FakeDocument(0)),
        bus: HardwareButtonBus = HardwareButtonBus(),
    ): ReaderViewModel = ReaderViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf("bookId" to "b1", "sourceId" to sourceId, "format" to "CBZ", "viewerMode" to mode, "stream" to stream),
        ),
        active = active,
        bus = bus,
        downloadRepository = FakeDownloads(downloadBook),
        localBookBytes = FakeLocalBookBytes(localBytes),
        documentFactory = documentFactory,
        settings = FakeSettings(),
        screenSaver = mockk(relaxed = true),
    )

    private fun downloadedBook() = DownloadedBook(
        bookRemoteId = "b1", sourceId = 42L, seriesRemoteId = "s1",
        title = "T", format = "cbz", localPath = "/tmp/x.cbz", totalPages = 3,
    )

    @Test
    fun `paged-laden nutzt active source und liefert SourceImage-seiten`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val vm = readerVm(FakeSource(pageCount = 5), mode = "PAGED")

            val content = vm.content.first { it !is ReaderContent.Loading } as ReaderContent.Streamed
            assertEquals(5, content.pages.size)
            assertEquals(SourceImage(sourceId = 42L, bookRemoteId = "b1", pageNumber = 1), content.pages.first())
            assertEquals(SourceImage(sourceId = 42L, bookRemoteId = "b1", pageNumber = 5), content.pages.last())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `webtoon-strip baut refs ueber seriesIdOf + books + buildPageRefs`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val source = FakeSource(
                seriesId = "s1",
                booksInSeries = listOf(book("b1", pageCount = 2), book("b2", pageCount = 3)),
            )
            val vm = readerVm(source, mode = "WEBTOON")

            val content = vm.content.first { it !is ReaderContent.Loading } as ReaderContent.Webtoon
            assertEquals(5, content.pages.size)
            assertEquals(SourceImage(sourceId = 42L, bookRemoteId = "b1", pageNumber = 1), content.pages.first())
            assertEquals(SourceImage(sourceId = 42L, bookRemoteId = "b2", pageNumber = 1), content.pages[2])
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `paged-laden loest bei mehreren quellen die per sourceId auf - nicht die erste`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            // Zwei aktive Quellen; geöffnet wird das Werk der ZWEITEN (id 99). Greift der Reader
            // fälschlich auf „die erste/aktive" zu, lädt er 5 statt 2 Seiten.
            val first = FakeSource(id = 42L, pageCount = 5)
            val second = FakeSource(id = 99L, pageCount = 2)
            val vm = readerVm(
                source = second, mode = "PAGED", sourceId = 99L,
                active = FakeActiveSource(listOf(first, second)),
            )

            val content = vm.content.first { it !is ReaderContent.Loading } as ReaderContent.Streamed
            assertEquals(2, content.pages.size)
            assertEquals(SourceImage(sourceId = 99L, bookRemoteId = "b1", pageNumber = 1), content.pages.first())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `rendered-pfad oeffnet lokalen download ueber injizierte DocumentFactory`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val factory = FakeDocumentFactory(FakeDocument(pages = 3))
            val vm = readerVm(
                source = FakeSource(),
                mode = "PAGED",
                stream = false,
                downloadBook = downloadedBook(),
                localBytes = "BYTES".toByteArray(),
                documentFactory = factory,
            )

            val content = vm.content.first { it !is ReaderContent.Loading } as ReaderContent.Rendered
            assertEquals(3, content.pageCount)
            assertEquals("BYTES", factory.openedWith)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `short press events do turn the page`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val bus = HardwareButtonBus()
            val vm = readerVm(FakeSource(pageCount = 5), mode = "PAGED", bus = bus)

            // Wait until the content is loaded.
            vm.content.first { it !is ReaderContent.Loading }

            // Emit a PAGE_NEXT press — must advance to page 1.
            bus.emit(ButtonEvent(HardwareButton.PAGE_NEXT))
            testScheduler.advanceUntilIdle()

            assertEquals(1, vm.requestedPage.value, "Short press must advance requestedPage")
        } finally {
            Dispatchers.resetMain()
        }
    }
}
