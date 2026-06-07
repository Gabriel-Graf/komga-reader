package com.komgareader.app.ui.reader

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgareader.app.eink.HardwareButtonBus
import com.komgareader.domain.eink.HardwareButton
import com.komgareader.domain.render.ReflowConfig
import com.komgareader.render.crengine.CrengineDocument
import com.komgareader.render.crengine.CrengineNative
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** Zustand des Roman-Readers: reflowte Seiten zur aktuellen Viewport-Größe. */
data class NovelUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val chromeVisible: Boolean = false,
)

/**
 * Roman-Reader-ViewModel (ViewerType.NOVEL): öffnet das EPUB über die
 * crengine-Reflow-Engine (Naht B) und schichtet es zur Pixel-Größe des Viewports
 * um. Die Bytes kommen über denselben [EpubBytesLoader] wie der paginierte Pfad
 * (lokaler Download oder Stream) — kein eigener Abrufweg.
 *
 * Eigenes ViewModel (analog [ComicReaderViewModel]), das den geteilten
 * [ReaderChromeState]-Vertrag erfüllt und das [ReaderScaffold] speist. Die
 * Hardware-Tasten laufen über den [HardwareButtonBus] (vor/zurück blättern),
 * exakt wie in den anderen Readern.
 */
@HiltViewModel
class NovelReaderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val bytesLoader: EpubBytesLoader,
    private val bus: HardwareButtonBus,
) : ViewModel(), ReaderChromeState {

    private val bookId: String = checkNotNull(savedStateHandle["bookId"])
    private val forceStream: Boolean = savedStateHandle.get<Boolean>("stream") ?: false

    private val _uiState = MutableStateFlow(NovelUiState())
    val uiState: StateFlow<NovelUiState> = _uiState.asStateFlow()

    /** Overlay-Sichtbarkeit als eigener Flow für das geteilte [ReaderScaffold]. */
    override val chromeVisible: StateFlow<Boolean> = _uiState
        .map { it.chromeVisible }
        .stateIn(viewModelScope, SharingStarted.Eagerly, _uiState.value.chromeVisible)

    private var document: CrengineDocument? = null
    private val renderCache = mutableMapOf<Int, Bitmap>()
    private val renderMutex = Mutex()
    private var opened = false

    init {
        collectButtonEvents()
    }

    /**
     * Öffnet das EPUB für den [viewportWidth]×[viewportHeight] großen Viewport und
     * schichtet es mit [ReflowConfig.DEFAULT] um. Idempotent: ein erneuter Aufruf
     * mit derselben Größe ist ein No-Op (der Screen ruft es beim ersten Layout auf).
     */
    fun open(viewportWidth: Int, viewportHeight: Int) {
        if (opened || viewportWidth <= 0 || viewportHeight <= 0) return
        opened = true
        viewModelScope.launch {
            runCatching {
                ensureFontManager()
                val bytes = bytesLoader.load(bookId, forceStream)
                val startFraction = bytesLoader.startProgressFraction(bookId)
                val doc = withContext(Dispatchers.IO) {
                    CrengineDocument(bytes, ".epub", viewportWidth, viewportHeight).also {
                        it.applyLayout(ReflowConfig.DEFAULT)
                        it.seekToProgress(startFraction)
                    }
                }
                document = doc
                val pageCount = withContext(Dispatchers.IO) { doc.pageCount() }
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    pageCount = pageCount,
                    currentPage = 0,
                )
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = e.message ?: "Roman konnte nicht geöffnet werden",
                )
            }
        }
    }

    /** Rendert die reflowte Seite [index] (gecacht) in eine Bitmap. */
    suspend fun renderPage(index: Int): Bitmap? = withContext(Dispatchers.IO) {
        val doc = document ?: return@withContext null
        renderMutex.withLock {
            renderCache[index]?.let { return@withLock it }
            val rp = doc.renderPage(index, zoom = 1f, rotation = 0)
            Bitmap.createBitmap(rp.pixels, rp.width, rp.height, Bitmap.Config.ARGB_8888)
                .also { renderCache[index] = it }
        }
    }

    fun nextPage() = navigateTo(_uiState.value.currentPage + 1)

    fun prevPage() = navigateTo(_uiState.value.currentPage - 1)

    override fun navigateTo(page: Int) {
        val count = _uiState.value.pageCount
        if (count == 0) return
        val target = page.coerceIn(0, count - 1)
        if (target != _uiState.value.currentPage) {
            _uiState.value = _uiState.value.copy(currentPage = target)
        }
    }

    override fun onPageSettled(page: Int) {
        if (page != _uiState.value.currentPage) {
            _uiState.value = _uiState.value.copy(currentPage = page)
        }
    }

    override fun toggleChrome() {
        _uiState.value = _uiState.value.copy(chromeVisible = !_uiState.value.chromeVisible)
    }

    private fun collectButtonEvents() = viewModelScope.launch {
        bus.events.collect { event ->
            when (event.button) {
                HardwareButton.PAGE_NEXT, HardwareButton.VOLUME_DOWN -> nextPage()
                HardwareButton.PAGE_PREV, HardwareButton.VOLUME_UP -> prevPage()
            }
        }
    }

    /**
     * Initialisiert den crengine-Font-Manager einmalig mit der gebündelten
     * Bootstrap-Schrift. Muss vor dem ersten [CrengineDocument.applyLayout] laufen.
     */
    private suspend fun ensureFontManager() = withContext(Dispatchers.IO) {
        if (fontManagerReady) return@withContext
        val fontFile = File(context.cacheDir, BOOTSTRAP_FONT.substringAfterLast('/'))
        if (!fontFile.exists()) {
            context.assets.open(BOOTSTRAP_FONT).use { input ->
                fontFile.outputStream().use { input.copyTo(it) }
            }
        }
        CrengineNative.nativeInit(fontFile.absolutePath)
        fontManagerReady = true
    }

    override fun onCleared() {
        super.onCleared()
        document?.close()
        document = null
        renderCache.clear()
    }

    private companion object {
        const val BOOTSTRAP_FONT = "fonts/DejaVuSans.ttf"

        /** Der native Font-Manager ist prozessweit — nur einmal initialisieren. */
        @Volatile
        var fontManagerReady = false
    }
}
