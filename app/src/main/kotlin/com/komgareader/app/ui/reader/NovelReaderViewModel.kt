package com.komgareader.app.ui.reader

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgareader.app.data.PluginCatalog
import com.komgareader.app.di.ApplicationScope
import com.komgareader.app.eink.HardwareButtonBus
import com.komgareader.app.ui.common.UiError
import com.komgareader.app.ui.common.uiErrorOf
import com.komgareader.domain.eink.HardwareButton
import com.komgareader.domain.model.BookmarkMarkerStyle
import com.komgareader.domain.model.NovelBookmark
import com.komgareader.domain.render.Chapter
import com.komgareader.domain.render.Hyphenation
import com.komgareader.domain.render.NovelFonts
import com.komgareader.domain.render.NovelSettings
import com.komgareader.domain.render.ReflowConfig
import com.komgareader.domain.render.ReflowableDocument
import com.komgareader.domain.render.ReflowableDocumentFactory
import com.komgareader.domain.render.IntRect
import com.komgareader.domain.render.SearchHit
import com.komgareader.domain.render.resolveHyphenationLang
import com.komgareader.domain.render.WordHit
import com.komgareader.domain.repository.NovelBookmarkRepository
import com.komgareader.domain.repository.NovelProgressRepository
import com.komgareader.domain.repository.SettingsRepository
import com.komgareader.domain.usecase.ToggleResult
import com.komgareader.domain.usecase.toggleBookmark
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
import javax.inject.Inject

/** Zustand des Roman-Readers: reflowte Seiten zur aktuellen Viewport-Größe. */
data class NovelUiState(
    val loading: Boolean = true,
    val error: UiError? = null,
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val chromeVisible: Boolean = false,
    /**
     * Zähler, der bei **jedem** abgeschlossenen Re-Layout (Typografie-Änderung)
     * erhöht wird. Der Screen keyt das Seiten-Rendern zusätzlich hierauf, damit
     * nach einem Re-Layout eine frische Bitmap gerendert wird — auch wenn die
     * Seitenzahl unverändert bleibt. Sonst zeichnete Compose die alte (im Cache
     * gelöschte) Bitmap-Referenz weiter.
     */
    val layoutGeneration: Int = 0,
)

/**
 * Roman-Reader-ViewModel (ViewerType.NOVEL): öffnet das EPUB über die
 * crengine-Reflow-Engine (Naht B) und schichtet es zur Pixel-Größe des Viewports
 * um. Die Bytes kommen über denselben [EpubBytesLoader] wie der paginierte Pfad
 * (lokaler Download oder Stream) — kein eigener Abrufweg.
 *
 * Eigenes ViewModel (analog [ComicReaderViewModel]), das den geteilten
 * [Viewer]-Vertrag erfüllt und das [ReaderScaffold] speist. Die
 * Hardware-Tasten laufen über den [HardwareButtonBus] (vor/zurück blättern),
 * exakt wie in den anderen Readern.
 */
