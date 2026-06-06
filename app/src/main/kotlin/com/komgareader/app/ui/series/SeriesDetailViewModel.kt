package com.komgareader.app.ui.series

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgareader.app.data.KomgaSourceProvider
import com.komgareader.data.download.DownloadManager
import com.komgareader.domain.model.Book
import com.komgareader.domain.repository.DownloadRepository
import com.komgareader.domain.repository.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
    data class Content(val books: List<Book>, val seriesTitle: String, val apiKey: String?) : SeriesDetailUiState
    data class Error(val message: String) : SeriesDetailUiState
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
                        { SeriesDetailUiState.Content(it, seriesId, config.apiKey) },
                        { SeriesDetailUiState.Error(it.message ?: "Bücher konnten nicht geladen werden") },
                    ))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SeriesDetailUiState.Loading)

    /** Menge der lokal vorhandenen bookRemoteIds (aus DB). */
    val localBookIds: StateFlow<Set<String>> = downloadRepository.downloads
        .map { list -> list.map { it.bookRemoteId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Bücher die gerade heruntergeladen werden (lokal verwaltet). */
    private val _downloadingIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadingIds: StateFlow<Set<String>> = _downloadingIds

    fun download(book: Book) {
        viewModelScope.launch {
            val config = servers.config.first() ?: return@launch
            val source = sourceProvider.from(config) ?: return@launch
            _downloadingIds.update { it + book.remoteId }
            runCatching {
                val bytes = source.downloadFile(book.remoteId)
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
            _downloadingIds.update { it - book.remoteId }
        }
    }

    fun removeDownload(bookRemoteId: String) {
        viewModelScope.launch {
            downloadManager.delete(bookRemoteId)
        }
    }
}
