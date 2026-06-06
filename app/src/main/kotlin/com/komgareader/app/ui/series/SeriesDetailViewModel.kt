package com.komgareader.app.ui.series

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgareader.app.data.KomgaSourceProvider
import com.komgareader.data.download.DownloadManager
import com.komgareader.domain.model.Book
import com.komgareader.domain.repository.DownloadRepository
import com.komgareader.domain.repository.ServerConfig
import com.komgareader.domain.repository.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
import javax.inject.Inject

sealed interface SeriesDetailUiState {
    data object Loading : SeriesDetailUiState
    data object NoServer : SeriesDetailUiState
    data class Content(
        val books: List<Book>,
        val seriesTitle: String,
        val seriesRemoteId: String,
        val serverConfig: ServerConfig?,
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
) : ViewModel() {

    private val seriesId: String = checkNotNull(savedStateHandle["seriesId"])

    val state: StateFlow<SeriesDetailUiState> =
        servers.config.flatMapLatest { config ->
            flow {
                emit(SeriesDetailUiState.Loading)
                val source = sourceProvider.from(config)
                if (config == null || source == null) { emit(SeriesDetailUiState.NoServer); return@flow }
                emit(runCatching { source.books(seriesId) }
                    .fold(
                        { books ->
                            // Serientitel aus dem ersten Buch ableiten (seriesTitle-Feld),
                            // Fallback auf seriesId wenn leer
                            val resolvedTitle = books.firstOrNull()?.seriesTitle
                                ?.takeIf { it.isNotBlank() } ?: seriesId
                            SeriesDetailUiState.Content(books, resolvedTitle, seriesId, config)
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
