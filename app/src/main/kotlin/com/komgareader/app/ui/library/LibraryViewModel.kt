package com.komgareader.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgareader.app.data.ActiveSource
import com.komgareader.app.data.localSeries
import com.komgareader.app.ui.common.holdSpinning
import com.komgareader.app.ui.common.UiError
import com.komgareader.app.ui.common.uiErrorOf
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
import kotlinx.coroutines.flow.asStateFlow
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
    data class Error(val error: UiError) : LibraryUiState
}

/** Kurze Rückmeldung an die UI (Snackbar-ähnlich). */
sealed interface LibraryEvent {
    data class DownloadStarted(val count: Int) : LibraryEvent
    data object DownloadComplete : LibraryEvent
    data class DownloadError(val error: UiError) : LibraryEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val servers: ServerRepository,
    private val active: ActiveSource,
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
                    // Multi-source: über ALLE konfigurierten Quellen (Naht A) aggregieren —
                    // n Komga, OPDS, später Plugin-Server, gemischt. Jede Serie trägt ihre
                    // `sourceId`, sodass das Öffnen die richtige Quelle trifft.
                    val sources = active.all()
                    if (config == null || sources.isEmpty()) {
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
                    val overrides = sources
                        .flatMap { runCatching { overrideRepository.all(it.id) }.getOrDefault(emptyMap()).entries }
                        .associate { it.key to it.value }
                    val results = sources.map { source ->
                        runCatching { source.browse(0, SourceFilter()).items }
                    }
                    emit(
                        if (results.any { it.isSuccess }) {
                            // Teilerfolg zählt: Werke aller erreichbaren Quellen zeigen.
                            val series = results.mapNotNull { it.getOrNull() }.flatten()
                            LibraryUiState.Content(
                                series, config,
                                effectiveTypes = resolveTypes(series, shelves, overrides),
                            )
                        } else {
                            // Keine Quelle erreichbar → nur lokal vorhandene Werke zeigen.
                            val local = downloadRepository.downloads.first().localSeries()
                            if (local.isNotEmpty()) {
                                LibraryUiState.Content(
                                    local, config, offline = true,
                                    effectiveTypes = resolveTypes(local, shelves, overrides),
                                )
                            } else {
                                val error = results.firstNotNullOfOrNull { it.exceptionOrNull() }
                                LibraryUiState.Error(uiErrorOf(error))
                            }
                        },
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState.Loading)

    val events = MutableSharedFlow<LibraryEvent>(extraBufferCapacity = 4)

    /** Spins the refresh button. A dedicated latch (not derived from [LibraryUiState.Loading]) — a
     *  fast/offline refresh would conflate Loading away and the icon would never animate. */
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    fun refresh() {
        refreshTrigger.value++
        viewModelScope.launch { _refreshing.holdSpinning() }
    }

    fun downloadSeries(series: Series) {
        viewModelScope.launch {
            val source = active.get(series.sourceId) ?: return@launch
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
                events.emit(LibraryEvent.DownloadError(uiErrorOf(e)))
            }
        }
    }
}
