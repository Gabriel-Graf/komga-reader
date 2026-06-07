package com.komgareader.app.ui.reader

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgareader.app.eink.HardwareButtonBus
import com.komgareader.domain.eink.HardwareButton
import com.komgareader.domain.render.Chapter
import com.komgareader.domain.render.NovelSettings
import com.komgareader.domain.render.ReflowConfig
import com.komgareader.domain.render.SearchHit
import com.komgareader.domain.repository.NovelProgressRepository
import com.komgareader.domain.repository.SettingsRepository
import com.komgareader.render.crengine.CrengineDocument
import com.komgareader.render.crengine.CrengineNative
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    private val settings: SettingsRepository,
    private val novelProgress: NovelProgressRepository,
) : ViewModel(), ReaderChromeState {

    private val bookId: String = checkNotNull(savedStateHandle["bookId"])
    private val forceStream: Boolean = savedStateHandle.get<Boolean>("stream") ?: false

    private val _uiState = MutableStateFlow(NovelUiState())
    val uiState: StateFlow<NovelUiState> = _uiState.asStateFlow()

    /**
     * Globale Roman-Typografie als [ReflowConfig], aus den sechs persistierten
     * Settings-Flows über den reinen [NovelSettings]-Mapper zusammengesetzt.
     * Single Source of Truth — die UI liest hier, schreibt über die Setter.
     */
    val reflowConfig: StateFlow<ReflowConfig> = combine(
        settings.novelFontSizeEm,
        settings.novelLineHeight,
        settings.novelMarginPreset,
        settings.novelFontFamily,
        combine(settings.novelTextAlign, settings.novelHyphenationLang) { align, hyph -> align to hyph },
    ) { fontSizeEm, lineHeight, marginPreset, fontFamily, alignHyph ->
        NovelSettings(
            fontSizeEm = fontSizeEm,
            lineHeight = lineHeight,
            marginPreset = marginPreset,
            fontFamily = fontFamily,
            textAlign = alignHyph.first,
            hyphenationLang = alignHyph.second,
        ).toReflowConfig()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ReflowConfig.DEFAULT)

    /** Overlay-Sichtbarkeit als eigener Flow für das geteilte [ReaderScaffold]. */
    override val chromeVisible: StateFlow<Boolean> = _uiState
        .map { it.chromeVisible }
        .stateIn(viewModelScope, SharingStarted.Eagerly, _uiState.value.chromeVisible)

    /**
     * Inhaltsverzeichnis des geöffneten Romans (flach, tiefenmarkiert). Wird einmal
     * nach dem Öffnen/Re-Layout geladen — die Anker sind layout-unabhängig (Xpointer),
     * also bleibt die Liste über Typo-Änderungen hinweg gültig.
     */
    private val _chapters = MutableStateFlow<List<Chapter>>(emptyList())
    val chapters: StateFlow<List<Chapter>> = _chapters.asStateFlow()

    /**
     * Seiten-Startindex je Kapitel im **aktuellen** Layout: bestimmt das aktuelle
     * Kapitel aus der Seite. Muss nach jedem Re-Layout neu berechnet werden, weil
     * sich die Seitenzuordnung der (stabilen) Anker mit der Typografie verschiebt.
     */
    private var chapterStartPages: List<Int> = emptyList()

    private val _currentChapterTitle = MutableStateFlow<String?>(null)

    /** Titel des Kapitels, in dem die aktuelle Seite liegt (oder `null`, falls keins). */
    val currentChapterTitle: StateFlow<String?> = _currentChapterTitle.asStateFlow()

    /** Leseanteil 0–100 % der aktuellen Seite — für den Status-Fuß. */
    val progressPercent: StateFlow<Int> = _uiState
        .map { percentOf(it.currentPage, it.pageCount) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private var document: CrengineDocument? = null
    private val renderCache = mutableMapOf<Int, Bitmap>()
    private val renderMutex = Mutex()
    private var opened = false

    /** Id der aktiven Quelle (quellen-übergreifender `novel_progress`-Schlüssel). */
    private var sourceId: Long? = null

    /** Zuletzt persistierter Anker — verhindert redundante Schreib-/Push-Vorgänge je Seite. */
    private var lastSavedAnchor: String? = null

    /** Zuletzt auf das Dokument angewandte Typografie — verhindert ein redundantes
     *  Re-Layout für die bereits beim Öffnen verwendete Konfiguration. */
    private var appliedConfig: ReflowConfig? = null

    init {
        collectButtonEvents()
    }

    /**
     * Öffnet das EPUB für den [viewportWidth]×[viewportHeight] großen Viewport und
     * schichtet es mit der global persistierten Typografie ([reflowConfig]) um.
     * Idempotent: ein erneuter Aufruf mit derselben Größe ist ein No-Op (der Screen
     * ruft es beim ersten Layout auf). Danach läuft die Re-Layout-Beobachtung an.
     */
    fun open(viewportWidth: Int, viewportHeight: Int) {
        if (opened || viewportWidth <= 0 || viewportHeight <= 0) return
        opened = true
        viewModelScope.launch {
            runCatching {
                ensureFontManager()
                val bytes = bytesLoader.load(bookId, forceStream)
                sourceId = bytesLoader.activeSourceId()
                // EINE kohärente Wiederaufnahme: der lokale Xpointer ist die Wahrheit (exakt,
                // Schrift-/Viewport-unabhängig). Fehlt er, fällt es auf den groben Komga-%-Stand
                // zurück (geräteübergreifend). Anker hat IMMER Vorrang vor dem Anteil.
                val local = sourceId?.let { novelProgress.get(it, bookId) }
                val savedAnchor = local?.anchor?.takeIf { it.isNotEmpty() }
                val fallbackFraction = local?.fraction ?: bytesLoader.startProgressFraction(bookId)
                // Auf den ersten real persistierten Wert warten (nicht den Eagerly-Default),
                // damit beim Öffnen sofort mit der gespeicherten Typografie umgeschichtet wird.
                val initialConfig = reflowConfig.first()
                appliedConfig = initialConfig
                val doc = withContext(Dispatchers.IO) {
                    CrengineDocument(bytes, ".epub", viewportWidth, viewportHeight).also {
                        it.applyLayout(initialConfig)
                        if (savedAnchor != null) it.seekToAnchor(savedAnchor)
                        else it.seekToProgress(fallbackFraction)
                    }
                }
                lastSavedAnchor = savedAnchor
                document = doc
                val (pageCount, startPage) = withContext(Dispatchers.IO) {
                    doc.pageCount() to doc.currentPage()
                }
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    pageCount = pageCount,
                    currentPage = startPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0)),
                )
                loadChapters(doc)
                refreshCurrentChapter()
                observeReflowConfig()
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = e.message ?: "Roman konnte nicht geöffnet werden",
                )
            }
        }
    }

    /**
     * Schichtet das geöffnete Dokument bei jeder Typografie-Änderung neu um und
     * **erhält die Leseposition**: vor dem Re-Layout wird der stabile Anker der
     * aktuellen Stelle gemerkt, danach wieder angesprungen — so springt der Leser
     * beim Vergrößern der Schrift nicht auf eine zufällige Seite. Die bereits beim
     * Öffnen angewandte Konfiguration ([appliedConfig]) wird übersprungen.
     */
    private fun observeReflowConfig() = viewModelScope.launch {
        reflowConfig.collect { cfg ->
            if (cfg != appliedConfig) relayout(cfg)
        }
    }

    private suspend fun relayout(cfg: ReflowConfig) {
        val doc = document ?: return
        appliedConfig = cfg
        val (pageCount, newPage) = withContext(Dispatchers.IO) {
            val anchor = doc.currentAnchor()
            doc.applyLayout(cfg)
            if (anchor.isNotEmpty()) doc.seekToAnchor(anchor)
            doc.pageCount() to doc.currentPage()
        }
        renderMutex.withLock { renderCache.values.forEach { it.recycle() }; renderCache.clear() }
        _uiState.value = _uiState.value.copy(
            pageCount = pageCount,
            currentPage = newPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0)),
        )
        // Die Kapitel-Anker bleiben gültig (Xpointer, layout-unabhängig), aber ihre
        // Seitenzuordnung hat sich verschoben -> Startseiten neu berechnen.
        recomputeChapterStartPages(doc)
        refreshCurrentChapter()
    }

    /**
     * Lädt das Inhaltsverzeichnis (einmal je Dokument) und berechnet die Seiten-Startindizes.
     */
    private suspend fun loadChapters(doc: CrengineDocument) {
        val list = withContext(Dispatchers.IO) { doc.chapters() }
        _chapters.value = list
        recomputeChapterStartPages(doc)
    }

    /**
     * Bestimmt je Kapitel die Seite seines Ankers im **aktuellen** Layout, ohne die
     * sichtbare Position zu verschieben: vor dem Sondieren wird der aktuelle Anker
     * gemerkt und danach wieder angesprungen.
     */
    private suspend fun recomputeChapterStartPages(doc: CrengineDocument) {
        val list = _chapters.value
        if (list.isEmpty()) {
            chapterStartPages = emptyList()
            return
        }
        chapterStartPages = withContext(Dispatchers.IO) {
            val keep = doc.currentAnchor()
            val pages = list.map { chapter ->
                if (chapter.anchor.isEmpty()) {
                    0
                } else {
                    doc.seekToAnchor(chapter.anchor)
                    doc.currentPage()
                }
            }
            if (keep.isNotEmpty()) doc.seekToAnchor(keep)
            pages
        }
    }

    /** Setzt [currentChapterTitle] auf das letzte Kapitel, dessen Startseite ≤ aktueller Seite liegt. */
    private fun refreshCurrentChapter() {
        val list = _chapters.value
        val starts = chapterStartPages
        if (list.isEmpty() || starts.size != list.size) {
            _currentChapterTitle.value = null
            return
        }
        val page = _uiState.value.currentPage
        val index = starts.indexOfLast { it <= page }
        _currentChapterTitle.value = list.getOrNull(index)?.title
    }

    fun setFontSizeEm(value: Float) = viewModelScope.launch { settings.setNovelFontSizeEm(value) }.let {}
    fun setLineHeight(value: Float) = viewModelScope.launch { settings.setNovelLineHeight(value) }.let {}
    fun setMargin(preset: String) = viewModelScope.launch { settings.setNovelMarginPreset(preset) }.let {}
    fun setFontFamily(family: String) = viewModelScope.launch { settings.setNovelFontFamily(family) }.let {}
    fun setTextAlign(align: String) = viewModelScope.launch { settings.setNovelTextAlign(align) }.let {}
    fun setHyphenation(lang: String) = viewModelScope.launch { settings.setNovelHyphenationLang(lang) }.let {}

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
            refreshCurrentChapter()
        }
    }

    /**
     * Springt zum (layout-unabhängigen) Anker — für TOC-Auswahl und Suchtreffer.
     * Liest die neue Seite aus dem aktuellen Layout und übernimmt sie als
     * Leseposition; der Screen löst daraufhin den Full-Refresh aus (wie beim Blättern).
     */
    fun goToAnchor(anchor: String) {
        if (anchor.isEmpty()) return
        viewModelScope.launch {
            val doc = document ?: return@launch
            val newPage = withContext(Dispatchers.IO) {
                doc.seekToAnchor(anchor)
                doc.currentPage()
            }
            navigateTo(newPage)
        }
    }

    /** Springt an die relative Position [fraction] (0.0–1.0) — für „Gehe zu %". */
    fun goToProgress(fraction: Float) {
        viewModelScope.launch {
            val doc = document ?: return@launch
            val newPage = withContext(Dispatchers.IO) {
                doc.seekToProgress(fraction.coerceIn(0f, 1f))
                doc.currentPage()
            }
            navigateTo(newPage)
        }
    }

    /** Volltextsuche off-main-thread; liefert die Treffer in Lesereihenfolge (leer = keine). */
    suspend fun search(query: String): List<SearchHit> {
        val doc = document ?: return emptyList()
        if (query.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) { doc.search(query) }
    }

    override fun onPageSettled(page: Int) {
        if (page != _uiState.value.currentPage) {
            _uiState.value = _uiState.value.copy(currentPage = page)
            refreshCurrentChapter()
        }
        persistProgress(page)
    }

    /**
     * Schreibt den Roman-Fortschritt **lokal zuerst** (Xpointer + grober Anteil, `dirty = true`)
     * und pusht denselben Anteil als Prozent zu Komga — über denselben `pushProgress`-Pfad wie
     * die anderen Reader. Gelingt der Push, wird der Eintrag als synchronisiert markiert; sonst
     * bleibt er `dirty` (offline-first). Redundante Schreibvorgänge auf demselben Anker werden
     * übersprungen.
     */
    private fun persistProgress(page: Int) {
        val doc = document ?: return
        val src = sourceId ?: return
        viewModelScope.launch {
            val anchor = withContext(Dispatchers.IO) { doc.currentAnchor() }
            if (anchor.isEmpty() || anchor == lastSavedAnchor) return@launch
            lastSavedAnchor = anchor
            val fraction = readingFraction(page)
            novelProgress.save(src, bookId, anchor, fraction)
            if (bytesLoader.pushNovelFraction(bookId, fraction)) {
                novelProgress.markSynced(src, bookId)
            }
        }
    }

    /** Leseanteil 0.0..1.0 aus Seitenindex und Seitenzahl. Letzte Seite = 1.0, sonst index/(N-1). */
    private fun readingFraction(page: Int): Float {
        val lastIndex = (_uiState.value.pageCount - 1).coerceAtLeast(1)
        return (page.toFloat() / lastIndex).coerceIn(0f, 1f)
    }

    /** Leseanteil als ganze Prozent (0–100) für den Status-Fuß. */
    private fun percentOf(page: Int, pageCount: Int): Int {
        if (pageCount <= 1) return 0
        return ((page.toFloat() / (pageCount - 1)) * 100f).toInt().coerceIn(0, 100)
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
        // Letzten Stand beim Schließen sichern: den Anker SYNCHRON lesen, solange das Dokument
        // noch offen ist; die (suspendierende) Persistenz + der %-Push laufen in einem vom
        // VM-Lifecycle entkoppelten Scope, da [viewModelScope] hier bereits abgebrochen wird.
        persistOnClose()
        document?.close()
        document = null
        renderCache.clear()
    }

    private fun persistOnClose() {
        val doc = document ?: return
        val src = sourceId ?: return
        val anchor = doc.currentAnchor()
        if (anchor.isEmpty() || anchor == lastSavedAnchor) return
        lastSavedAnchor = anchor
        val fraction = readingFraction(_uiState.value.currentPage)
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            novelProgress.save(src, bookId, anchor, fraction)
            if (bytesLoader.pushNovelFraction(bookId, fraction)) {
                novelProgress.markSynced(src, bookId)
            }
        }
    }

    private companion object {
        const val BOOTSTRAP_FONT = "fonts/DejaVuSans.ttf"

        /** Der native Font-Manager ist prozessweit — nur einmal initialisieren. */
        @Volatile
        var fontManagerReady = false
    }
}
