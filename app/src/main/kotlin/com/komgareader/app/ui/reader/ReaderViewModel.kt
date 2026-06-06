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
import com.komgareader.domain.model.ReadProgress
import com.komgareader.domain.render.Document
import com.komgareader.domain.repository.DownloadRepository
import com.komgareader.domain.repository.ServerRepository
import com.komgareader.render.mupdf.MupdfDocumentFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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

    private fun collectButtonEvents() = viewModelScope.launch {
        bus.events.collect { event ->
            val current = _currentPage.value
            val pageCount = when (val c = _content.value) {
                is ReaderContent.Streamed -> c.pages.size
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
        val pageCount = when (val c = _content.value) {
            is ReaderContent.Streamed -> c.pages.size
            is ReaderContent.Rendered -> c.pageCount
            else -> return
        }
        viewModelScope.launch {
            val config = servers.config.first()
            val source = sourceProvider.from(config) ?: return@launch
            runCatching {
                source.pushProgress(
                    bookId,
                    ReadProgress(
                        bookId = 0,
                        page = index + 1,
                        totalPages = pageCount,
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
