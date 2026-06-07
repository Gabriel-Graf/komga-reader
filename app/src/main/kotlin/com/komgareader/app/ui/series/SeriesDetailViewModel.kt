package com.komgareader.app.ui.series

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgareader.app.data.KomgaSourceProvider
import com.komgareader.app.ui.reader.ViewerMode
import com.komgareader.data.download.DownloadManager
import com.komgareader.domain.model.Book
import com.komgareader.domain.model.ContentType
import com.komgareader.domain.model.ReadingDirection
import com.komgareader.domain.model.Series
import com.komgareader.domain.model.ViewerType
import com.komgareader.domain.repository.DownloadRepository
import com.komgareader.domain.repository.SeriesOverrideRepository
import com.komgareader.domain.repository.ServerConfig
import com.komgareader.domain.repository.ServerRepository
import com.komgareader.domain.repository.ShelfRepository
import com.komgareader.domain.usecase.ResolveViewerType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface SeriesDetailUiState {
    data object Loading : SeriesDetailUiState
    data object NoServer : SeriesDetailUiState
    data class Content(
        val books: List<Book>,
        val seriesTitle: String,
        val seriesRemoteId: String,
        val serverConfig: ServerConfig?,
        val seriesSummary: String? = null,
        val seriesStatus: String? = null,
        val seriesGenres: List<String> = emptyList(),
        val viewerModes: Map<String, String> = emptyMap(),
        /** Wirksamer Typ (Bibliothek ?: manuell); `null` = unbekannt → für den Chip. */
        val effectiveContentType: ContentType? = null,
        /** Manuell gesetzter Typ — preselektiert das Burger-Menü. */
        val manualContentType: ContentType? = null,
        /** Bibliotheks-Default (Vorrang vor manuell) — für optimistische Neuberechnung. */
        val libraryDefault: ContentType? = null,
        /** Leserichtung der Serie — für optimistische Viewer-Neuberechnung bei Typwechsel. */
        val readingDirection: ReadingDirection? = null,
    ) : SeriesDetailUiState
    data class Error(val message: String) : SeriesDetailUiState
}

/** Einmalige Rückmeldung an die UI (Snackbar). */
sealed interface SeriesDetailEvent {
    data class DownloadError(val message: String) : SeriesDetailEvent
    /** Serien-Download abgebrochen — genau eine Meldung, nicht pro Kapitel. */
    data object DownloadCancelled : SeriesDetailEvent
}

/** Download-Fortschritt pro Buch: bookRemoteId → Zustand. */
enum class BookDownloadStatus { REMOTE, DOWNLOADING, LOCAL }

/**
 * Fortschritt eines Serien-Downloads für die Button-Anzeige: [current]/[total] Kapitel
 * fertig, [bytesPerSec] aktuelle Geschwindigkeit (0 = noch unbekannt). Wird höchstens
 * einmal pro Sekunde aktualisiert, damit E-Ink nicht flackert.
 */
data class DownloadProgress(val current: Int, val total: Int, val bytesPerSec: Long)

