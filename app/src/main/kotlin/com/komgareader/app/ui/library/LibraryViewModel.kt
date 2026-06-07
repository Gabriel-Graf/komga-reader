package com.komgareader.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgareader.app.data.KomgaSourceProvider
import com.komgareader.app.data.localSeries
import com.komgareader.data.download.DownloadManager
import com.komgareader.domain.model.ContentType
import com.komgareader.domain.model.Series
import com.komgareader.domain.model.Shelf
import com.komgareader.domain.repository.DownloadRepository
import com.komgareader.domain.repository.SeriesOverrideRepository
import com.komgareader.domain.repository.ServerConfig
import com.komgareader.domain.repository.ServerRepository
import com.komgareader.domain.repository.ShelfRepository
import com.komgareader.domain.source.SourceFilter
import com.komgareader.domain.usecase.ResolveShelfContentType
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface LibraryUiState {
    data object NoServer : LibraryUiState
    data object Loading : LibraryUiState
    data class Content(
        val series: List<Series>,
        val serverConfig: ServerConfig?,
        val offline: Boolean = false,
        /** Effektiver Werk-Typ je Serie (remoteId → Typ) — derselbe Wert wie das Typ-Tag. */
        val effectiveTypes: Map<String, ContentType?> = emptyMap(),
    ) : LibraryUiState
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
    private val downloadRepository: DownloadRepository,
    private val shelfRepository: ShelfRepository,
    private val overrideRepository: SeriesOverrideRepository,
) : ViewModel() {

    private val refreshTrigger = MutableStateFlow(0)
    private val resolveShelfContentType = ResolveShelfContentType()

    /**
     * Effektiver Werk-Typ je Serie — **dieselbe Regel wie das Typ-Tag** (Bibliotheks-Default
     * hat Vorrang vor manueller Zuweisung), damit der Filter exakt nach dem sichtbaren Tag
     * filtert. Siehe [com.komgareader.app.ui.series.SeriesDetailViewModel].
     */
    private fun resolveTypes(
        series: List<Series>,
        shelves: List<Shelf>,
        overrides: Map<String, ContentType>,
    ): Map<String, ContentType?> =
        series.associate { item ->
            item.remoteId to (resolveShelfContentType(item, shelves) ?: overrides[item.remoteId])
        }

    /** Serien mit lokalem Inhalt → Download-Badge statt Cloud. */
    val localSeriesIds: StateFlow<Set<String>> = downloadRepository.downloads
        .map { list -> list.mapTo(mutableSetOf()) { it.seriesRemoteId } as Set<String> }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val state: StateFlow<LibraryUiState> =
        combine(servers.config, refreshTrigger, shelfRepository.shelves) { config, _, shelves ->
            config to shelves
        }
            .flatMapLatest { (config, shelves) ->
                flow {
                    emit(LibraryUiState.Loading)
                    val source = sourceProvider.from(config)
                    if (config == null || source == null) {
                        // Getrennt: trotzdem lokale Werke zeigen, sonst „kein Server".
                        val local = downloadRepository.downloads.first().localSeries()
                        emit(
                            if (local.isNotEmpty()) {
                                LibraryUiState.Content(
                                    local, config, offline = true,
                                    effectiveTypes = resolveTypes(local, shelves, emptyMap()),
                                )
                            } else {
                                LibraryUiState.NoServer
                            },
                        )
                        return@flow
                    }
                    val overrides = runCatching { overrideRepository.all(source.id) }.getOrDefault(emptyMap())
                    emit(runCatching { source.browse(0, SourceFilter()) }
                        .fold(
                            {
                                LibraryUiState.Content(
                                    it.items, config,
                                    effectiveTypes = resolveTypes(it.items, shelves, overrides),
                                )
                            },
                            { error ->
                                // Server weg → nur lokal vorhandene Werke zeigen.
                                val local = downloadRepository.downloads.first().localSeries(source.id)
                                if (local.isNotEmpty()) {
                                    LibraryUiState.Content(
                                        local, config, offline = true,
                                        effectiveTypes = resolveTypes(local, shelves, overrides),
                                    )
                                } else {
                                    LibraryUiState.Error(error.message ?: "Verbindung fehlgeschlagen")
                                }
                            },
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
                            seriesTitle = series.title,
                            seriesCoverUrl = series.coverUrl,
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
