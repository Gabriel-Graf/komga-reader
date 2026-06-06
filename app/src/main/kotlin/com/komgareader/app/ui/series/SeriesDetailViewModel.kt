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
import com.komgareader.domain.model.Series
import com.komgareader.domain.model.ViewerType
import com.komgareader.domain.repository.DownloadRepository
import com.komgareader.domain.repository.ServerConfig
import com.komgareader.domain.repository.ServerRepository
import com.komgareader.domain.repository.ShelfRepository
import com.komgareader.domain.usecase.ResolveViewerType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    ) : SeriesDetailUiState
    data class Error(val message: String) : SeriesDetailUiState
}

/** Einmalige Rückmeldung an die UI (Snackbar). */
sealed interface SeriesDetailEvent {
    data class DownloadError(val message: String) : SeriesDetailEvent
}

/** Download-Fortschritt pro Buch: bookRemoteId → Zustand. */
enum class BookDownloadStatus { REMOTE, DOWNLOADING, LOCAL }

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SeriesDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val servers: ServerRepository,
    private val sourceProvider: KomgaSourceProvider,
    private val downloadManager: DownloadManager,
    private val downloadRepository: DownloadRepository,
    private val shelfRepository: ShelfRepository,
) : ViewModel() {

    private val seriesId: String = checkNotNull(savedStateHandle["seriesId"])
    private val shelfId: Long? = savedStateHandle.get<Long>("shelfId")
    private val resolveViewerType = ResolveViewerType()

    val state: StateFlow<SeriesDetailUiState> =
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
                            val fallback: ContentType? = shelfId
                                ?.let { id -> shelfRepository.shelves.first().firstOrNull { it.id == id } }
                                ?.defaultContentType
                            val seriesForResolve: Series = detail
                                ?: Series(id = 0, sourceId = 0, remoteId = seriesId, title = resolvedTitle)
                            val viewerModes = books.associate { book ->
                                book.remoteId to mapViewerMode(
                                    resolveViewerType(seriesForResolve, book, fallback),
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
                            )
                        },
                        { SeriesDetailUiState.Error(it.message ?: "Bücher konnten nicht geladen werden") },
                    ))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SeriesDetailUiState.Loading)

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