/** Box für eine optimistische Typ-Zuweisung; [type] == null bedeutet „Auto" (kein Override). */
private data class TypeOverride(val type: ContentType?)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SeriesDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val servers: ServerRepository,
    private val sourceProvider: KomgaSourceProvider,
    private val downloadManager: DownloadManager,
    private val downloadRepository: DownloadRepository,
    private val shelfRepository: ShelfRepository,
    private val overrideRepository: SeriesOverrideRepository,
) : ViewModel() {

    private val seriesId: String = checkNotNull(savedStateHandle["seriesId"])
    private val shelfId: Long? = savedStateHandle.get<Long>("shelfId")
    private val resolveViewerType = ResolveViewerType()

    /** Optimistische Read-Status-Änderungen (bookRemoteId → gelesen), ohne Voll-Reload. */
    private val _readOverrides = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    /** Optimistischer manueller Typ (Box, da `null` ein gültiger Wert „Auto" ist). */
    private val _typeOverride = MutableStateFlow<TypeOverride?>(null)

    private val baseState: StateFlow<SeriesDetailUiState> =
        servers.config.flatMapLatest { config ->
            flow {
                emit(SeriesDetailUiState.Loading)
                val source = sourceProvider.from(config)
                if (config == null || source == null) { emit(SeriesDetailUiState.NoServer); return@flow }
                emit(runCatching { source.books(seriesId) }
                    .fold(
                        { books ->
                            // Reichhaltige Serien-Metadaten optional nachladen (Naht A).
                            // Ältere/abweichende Quellen können das nicht — dann Fallback.
                            val detail = runCatching { source.seriesDetail(seriesId) }.getOrNull()
                            // Serientitel: Serien-Detail > erstes Buch (seriesTitle) > seriesId
                            val resolvedTitle = detail?.title?.takeIf { it.isNotBlank() }
                                ?: books.firstOrNull()?.seriesTitle?.takeIf { it.isNotBlank() }
                                ?: seriesId
                            val libraryDefault: ContentType? = shelfId
                                ?.let { id -> shelfRepository.shelves.first().firstOrNull { it.id == id } }
                                ?.defaultContentType
                            val manualType: ContentType? = overrideRepository.get(source.id, seriesId)
                            // Bibliothek hat Vorrang vor manuell; beide speisen den Stufe-4-Fallback.
                            val effectiveType: ContentType? = libraryDefault ?: manualType
                            val seriesForResolve: Series = detail
                                ?: Series(id = 0, sourceId = 0, remoteId = seriesId, title = resolvedTitle)
                            val viewerModes = books.associate { book ->
                                book.remoteId to mapViewerMode(
                                    resolveViewerType(seriesForResolve, book, effectiveType),
                                ).name
                            }
                            SeriesDetailUiState.Content(
                                books = books,
                                seriesTitle = resolvedTitle,
                                seriesRemoteId = seriesId,
                                serverConfig = config,
                                seriesSummary = detail?.summary,
                                seriesStatus = detail?.status,
                                seriesGenres = detail?.genres ?: emptyList(),
                                viewerModes = viewerModes,
                                effectiveContentType = effectiveType,
                                manualContentType = manualType,
                                libraryDefault = libraryDefault,
                                readingDirection = seriesForResolve.readingDirection,
                            )
                        },
                        { SeriesDetailUiState.Error(it.message ?: "Bücher konnten nicht geladen werden") },
                    ))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SeriesDetailUiState.Loading)

    /**
     * Öffentlicher State = geladene Bücher + optimistische Read-/Typ-Änderungen. So aktualisieren
     * „gelesen markieren" und „Typ zuweisen" sofort nur den betroffenen Teil — ohne Voll-Reload
     * (Loading-Flash / E-Ink-Ghosting). Voll-Reload nur bei echtem Seitenwechsel (Server/Serie).
     */
    val state: StateFlow<SeriesDetailUiState> =
        combine(baseState, _readOverrides, _typeOverride) { base, readOv, typeOv ->
            if (base !is SeriesDetailUiState.Content) return@combine base
            var content = base
            // Typ optimistisch: Chip + Viewer-Modi neu berechnen (Bibliothek behält Vorrang).
            if (typeOv != null) {
                val manual = typeOv.type
                val effective = content.libraryDefault ?: manual
                val series = Series(
                    id = 0, sourceId = 0, remoteId = content.seriesRemoteId,
                    title = content.seriesTitle, readingDirection = content.readingDirection,
                )
                content = content.copy(
                    manualContentType = manual,
                    effectiveContentType = effective,
                    viewerModes = content.books.associate { book ->
                        book.remoteId to mapViewerMode(resolveViewerType(series, book, effective)).name
                    },
                )
            }
            // Read-Status optimistisch einblenden.
            if (readOv.isNotEmpty()) {
                content = content.copy(
                    books = content.books.map { b ->
                        readOv[b.remoteId]?.let { read ->
                            b.copy(readCompleted = read, lastReadPage = if (read) b.pageCount else null)
                        } ?: b
                    },
                )
            }
            content
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SeriesDetailUiState.Loading)

    /** Menge der lokal vorhandenen bookRemoteIds (aus DB). Reaktiv — aktualisiert sich sofort nach Download. */
    val localBookIds: StateFlow<Set<String>> = downloadRepository.downloads
        .map { list -> list.map { it.bookRemoteId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Bücher die gerade heruntergeladen werden (lokal verwaltet). */
    private val _downloadingIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadingIds: StateFlow<Set<String>> = _downloadingIds

    /** Einmalige Fehlermeldungen an die UI. */
    private val _events = MutableSharedFlow<SeriesDetailEvent>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    /** Aktiver Serien-Download (null = keiner läuft). */
    private val _downloadProgress = MutableStateFlow<DownloadProgress?>(null)
    val downloadProgress: StateFlow<DownloadProgress?> = _downloadProgress

    /** Laufender Serien-Download — für Abbrechen. */
    private var downloadJob: Job? = null

    /** True ab „Abbrechen" gedrückt bis das laufende Kapitel beendet ist (Button zeigt Laden). */
    private val _cancelling = MutableStateFlow(false)
    val cancelling: StateFlow<Boolean> = _cancelling

    /**
     * Lädt ALLE noch nicht lokal vorhandenen Kapitel der Serie nacheinander herunter und
     * meldet Fortschritt (Kapitel x/y) + Geschwindigkeit über [downloadProgress]. Die
     * Geschwindigkeit wird ≤1×/s neu berechnet, aber zwischen Kapiteln gehalten (kein Blinken).
     */
    fun downloadAll(books: List<Book>) {
        if (downloadJob?.isActive == true) return
        downloadJob = viewModelScope.launch {
            val config = servers.config.first() ?: run {
                _events.emit(SeriesDetailEvent.DownloadError("Kein Server verbunden")); return@launch
            }
            val source = sourceProvider.from(config) ?: run {
                _events.emit(SeriesDetailEvent.DownloadError("Quelle nicht verfügbar")); return@launch
            }
            val total = books.size
            val pending = books.filter { it.remoteId !in localBookIds.value }
            var done = total - pending.size
            // Geschwindigkeit über ein gleitendes 1-Sekunden-Fenster, das über Kapitelgrenzen
            // hinweg weiterläuft — der zuletzt berechnete Wert bleibt sichtbar, bis ein neuer kommt.
            var windowStart = android.os.SystemClock.elapsedRealtime()
            var windowBytes = 0L
            var speed = 0L
            _downloadProgress.value = DownloadProgress(done, total, 0L)
            try {
                for (book in pending) {
                    _downloadingIds.update { it + book.remoteId }
                    runCatching {
                        withContext(Dispatchers.IO) {
                            var fileLastRead = 0L
                            val bytes = source.downloadFile(book.remoteId) { read, _ ->
                                windowBytes += read - fileLastRead
                                fileLastRead = read
                                val now = android.os.SystemClock.elapsedRealtime()
                                val elapsed = now - windowStart
                                if (elapsed >= 1000) {
                                    speed = windowBytes * 1000 / elapsed
                                    windowStart = now
                                    windowBytes = 0L
                                    _downloadProgress.value = DownloadProgress(done, total, speed)
                                }
                            }
                            check(bytes.isNotEmpty()) { "Server lieferte leere Datei für ${book.remoteId}" }
                            downloadManager.store(
                                bookRemoteId = book.remoteId,
                                sourceId = book.sourceId,
                                seriesRemoteId = seriesId,
                                title = book.title,
                                format = book.format.name,
                                totalPages = book.pageCount,
                                bytes = bytes,
                            )
                        }
                    }.onFailure { e ->
                        // Abbruch nicht als Fehler melden — propagieren, damit das finally einmalig greift.
                        if (e is CancellationException) throw e
                        Log.e(TAG, "Download fehlgeschlagen: ${book.title}", e)
                        _events.emit(SeriesDetailEvent.DownloadError(e.message ?: "Download fehlgeschlagen"))
                    }
                    _downloadingIds.update { it - book.remoteId }
                    done++
                    // Kapitelgrenze: Zähler hochsetzen, zuletzt bekannte Geschwindigkeit halten.
                    _downloadProgress.value = DownloadProgress(done, total, speed)
                }
            } finally {
                _downloadingIds.value = emptySet()
                _downloadProgress.value = null
                if (_cancelling.value) {
                    // Genau EINE Abbruch-Meldung (tryEmit, da der Scope hier evtl. abgebrochen ist).
                    _events.tryEmit(SeriesDetailEvent.DownloadCancelled)
                    _cancelling.value = false
                }
            }
        }
    }

    /** Bricht den laufenden Serien-Download ab (kein weiteres Kapitel). */
    fun cancelDownloadAll() {
        if (downloadJob?.isActive != true) return
        _cancelling.value = true
        downloadJob?.cancel()
    }

    /** Entfernt alle lokalen Downloads der Serie. */
    fun removeAll(books: List<Book>) {
        viewModelScope.launch {
            books.forEach { book ->
                runCatching { downloadManager.delete(book.remoteId) }
                    .onFailure { Log.e(TAG, "Löschen fehlgeschlagen: ${book.remoteId}", it) }
            }
        }
    }


    fun download(book: Book) {
        viewModelScope.launch {
            val config = servers.config.first() ?: run {
                _events.emit(SeriesDetailEvent.DownloadError("Kein Server verbunden"))
                return@launch
            }
            val source = sourceProvider.from(config) ?: run {
                _events.emit(SeriesDetailEvent.DownloadError("Quelle nicht verfügbar"))
                return@launch
            }
            _downloadingIds.update { it + book.remoteId }
            runCatching {
                // Netzwerk + Datei-I/O explizit auf IO-Dispatcher ausführen
                withContext(Dispatchers.IO) {
                    val bytes = source.downloadFile(book.remoteId)
                    check(bytes.isNotEmpty()) { "Server lieferte leere Datei für ${book.remoteId}" }
                    downloadManager.store(
                        bookRemoteId = book.remoteId,
                        sourceId = book.sourceId,
                        seriesRemoteId = seriesId,
                        title = book.title,
                        format = book.format.name,
                        totalPages = book.pageCount,
                        bytes = bytes,
                    )
                    Log.i(TAG, "Download gespeichert: ${book.title} (${bytes.size} Bytes)")
                }
            }.onFailure { e ->
                Log.e(TAG, "Download fehlgeschlagen: ${book.title}", e)
                _events.emit(SeriesDetailEvent.DownloadError(e.message ?: "Download fehlgeschlagen"))
            }
            _downloadingIds.update { it - book.remoteId }
        }
    }

    /**
     * Markiert ein Kapitel server-seitig als gelesen/ungelesen und lädt danach die Liste neu,
     * damit Häkchen/Lesezeichen den neuen Server-Stand zeigen.
     */
    fun setRead(book: Book, read: Boolean) {
        // Sofort optimistisch einblenden — kein Voll-Reload der Seite.
        _readOverrides.update { it + (book.remoteId to read) }
        viewModelScope.launch {
            val config = servers.config.first() ?: return@launch
            val source = sourceProvider.from(config) ?: return@launch
            runCatching { source.setRead(book.remoteId, read, book.pageCount) }
                .onFailure { e ->
                    Log.e(TAG, "Read-Status setzen fehlgeschlagen: ${book.remoteId}", e)
                    _readOverrides.update { it - book.remoteId } // optimistisches Update zurücknehmen
                    _events.emit(SeriesDetailEvent.DownloadError(e.message ?: "Aktion fehlgeschlagen"))
                }
        }
    }

    /**
     * Weist diesem Werk manuell einen Inhaltstyp zu ([type] == null löscht ihn) und lädt neu,
     * damit Chip und Viewer-Auflösung den neuen Stand zeigen. Eine Bibliothek hat weiterhin Vorrang.
     */
    fun setType(type: ContentType?) {
        // Sofort optimistisch (Chip + Viewer) — kein Voll-Reload.
        _typeOverride.value = TypeOverride(type)
        viewModelScope.launch {
            val config = servers.config.first() ?: return@launch
            val source = sourceProvider.from(config) ?: return@launch
            runCatching { overrideRepository.set(source.id, seriesId, type) }
                .onFailure {
                    Log.e(TAG, "Typ setzen fehlgeschlagen", it)
                    _typeOverride.value = null // optimistisches Update zurücknehmen
                }
        }
    }

    fun removeDownload(bookRemoteId: String) {
        viewModelScope.launch {
            runCatching {
                downloadManager.delete(bookRemoteId)
            }.onFailure { e ->
                Log.e(TAG, "Löschen fehlgeschlagen: $bookRemoteId", e)
                _events.emit(SeriesDetailEvent.DownloadError(e.message ?: "Löschen fehlgeschlagen"))
            }
        }
    }

    private fun mapViewerMode(type: ViewerType): ViewerMode = when (type) {
        ViewerType.WEBTOON -> ViewerMode.WEBTOON
        else -> ViewerMode.PAGED // PAGED und EPUB lesen paginiert; EPUB-Buch wählt Reader per Format
    }

    companion object {
        private const val TAG = "SeriesDetailVM"

        /** Wandelt Bytes in menschenlesbare Größenangabe (KiB/MiB) um. */
        fun humanReadableSize(bytes: Long): String = when {
            bytes <= 0L -> "–"
            bytes < 1024L * 1024L -> "${bytes / 1024} KiB"
            else -> String.format("%.1f MiB", bytes / (1024.0 * 1024.0))
        }
    }
}
