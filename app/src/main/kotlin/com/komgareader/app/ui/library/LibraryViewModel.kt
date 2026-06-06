package com.komgareader.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgareader.app.data.KomgaSourceProvider
import com.komgareader.data.download.DownloadManager
import com.komgareader.domain.model.Series
import com.komgareader.domain.repository.ServerConfig
import com.komgareader.domain.repository.ServerRepository
import com.komgareader.domain.source.SourceFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface LibraryUiState {
    data object NoServer : LibraryUiState
    data object Loading : LibraryUiState
    data class Content(val series: List<Series>, val serverConfig: ServerConfig?) : LibraryUiState
    data class Error(val message: String) : LibraryUiState
}

/** Kurze Rückmeldung an die UI (Snackbar-ähnlich). */
sealed interface LibraryEvent {
    data class DownloadStarted(val count: Int) : LibraryEvent
    data object DownloadComplete : LibraryEvent
    data class DownloadError(val message: String) : LibraryEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val servers: ServerRepository,
    private val sourceProvider: KomgaSourceProvider,
    private val downloadManager: DownloadManager,
) : ViewModel() {

    private val refreshTrigger = MutableStateFlow(0)

    val state: StateFlow<LibraryUiState> =
        combine(servers.config, refreshTrigger) { config, _ -> config }
            .flatMapLatest { config ->
                flow {
                    emit(LibraryUiState.Loading)
                    val source = sourceProvider.from(config)
                    if (config == null || source == null) { emit(LibraryUiState.NoServer); return@flow }
                    emit(runCatching { source.browse(0, SourceFilter()) }
                        .fold(
                            { LibraryUiState.Content(it.items, config) },
                            { LibraryUiState.Error(it.message ?: "Verbindung fehlgeschlagen") },
                        ))
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState.Loading)

    val events = MutableSharedFlow<LibraryEvent>(extraBufferCapacity = 4)

    fun refresh() { refreshTrigger.value++ }

    fun downloadSeries(series: Series) {
        viewModelScope.launch {
            val config = servers.config.first() ?: return@launch
            val source = sourceProvider.from(config) ?: return@launch
            runCatching {
                val books = withContext(Dispatchers.IO) { source.books(series.remoteId) }
                events.emit(LibraryEvent.DownloadStarted(books.size))
                for (book in books) {
                    withContext(Dispatchers.IO) {
                        val bytes = source.downloadFile(book.remoteId)
                        downloadManager.store(
                            bookRemoteId = book.remoteId,
                            sourceId = book.sourceId,
                            seriesRemoteId = series.remoteId,
                            title = book.title,
                            format = book.format.name,
                            totalPages = book.pageCount,
                            bytes = bytes,
                        )
                    }
                }
                events.emit(LibraryEvent.DownloadComplete)
            }.onFailure { e ->
                events.emit(LibraryEvent.DownloadError(e.message ?: "Download fehlgeschlagen"))
            }
        }
    }
}
