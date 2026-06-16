package com.komgareader.app.ui.reader

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgareader.app.data.ActiveSource
import com.komgareader.app.data.coil.SourceImage
import com.komgareader.app.eink.HardwareButtonBus
import com.komgareader.app.ui.common.ErrorKind
import com.komgareader.app.ui.common.UiError
import com.komgareader.app.ui.common.uiErrorOf
import com.komgareader.data.download.LocalBookBytes
import com.komgareader.domain.eink.HardwareButton
import com.komgareader.domain.model.BookFormat
import com.komgareader.domain.model.DisplayMode
import com.komgareader.domain.model.ReadProgress
import com.komgareader.domain.reader.WebtoonChapter
import com.komgareader.domain.render.Document
import com.komgareader.domain.render.DocumentFactory
import com.komgareader.domain.repository.DownloadRepository
import com.komgareader.domain.repository.SettingsRepository
import com.komgareader.domain.source.BrowsableSource
import com.komgareader.domain.source.SyncingSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ReaderUiState(
    // Reader startet im Vollbild; das Overlay erscheint erst auf Tipp.
    val chromeVisible: Boolean = false,
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val active: ActiveSource,
    private val bus: HardwareButtonBus,
    private val downloadRepository: DownloadRepository,
    private val localBookBytes: LocalBookBytes,
    private val documentFactory: DocumentFactory,
    private val settings: SettingsRepository,
) : ViewModel(), Viewer {

    private val bookId: String = checkNotNull(savedStateHandle["bookId"])

    /** Quelle dieses Werks (Naht A) — aus der Navigation, nicht „die erste/aktive". */
    private val routeSourceId: Long = checkNotNull(savedStateHandle["sourceId"])

    /** Route-scoped identity, identical for every reader of this book (used by stats tracking). */
    val bookRemoteId: String get() = bookId
    val sourceId: Long get() = routeSourceId

    private val format: BookFormat = runCatching {
        BookFormat.valueOf(savedStateHandle.get<String>("format") ?: "CBZ")
    }.getOrDefault(BookFormat.CBZ)

    /** Wenn true, lokalen Download ignorieren und immer streamen. */
    private val forceStream: Boolean = savedStateHandle.get<Boolean>("stream") ?: false

    private val initialViewerMode: ViewerMode = runCatching {
        ViewerMode.valueOf(savedStateHandle.get<String>("viewerMode") ?: "PAGED")
    }.getOrDefault(ViewerMode.PAGED)

    /** Der Nicht-Webtoon-Modus, in dem geöffnet wurde (PAGED oder COMIC); Ziel beim Zurück-Togglen. */
    private val pagedFamilyMode: ViewerMode =
        if (initialViewerMode == ViewerMode.WEBTOON) ViewerMode.PAGED else initialViewerMode

    private val _content = MutableStateFlow<ReaderContent>(ReaderContent.Loading)
    val content: StateFlow<ReaderContent> = _content.asStateFlow()

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    /** Overlay-Sichtbarkeit als eigener Flow für das geteilte [ReaderScaffold]. */
    override val chromeVisible: StateFlow<Boolean> = _uiState
        .map { it.chromeVisible }
        .stateIn(viewModelScope, SharingStarted.Eagerly, _uiState.value.chromeVisible)

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

    /**
     * Schaltet den **Anzeige**-Modus paged⟷webtoon (bzw. comic⟷webtoon) auf demselben bereits
     * geladenen [ReaderContent] um — kein Neuladen, nur ein anderes Layout über dieselbe
     * `pages`-Liste. Dieser Toggle bleibt **bewusst** in diesem VM (kein Zwei-VM-Split): beide
     * Layouts teilen `_currentPage` (Scroll-/Seitenposition über den Wechsel hinweg) und [frameStep].
     * Ein separates `WebtoonReaderViewModel` müsste diesen geteilten Zustand entweder duplizieren,
     * die Position beim Toggle verlieren oder den Inhalt neu laden (Lade-Sturm + Verhaltensänderung).
     * Die genuin webtoon-spezifische *Lade-Logik* ist stattdessen in den puren [buildWebtoonStrip]
     * ausgelagert und einzeln getestet.
     */
    fun toggleViewerMode() {
        viewerMode.value = if (viewerMode.value == ViewerMode.WEBTOON) pagedFamilyMode else ViewerMode.WEBTOON
    }

    init {
        loadBook()
        collectButtonEvents()
    }

    private fun loadBook() = viewModelScope.launch {
        runCatching {
            // EPUB läuft über den Roman-Reader (crengine-Reflow): nur den Modus
            // signalisieren, die Bytes lädt der NovelReaderViewModel selbst über
            // denselben Mechanismus (lokaler Download oder Stream). Kein MuPDF.
            if (format == BookFormat.EPUB) {
                _content.value = ReaderContent.Novel
                return@launch
            }

            // Zuerst prüfen ob lokal vorhanden — außer wenn forceStream gesetzt ist
            if (!forceStream) {
                val localDownload = downloadRepository.get(bookId)
                if (localDownload != null) {
                    val bytes = withContext(Dispatchers.IO) { localBookBytes.bytesOf(localDownload) }
                    val ext = ".${localDownload.format.lowercase()}"
                    val doc = withContext(Dispatchers.IO) { documentFactory.open(bytes, ext) }
                    document = doc
                    val pageCount = withContext(Dispatchers.IO) { doc.pageCount() }
                    // Auch beim lokalen Download auf der letzten Seite fortsetzen:
                    // Server-Progress best-effort holen (offline → Seite 0).
                    val startPage = runCatching {
                        (active.get(routeSourceId) as? SyncingSource)?.pullProgress(bookId)
                            ?.let { (it.page - 1).coerceIn(0, pageCount - 1) }
                    }.getOrNull() ?: 0
                    _currentPage.value = startPage
                    _content.value = ReaderContent.Rendered(pageCount = pageCount, initialPage = startPage)
                    return@launch
                }
            }

            // Kein lokaler Download (oder forceStream) → Stream über die Quelle dieses Werks.
            val source = active.get(routeSourceId)
            if (source == null) {
                _content.value = ReaderContent.Error(UiError(ErrorKind.NO_CONNECTION, ""))
                return@launch
            }

            if (initialViewerMode == ViewerMode.WEBTOON) {
                loadWebtoonStrip(source)
            } else {
                val pages = source.pages(bookId)
                if (pages.isEmpty()) {
                    // Source cannot stream pages (OPDS, LocalSource PDF/CBR) → render whole file via MuPDF.
                    val bytes = withContext(Dispatchers.IO) { source.downloadFile(bookId) }
                    val ext = ".${format.name.lowercase()}"
                    val doc = withContext(Dispatchers.IO) { documentFactory.open(bytes, ext) }
                    document = doc
                    val pageCount = withContext(Dispatchers.IO) { doc.pageCount() }
                    val startPage = runCatching {
                        (source as? SyncingSource)?.pullProgress(bookId)
                            ?.let { (it.page - 1).coerceIn(0, pageCount - 1) }
                    }.getOrNull() ?: 0
                    _currentPage.value = startPage
                    _content.value = ReaderContent.Rendered(pageCount = pageCount, initialPage = startPage)
                } else {
                    val startPage = runCatching { (source as? SyncingSource)?.pullProgress(bookId) }
                        .getOrNull()
                        ?.let { progress -> (progress.page - 1).coerceIn(0, pages.size - 1) }
                        ?: 0
                    _currentPage.value = startPage
                    _content.value = ReaderContent.Streamed(
                        pages = pages.map { SourceImage(source.id, bookId, it.pageNumber) },
                        initialPage = startPage,
                    )
                }
            }
        }.onFailure { e ->
            _content.value = ReaderContent.Error(uiErrorOf(e))
        }
    }

    /**
     * Baut den nahtlosen, kapitelübergreifenden Webtoon-Strip aus EINEM
     * `books(seriesId)`-Call: Die Seiten-Refs werden deterministisch aus der
     * Seitenzahl jedes Kapitels gebaut (kein Pro-Kapitel-Request!), die Bilder
     * lädt die LazyColumn/Coil erst beim Sichtbarwerden. So entsteht kein
     * Lade-Sturm bei Serien mit vielen Kapiteln.
     */
    private suspend fun loadWebtoonStrip(source: BrowsableSource) {
        val seriesId = withContext(Dispatchers.IO) { source.seriesIdOf(bookId) }
        val books = withContext(Dispatchers.IO) { source.books(seriesId) }
        val localStart = runCatching { (source as? SyncingSource)?.pullProgress(bookId) }
            .getOrNull()
            ?.let { it.page - 1 }
            ?: 0

        // Reine Strip-Planung (Index ↔ Kapitel/Seite, flache Seiten-Liste, globaler Start) —
        // im pur-getesteten [buildWebtoonStrip]. Hier nur das I/O + das Mapping auf SourceImage.
        val plan = buildWebtoonStrip(
            chapters = books.map { WebtoonChapter(it.remoteId, it.pageCount) },
            openedBookRemoteId = bookId,
            localStartPage = localStart,
        )
        val flat = plan.pages.map { SourceImage(source.id, it.bookRemoteId, it.pageNumber) }

        _currentPage.value = plan.initialGlobalIndex
        _content.value = ReaderContent.Webtoon(
            pages = flat,
            initialPage = plan.initialGlobalIndex,
            strip = plan.strip,
        )
    }

    private fun collectButtonEvents() = viewModelScope.launch {
        bus.events.collect { event ->
            // Im Comic-Modus übernimmt der ComicReaderViewModel die Tasten.
            if (viewerMode.value == ViewerMode.COMIC) return@collect
            // Im Webtoon-Modus bedeutet eine Taste einen Frame-Sprung (Pixel-Scroll),
            // nicht einen Seitenindex.
            if (viewerMode.value == ViewerMode.WEBTOON) {
                val dir = when (event.button) {
                    HardwareButton.PAGE_NEXT -> 1
                    HardwareButton.PAGE_PREV -> -1
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
                HardwareButton.PAGE_NEXT -> (current + 1).coerceAtMost(pageCount - 1)
                HardwareButton.PAGE_PREV -> (current - 1).coerceAtLeast(0)
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

    override fun onPageSettled(page: Int) {
        val index = page
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
            val source = active.get(routeSourceId) as? SyncingSource ?: return@launch
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
    override fun navigateTo(page: Int) {
        if (page != _currentPage.value) {
            _currentPage.value = page
            _requestedPage.value = page
        }
    }

    override fun toggleChrome() {
        _uiState.value = _uiState.value.copy(chromeVisible = !_uiState.value.chromeVisible)
    }

    override fun onCleared() {
        super.onCleared()
        document?.close()
        renderCache.clear()
    }
}