@HiltViewModel
class NovelReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val documentFactory: ReflowableDocumentFactory,
    private val bytesLoader: EpubBytesLoader,
    private val bus: HardwareButtonBus,
    private val settings: SettingsRepository,
    private val novelProgress: NovelProgressRepository,
    private val bookmarks: NovelBookmarkRepository,
    private val catalog: PluginCatalog,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel(), Viewer {

    private val bookId: String = checkNotNull(savedStateHandle["bookId"])
    private val forceStream: Boolean = savedStateHandle.get<Boolean>("stream") ?: false

    /** Quelle dieses Werks (Naht A) — aus der Navigation, nicht „die erste/aktive". */
    private val routeSourceId: Long = checkNotNull(savedStateHandle["sourceId"])

    private val _uiState = MutableStateFlow(NovelUiState())
    val uiState: StateFlow<NovelUiState> = _uiState.asStateFlow()

    /** EPUB content language read once after open() — drives "auto" hyphenation resolution. */
    private val _docLanguage = MutableStateFlow("")

    /**
     * Globale Roman-Typografie als [ReflowConfig], aus den sechs persistierten
     * Settings-Flows über den reinen [NovelSettings]-Mapper zusammengesetzt.
     * Single Source of Truth — die UI liest hier, schreibt über die Setter.
     *
     * The inner [combine] also takes [_docLanguage] so that when the hyphenation
     * setting is "auto", [resolveHyphenationLang] maps it to the document's language
     * (or "" = off when the language is unsupported or unknown). Explicit settings
     * ("", "de", "en") pass through unchanged.
     */
    val reflowConfig: StateFlow<ReflowConfig> = combine(
        settings.novelFontSizeEm,
        settings.novelLineHeight,
        settings.novelMarginPreset,
        settings.novelFontFamily,
        combine(
            settings.novelTextAlign,
            settings.novelHyphenationLang,
            settings.novelFontWeight,
            _docLanguage,
        ) { align, hyph, weight, docLang ->
            Triple(align, resolveHyphenationLang(hyph, docLang), weight)
        },
    ) { fontSizeEm, lineHeight, marginPreset, fontFamily, alignHyphWeight ->
        NovelSettings(
            fontSizeEm = fontSizeEm,
            lineHeight = lineHeight,
            marginPreset = marginPreset,
            fontFamily = fontFamily,
            textAlign = alignHyphWeight.first,
            hyphenationLang = alignHyphWeight.second,
            fontWeight = alignHyphWeight.third,
        ).toReflowConfig()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ReflowConfig.DEFAULT)

    val availableNovelFonts =
        catalog.allNovelFonts.stateIn(viewModelScope, SharingStarted.Eagerly, NovelFonts.ALL)
    val fontSampleFiles =
        catalog.fontSampleFiles.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

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
    private val _chapterStartPages = MutableStateFlow<List<Int>>(emptyList())

    private val _currentChapterTitle = MutableStateFlow<String?>(null)

    /** Titel des Kapitels, in dem die aktuelle Seite liegt (oder `null`, falls keins). */
    val currentChapterTitle: StateFlow<String?> = _currentChapterTitle.asStateFlow()

    /**
     * Kapitel-Startpositionen als Bruchteil 0–1 des Buchs (Startseite / letzte Seite) im
     * **aktuellen** Layout — die Punkte auf dem Fortschrittsbalken des Page-Footers.
     */
    val chapterFractions: StateFlow<List<Float>> =
        combine(_chapterStartPages, _uiState) { starts, ui ->
            val last = (ui.pageCount - 1).toFloat()
            if (last <= 0f) emptyList()
            else starts.map { (it / last).coerceIn(0f, 1f) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Werktitel + Autor aus den EPUB-Metadaten — für den eigenen Page-Header (links). */
    private val _bookTitle = MutableStateFlow("")
    val bookTitle: StateFlow<String> = _bookTitle.asStateFlow()
    private val _bookAuthor = MutableStateFlow("")
    val bookAuthor: StateFlow<String> = _bookAuthor.asStateFlow()

    /** Leseanteil 0–100 % der aktuellen Seite — für den Status-Fuß. */
    val progressPercent: StateFlow<Int> = _uiState
        .map { percentOf(it.currentPage, it.pageCount) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** Local-only bookmarks of this work (Naht A, never synced). */
    val bookmarksFlow: StateFlow<List<NovelBookmark>> =
        bookmarks.observe(routeSourceId, bookId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Ephemeral toggle: tapping a word sets/removes its bookmark while on. */
    private val _bookmarkMode = MutableStateFlow(false)
    val bookmarkMode: StateFlow<Boolean> = _bookmarkMode.asStateFlow()
    fun toggleBookmarkMode() { _bookmarkMode.value = !_bookmarkMode.value }

    /**
     * The xpointer last jumped to from the bookmark list. Its on-page marker is drawn extra-thick so
     * that, with several bookmarks on the same page, it is clear which word was opened.
     */
    private val _highlightedBookmark = MutableStateFlow<String?>(null)
    val highlightedBookmark: StateFlow<String?> = _highlightedBookmark.asStateFlow()

    /** How set bookmarks are drawn on the page (persisted setting). */
    val markerStyle: StateFlow<String> =
        settings.bookmarkMarkerStyle
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BookmarkMarkerStyle.UNDERLINE.name)

    private var document: ReflowableDocument? = null
    private val renderCache = mutableMapOf<Int, Bitmap>()
    private val renderMutex = Mutex()

    /**
     * Serialisiert **jeden** Zugriff auf das native [ReflowableDocument]:
     * die Reflow-Engine ist explizit nicht thread-sicher, der Reader berührt sie aber
     * aus mehreren `Dispatchers.IO`-Coroutinen (Re-Layout, Anker lesen/setzen, Suche,
     * Kapitel-Sondierung). Ohne diese Sperre würde z. B. [recomputeChapterStartPages]
     * (Seek-Schleife) mit [persistProgress] (`currentAnchor`) rennen und der native
     * Zustand korrumpieren. Alle doc-berührenden Blöcke laufen unter dieser Sperre.
     */
    private val documentMutex = Mutex()
    private var opened = false

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
                val bytes = bytesLoader.load(bookId, routeSourceId, forceStream)
                // EINE kohärente Wiederaufnahme: der lokale Xpointer ist die Wahrheit (exakt,
                // Schrift-/Viewport-unabhängig). Fehlt er, fällt es auf den groben Komga-%-Stand
                // zurück (geräteübergreifend). Anker hat IMMER Vorrang vor dem Anteil.
                val local = novelProgress.get(routeSourceId, bookId)
                val savedAnchor = local?.anchor?.takeIf { it.isNotEmpty() }
                val fallbackFraction = local?.fraction ?: bytesLoader.startProgressFraction(bookId, routeSourceId)
                // Initialisierung vollständig unter der Document-Sperre abschließen — öffnen,
                // umschichten, Position wiederherstellen, Kapitel laden + Startseiten sondieren —
                // BEVOR die Re-Layout-Beobachtung anläuft. So kann kein Re-Layout gleichzeitig
                // mit der Kapitel-Sondierung am nicht-thread-sicheren Dokument arbeiten.
                documentMutex.withLock {
                    val doc = withContext(Dispatchers.IO) {
                        documentFactory.open(bytes, ".epub", viewportWidth, viewportHeight)
                    }
                    // Read the document's declared language and publish it so that
                    // the combine inside reflowConfig can resolve "auto" hyphenation
                    // once it catches up. We also resolve it eagerly below to avoid
                    // a stale first applyLayout (the combine propagates asynchronously,
                    // so reflowConfig.first() still carries the pre-docLanguage value).
                    val docLang = withContext(Dispatchers.IO) { doc.contentLanguage() }
                    _docLanguage.value = docLang
                    // Build the initial config synchronously: take the raw settings,
                    // resolve hyphenation with the just-read docLang, and override.
                    // This guarantees that the first applyLayout already uses the correct
                    // hyphenation, so when the combine catches up and reflowConfig emits
                    // the same resolved value, cfg == appliedConfig holds and
                    // observeReflowConfig does NOT fire a redundant relayout.
                    val rawConfig = reflowConfig.first()
                    val resolvedHyph = resolveHyphenationLang(settings.novelHyphenationLang.first(), docLang)
                    val initialConfig = rawConfig.copy(
                        hyphenation = if (resolvedHyph.isBlank()) Hyphenation.Off else Hyphenation.Language(resolvedHyph),
                    )
                    appliedConfig = initialConfig
                    withContext(Dispatchers.IO) {
                        doc.applyLayout(initialConfig)
                        if (savedAnchor != null) doc.seekToAnchor(savedAnchor)
                        else doc.seekToProgress(fallbackFraction)
                    }
                    document = doc
                    lastSavedAnchor = savedAnchor
                    val (count, startPage) = withContext(Dispatchers.IO) {
                        doc.pageCount() to doc.currentPage()
                    }
                    // EPUB-Metadaten einmalig für den Page-Header lesen (layout-unabhängig).
                    _bookTitle.value = withContext(Dispatchers.IO) { doc.title() }
                    _bookAuthor.value = withContext(Dispatchers.IO) { doc.authors() }
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        pageCount = count,
                        currentPage = startPage.coerceIn(0, (count - 1).coerceAtLeast(0)),
                    )
                    loadChaptersLocked(doc)
                }
                refreshCurrentChapter()
                observeReflowConfig()
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = uiErrorOf(e),
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
        appliedConfig = cfg
        documentMutex.withLock {
            val doc = document ?: return
            val (pageCount, newPage) = withContext(Dispatchers.IO) {
                val anchor = doc.currentAnchor()
                doc.applyLayout(cfg)
                if (anchor.isNotEmpty()) doc.seekToAnchor(anchor)
                doc.pageCount() to doc.currentPage()
            }
            // Cache leeren OHNE zu recyceln: die aktuell angezeigte Bitmap wird von
            // Compose noch gezeichnet. Recyceln würde sie unter dem laufenden Frame
            // wegziehen ("trying to use a recycled bitmap"). Die Referenz fallen zu
            // lassen reicht — die nativen Pixel (Android O+ liegen sie im Native-Heap)
            // gibt der GC frei, sobald keine Composition sie mehr hält.
            renderMutex.withLock { renderCache.clear() }
            _uiState.value = _uiState.value.copy(
                pageCount = pageCount,
                currentPage = newPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0)),
                layoutGeneration = _uiState.value.layoutGeneration + 1,
            )
            // Die Kapitel-Anker bleiben gültig (Xpointer, layout-unabhängig), aber ihre
            // Seitenzuordnung hat sich verschoben -> Startseiten neu berechnen.
            recomputeChapterStartPagesLocked(doc)
        }
        refreshCurrentChapter()
    }

    /**
     * Lädt das Inhaltsverzeichnis (einmal je Dokument) und berechnet die Seiten-Startindizes.
     * **Voraussetzung:** der Aufrufer hält [documentMutex] — diese Variante greift selbst nicht.
     */
    private suspend fun loadChaptersLocked(doc: ReflowableDocument) {
        val list = withContext(Dispatchers.IO) { doc.chapters() }
        _chapters.value = list
        recomputeChapterStartPagesLocked(doc)
    }

    /**
     * Bestimmt je Kapitel die Seite seines Ankers im **aktuellen** Layout, ohne die
     * sichtbare Position zu verschieben: vor dem Sondieren wird der aktuelle Anker
     * gemerkt und danach wieder angesprungen. **Voraussetzung:** der Aufrufer hält
     * [documentMutex] — die Seek-Schleife darf nicht mit anderen Dokumentzugriffen rennen.
     */
    private suspend fun recomputeChapterStartPagesLocked(doc: ReflowableDocument) {
        val list = _chapters.value
        if (list.isEmpty()) {
            _chapterStartPages.value = emptyList()
            return
        }
        _chapterStartPages.value = withContext(Dispatchers.IO) {
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
        val starts = _chapterStartPages.value
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
    fun setFontWeight(value: Int) = viewModelScope.launch { settings.setNovelFontWeight(value) }.let {}

    /**
     * Rendert die reflowte Seite [index] (gecacht) in eine Bitmap. Der Cache-Treffer
     * läuft unter [renderMutex]; der Cache-Miss rendert nativ unter [documentMutex]
     * (das Dokument ist nicht thread-sicher). Lock-Reihenfolge stets documentMutex →
     * renderMutex, identisch zu [relayout] — kein Deadlock.
     */
    suspend fun renderPage(index: Int): Bitmap? = withContext(Dispatchers.IO) {
        renderMutex.withLock { renderCache[index] }?.let { return@withContext it }
        documentMutex.withLock {
            val doc = document ?: return@withContext null
            renderMutex.withLock { renderCache[index] }?.let { return@withLock it }
            val rp = doc.renderPage(index, zoom = 1f, rotation = 0)
            val bitmap = Bitmap.createBitmap(rp.pixels, rp.width, rp.height, Bitmap.Config.ARGB_8888)
            renderMutex.withLock { renderCache[index] = bitmap }
            bitmap
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
            val newPage = documentMutex.withLock {
                val doc = document ?: return@launch
                withContext(Dispatchers.IO) {
                    doc.seekToAnchor(anchor)
                    doc.currentPage()
                }
            }
            navigateTo(newPage)
        }
    }

    /** Springt an die relative Position [fraction] (0.0–1.0) — für „Gehe zu %". */
    fun goToProgress(fraction: Float) {
        viewModelScope.launch {
            val newPage = documentMutex.withLock {
                val doc = document ?: return@launch
                withContext(Dispatchers.IO) {
                    doc.seekToProgress(fraction.coerceIn(0f, 1f))
                    doc.currentPage()
                }
            }
            navigateTo(newPage)
        }
    }

    /** Volltextsuche off-main-thread; liefert die Treffer in Lesereihenfolge (leer = keine). */
    suspend fun search(query: String): List<SearchHit> {
        if (query.isBlank()) return emptyList()
        return documentMutex.withLock {
            val doc = document ?: return emptyList()
            withContext(Dispatchers.IO) { doc.search(query) }
        }
    }

    /** Tap at page-relative pixel ([x],[y]) in bookmark mode: set or remove the word's bookmark. */
    fun onWordTap(x: Int, y: Int) {
        viewModelScope.launch {
            // Pass the displayed page so the engine seeks to it before the hit-test — renderPage may
            // serve a cached bitmap without moving the native view, which would otherwise desync the
            // native "current page" from the displayed page and resolve the wrong page (or nothing).
            val page = _uiState.value.currentPage
            val hit: WordHit = documentMutex.withLock {
                val doc = document ?: return@launch
                withContext(Dispatchers.IO) { doc.wordAt(page, x, y) }
            } ?: run {
                android.util.Log.w("NovelReader", "onWordTap: no word at page=$page ($x,$y)")
                return@launch
            }
            when (val r = toggleBookmark(bookmarksFlow.value, hit.xpointer)) {
                is ToggleResult.Remove -> bookmarks.remove(r.id)
                is ToggleResult.Set -> bookmarks.add(
                    NovelBookmark(
                        id = 0, sourceId = routeSourceId, bookId = bookId,
                        xpointer = hit.xpointer, number = r.number, label = null,
                        snippet = hit.word, createdAt = System.currentTimeMillis(),
                        // New bookmarks adopt the active default marker style and black colour;
                        // the pinned selector only changes this default, not existing bookmarks.
                        markerStyle = markerStyle.value, color = 0xFF000000.toInt(),
                    ),
                )
            }
        }
    }

    /** Page-relative rects of the bookmarks that fall on the currently rendered page. */
    suspend fun bookmarkRectsForCurrentPage(): Map<String, IntRect> {
        val xps = bookmarksFlow.value.map { it.xpointer }
        if (xps.isEmpty()) return emptyMap()
        // Pass the displayed page (see onWordTap) so the native view is seeked to it before the
        // markers are measured — otherwise a stale native page would hide the displayed page's marks.
        val page = _uiState.value.currentPage
        return documentMutex.withLock {
            val doc = document ?: return emptyMap()
            withContext(Dispatchers.IO) { doc.rectsFor(page, xps) }
        }
    }

    /** Jump to a bookmark's (layout-independent) anchor — same path as TOC/search; highlight it. */
    fun jumpToBookmark(xpointer: String) {
        _highlightedBookmark.value = xpointer
        goToAnchor(xpointer)
    }

    fun renameBookmark(id: Long, label: String?) =
        viewModelScope.launch { bookmarks.rename(id, label?.ifBlank { null }) }

    fun deleteBookmark(id: Long) = viewModelScope.launch { bookmarks.remove(id) }

    /** Sets the persisted default marker style applied to newly created bookmarks. */
    fun setDefaultMarkerStyle(style: String) =
        viewModelScope.launch { settings.setBookmarkMarkerStyle(style) }

    /** Sets the content colour (ARGB) drawn for a single bookmark's marker. */
    fun setBookmarkColor(id: Long, color: Int) =
        viewModelScope.launch { bookmarks.setColor(id, color) }

    /** Sets the marker style of a single existing bookmark. */
    fun setBookmarkMarkerStyle(id: Long, style: String) =
        viewModelScope.launch { bookmarks.setMarkerStyle(id, style) }

    /** Removes the given bookmarks in one transaction (multi-select delete). */
    fun deleteBookmarks(ids: List<Long>) =
        viewModelScope.launch { bookmarks.removeMany(ids) }

    /** Applies one content colour (ARGB) to all given bookmarks (multi-select). */
    fun applyColorToBookmarks(ids: List<Long>, color: Int) =
        viewModelScope.launch { bookmarks.setColorMany(ids, color) }

    /** Applies one marker style to all given bookmarks (multi-select). */
    fun applyMarkerStyleToBookmarks(ids: List<Long>, style: String) =
        viewModelScope.launch { bookmarks.setMarkerStyleMany(ids, style) }

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
        val src = routeSourceId
        viewModelScope.launch {
            val anchor = documentMutex.withLock {
                val doc = document ?: return@launch
                withContext(Dispatchers.IO) { doc.currentAnchor() }
            }
            if (anchor.isEmpty() || anchor == lastSavedAnchor) return@launch
            lastSavedAnchor = anchor
            val fraction = readingFraction(page)
            novelProgress.save(src, bookId, anchor, fraction)
            if (bytesLoader.pushNovelFraction(bookId, src, fraction)) {
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
                HardwareButton.PAGE_NEXT -> nextPage()
                HardwareButton.PAGE_PREV -> prevPage()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Letzten Stand beim Schließen sichern: den Anker SYNCHRON lesen, solange das Dokument
        // noch offen ist; die (suspendierende) Persistenz + der %-Push laufen im injizierten
        // [appScope], da [viewModelScope] hier bereits abgebrochen wird (offline-first: der
        // Schreibvorgang darf nicht still verloren gehen).
        persistOnClose()
        document?.close()
        document = null
        // Bitmaps erst recyceln, dann den Cache leeren — sonst leckt der native Pixelspeicher.
        renderCache.values.forEach { it.recycle() }
        renderCache.clear()
    }

    private fun persistOnClose() {
        val doc = document ?: return
        val src = routeSourceId
        val anchor = doc.currentAnchor()
        if (anchor.isEmpty() || anchor == lastSavedAnchor) return
        lastSavedAnchor = anchor
        val fraction = readingFraction(_uiState.value.currentPage)
        appScope.launch {
            novelProgress.save(src, bookId, anchor, fraction)
            if (bytesLoader.pushNovelFraction(bookId, src, fraction)) {
                novelProgress.markSynced(src, bookId)
            }
        }
    }
}
