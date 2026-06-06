package com.komgareader.app.ui.reader

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgareader.app.data.AuthHeaders
import com.komgareader.app.data.KomgaSourceProvider
import com.komgareader.app.eink.HardwareButtonBus
import com.komgareader.data.download.DownloadManager
import com.komgareader.domain.eink.HardwareButton
import com.komgareader.domain.model.BookFormat
import com.komgareader.domain.model.DisplayMode
import com.komgareader.domain.model.ReadProgress
import com.komgareader.domain.reader.WebtoonChapter
import com.komgareader.domain.reader.WebtoonStrip
import com.komgareader.domain.render.Document
import com.komgareader.domain.repository.DownloadRepository
import com.komgareader.domain.repository.ServerRepository
import com.komgareader.domain.repository.SettingsRepository
import com.komgareader.domain.source.PageRef
import com.komgareader.render.mupdf.MupdfDocumentFactory
import com.komgareader.source.komga.KomgaSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ReaderUiState(
    val chromeVisible: Boolean = true,
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val servers: ServerRepository,
    private val sourceProvider: KomgaSourceProvider,
    private val bus: HardwareButtonBus,
    private val downloadRepository: DownloadRepository,
    private val downloadManager: DownloadManager,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val bookId: String = checkNotNull(savedStateHandle["bookId"])
    private val format: BookFormat = runCatching {
        BookFormat.valueOf(savedStateHandle.get<String>("format") ?: "CBZ")
    }.getOrDefault(BookFormat.CBZ)

    /** Wenn true, lokalen Download ignorieren und immer streamen. */
    private val forceStream: Boolean = savedStateHandle.get<Boolean>("stream") ?: false

    private val initialViewerMode: ViewerMode = runCatching {
        ViewerMode.valueOf(savedStateHandle.get<String>("viewerMode") ?: "PAGED")
    }.getOrDefault(ViewerMode.PAGED)

    private val _content = MutableStateFlow<ReaderContent>(ReaderContent.Loading)
    val content: StateFlow<ReaderContent> = _content.asStateFlow()

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    /** Von Buttons angeforderte Seite; Pager beobachtet dies und scrollt. */
    private val _requestedPage = MutableStateFlow(-1)
    val requestedPage: StateFlow<Int> = _requestedPage.asStateFlow()

    private val _currentPage = MutableStateFlow(0)

    val viewerMode = MutableStateFlow(initialViewerMode)

    /** App-weiter Anzeige-Modus (EINK/SMARTPHONE) für den Reader. */
    val displayMode: StateFlow<DisplayMode> = settings.displayMode
        .map { runCatching { DisplayMode.valueOf(it) }.getOrDefault(DisplayMode.EINK) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DisplayMode.EINK)

    /**
     * Frame-Sprünge im Webtoon-Modus (Hardware-/Lautstärke-Tasten): +1 = vorwärts,
     * -1 = rückwärts. Der WebtoonReaderScreen scrollt darauf um ~1 Bildschirmhöhe.
     */
    private val _frameStep = MutableSharedFlow<Int>(extraBufferCapacity = 8)
    val frameStep: SharedFlow<Int> = _frameStep.asSharedFlow()

    // MuPDF-Dokument (EPUB-Stream oder lokaler Download)
    private var document: Document? = null
    private val renderCache = mutableMapOf<Int, Bitmap>()
    private val renderMutex = Mutex()

    fun toggleViewerMode() {
        viewerMode.value = if (viewerMode.value == ViewerMode.PAGED) ViewerMode.WEBTOON else ViewerMode.PAGED
    }

    init {
        loadBook()
        collectButtonEvents()
    }

    private fun loadBook() = viewModelScope.launch {
        runCatching {
            // Zuerst prüfen ob lokal vorhanden — außer wenn forceStream gesetzt ist
            if (!forceStream) {
                val localDownload = downloadRepository.get(bookId)
                if (localDownload != null) {
                    val bytes = withContext(Dispatchers.IO) { downloadManager.bytesOf(localDownload) }
                    val ext = ".${localDownload.format.lowercase()}"
                    val doc = withContext(Dispatchers.IO) { MupdfDocumentFactory().open(bytes, ext) }
                    document = doc
                    val pageCount = withContext(Dispatchers.IO) { doc.pageCount() }
                    _currentPage.value = 0
                    _content.value = ReaderContent.Rendered(pageCount = pageCount, initialPage = 0)
                    return@launch
                }
            }

            // Kein lokaler Download (oder forceStream) → Netzwerk-Pfad
            val config = servers.config.first()
            val source = sourceProvider.from(config)
            if (config == null || source == null) {
                _content.value = ReaderContent.Error("Kein Server verbunden.")
                return@launch
            }
            val authHeaders = AuthHeaders.forCovers(config)

            if (format == BookFormat.EPUB) {
                val bytes = withContext(Dispatchers.IO) { source.downloadFile(bookId) }
                val doc = withContext(Dispatchers.IO) { MupdfDocumentFactory().open(bytes, ".epub") }
                document = doc
                val pageCount = withContext(Dispatchers.IO) { doc.pageCount() }
                val startPage = runCatching { source.pullProgress(bookId) }
                    .getOrNull()
                    ?.let { progress -> (progress.page - 1).coerceIn(0, pageCount - 1) }
                    ?: 0
                _currentPage.value = startPage
                _content.value = ReaderContent.Rendered(pageCount = pageCount, initialPage = startPage)
            } else if (initialViewerMode == ViewerMode.WEBTOON) {
                loadWebtoonStrip(source, authHeaders)
            } else {
                val pages = source.pages(bookId)
                val startPage = runCatching { source.pullProgress(bookId) }
                    .getOrNull()
                    ?.let { progress -> (progress.page - 1).coerceIn(0, pages.size - 1) }
                    ?: 0
                _currentPage.value = startPage
                _content.value = ReaderContent.Streamed(
                    pages = pages,
                    authHeaders = authHeaders,
                    initialPage = startPage,
                )
            }
        }.onFailure { e ->
            _content.value = ReaderContent.Error(e.message ?: "Buch konnte nicht geladen werden")
        }
    }

    /**
     * Lädt alle Kapitel der Serie und baut den nahtlosen, kapitelübergreifenden
     * Webtoon-Strip. Seitenlisten werden parallel geholt (reines JSON); die
     * Bilder lädt die LazyColumn/Coil erst beim Sichtbarwerden.
     */
    private suspend fun loadWebtoonStrip(source: KomgaSource, authHeaders: Map<String, String>) {
        val seriesId = withContext(Dispatchers.IO) { source.seriesIdOf(bookId) }
        val books = withContext(Dispatchers.IO) { source.books(seriesId) }
        val perChapter: List<List<PageRef>> = withContext(Dispatchers.IO) {
            coroutineScope { books.map { async { source.pages(it.remoteId) } }.awaitAll() }
        }
        val strip = WebtoonStrip(
            books.mapIndexed { i, b -> WebtoonChapter(b.remoteId, perChapter[i].size) },
        )
        val flat = perChapter.flatten()

        val openedIdx = books.indexOfFirst { it.remoteId == bookId }.coerceAtLeast(0)
        val openedPageCount = perChapter.getOrNull(openedIdx)?.size ?: 0
        val localStart = runCatching { source.pullProgress(bookId) }
            .getOrNull()
            ?.let { (it.page - 1).coerceIn(0, (openedPageCount - 1).coerceAtLeast(0)) }
            ?: 0
        val initialGlobal = if (strip.totalPages == 0) 0 else strip.globalIndex(openedIdx, localStart)

        _currentPage.value = initialGlobal
        _content.value = ReaderContent.Webtoon(
            pages = flat,
            authHeaders = authHeaders,
            initialPage = initialGlobal,
            strip = strip,
        )
    }

    private fun collectButtonEvents() = viewModelScope.launch {
        bus.events.collect { event ->
            // Im Webtoon-Modus bedeutet eine Taste einen Frame-Sprung (Pixel-Scroll),
            // nicht einen Seitenindex.
            if (viewerMode.value == ViewerMode.WEBTOON) {
                val dir = when (event.button) {
                    HardwareButton.PAGE_NEXT, HardwareButton.VOLUME_DOWN -> 1
                    HardwareButton.PAGE_PREV, HardwareButton.VOLUME_UP -> -1
                }
                _frameStep.tryEmit(dir)
                return@collect
            }
            val current = _currentPage.value
            val pageCount = when (val c = _content.value) {
                is ReaderContent.Streamed -> c.pages.size
                is ReaderContent.Webtoon -> c.pages.size
                is ReaderContent.Rendered -> c.pageCount
                else -> return@collect
            }
            val next = when (event.button) {
                HardwareButton.PAGE_NEXT, HardwareButton.VOLUME_DOWN ->
                    (current + 1).coerceAtMost(pageCount - 1)
                HardwareButton.PAGE_PREV, HardwareButton.VOLUME_UP ->
                    (current - 1).coerceAtLeast(0)
            }
            if (next != current) {
                _currentPage.value = next
                _requestedPage.value = next
            }
        }
    }

    suspend fun renderEpubPage(index: Int): Bitmap = withContext(Dispatchers.IO) {
        renderMutex.withLock {
            renderCache.getOrPut(index) {
                val rp = document!!.renderPage(index, zoom = 2f, rotation = 0)
                Bitmap.createBitmap(rp.pixels, rp.width, rp.height, Bitmap.Config.ARGB_8888)
            }
        }
    }

    fun onPageSettled(index: Int) {
        _currentPage.value = index
        // Im Webtoon-Strip wird der globale Index auf Kapitel + Seite abgebildet,
        // damit der Fortschritt für das richtige Kapitel gepusht wird.
        when (val c = _content.value) {
            is ReaderContent.Webtoon -> {
                if (c.strip.totalPages == 0) return
                val pos = c.strip.locate(index)
                val chapterPages = c.strip.chapters[pos.chapterIndex].pageCount
                pushProgress(pos.bookRemoteId, page = pos.pageInChapter + 1, totalPages = chapterPages)
            }
            is ReaderContent.Streamed -> pushProgress(bookId, page = index + 1, totalPages = c.pages.size)
            is ReaderContent.Rendered -> pushProgress(bookId, page = index + 1, totalPages = c.pageCount)
            else -> Unit
        }
    }

    private fun pushProgress(targetBookId: String, page: Int, totalPages: Int) {
        viewModelScope.launch {
            val config = servers.config.first()
            val source = sourceProvider.from(config) ?: return@launch
            runCatching {
                source.pushProgress(
                    targetBookId,
                    ReadProgress(
                        bookId = 0,
                        page = page,
                        totalPages = totalPages,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    /** Tap-Zonen-Navigation: Seite ansteuern (Pager beobachtet requestedPage). */
    fun navigateTo(index: Int) {
        if (index != _currentPage.value) {
            _currentPage.value = index
            _requestedPage.value = index
        }
    }

    fun toggleChrome() {
        _uiState.value = _uiState.value.copy(chromeVisible = !_uiState.value.chromeVisible)
    }

    override fun onCleared() {
        super.onCleared()
        document?.close()
        renderCache.clear()
    }
}
