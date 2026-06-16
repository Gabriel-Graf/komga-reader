package com.komgareader.app.ui.groups

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgareader.app.data.ActiveSource
import com.komgareader.app.data.localSeries
import com.komgareader.app.ui.common.ErrorKind
import com.komgareader.app.ui.common.holdSpinning
import com.komgareader.app.ui.common.UiError
import com.komgareader.app.ui.common.uiErrorOf
import com.komgareader.domain.model.Series
import com.komgareader.domain.model.Shelf
import com.komgareader.domain.repository.DownloadRepository
import com.komgareader.domain.repository.ServerConfig
import com.komgareader.domain.repository.ServerRepository
import com.komgareader.domain.repository.ShelfRepository
import com.komgareader.domain.source.SourceFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import javax.inject.Inject

sealed interface GroupBrowseUiState {
    data object Loading : GroupBrowseUiState
    data object NoServer : GroupBrowseUiState
    data class Content(
        val shelf: Shelf,
        val series: List<Series>,
        val serverConfig: ServerConfig?,
        /** true = Server nicht erreichbar, nur lokal verfügbare Werke gezeigt. */
        val offline: Boolean = false,
    ) : GroupBrowseUiState
    data class Error(val error: UiError) : GroupBrowseUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GroupBrowseViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val shelfRepository: ShelfRepository,
    private val serverRepository: ServerRepository,
    private val active: ActiveSource,
    private val downloadRepository: DownloadRepository,
) : ViewModel() {

    private val shelfId: Long = checkNotNull(savedStateHandle["shelfId"])
    private val refreshTrigger = MutableStateFlow(0)

    /** Serien mit lokalem Inhalt → Download-Badge statt Cloud. */
    val localSeriesIds: StateFlow<Set<String>> = downloadRepository.downloads
        .map { list -> list.mapTo(mutableSetOf()) { it.seriesRemoteId } as Set<String> }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val state: StateFlow<GroupBrowseUiState> = combine(
        shelfRepository.shelves,
        serverRepository.config,
        refreshTrigger,
    ) { shelves, config, _ -> shelves to config }
        .flatMapLatest { (shelves, config) ->
            flow {
                val shelf = shelves.firstOrNull { it.id == shelfId }
                if (shelf == null) {
                    emit(GroupBrowseUiState.Error(UiError(ErrorKind.NOT_FOUND, "")))
                    return@flow
                }
                emit(GroupBrowseUiState.Loading)
                // Quellen-agnostisch + multi-source: jede im Regal referenzierte Quelle (Naht A)
                // über ihre `sourceId` auflösen und mit ihren eigenen containerIds browsen, dann
                // mergen. Funktioniert für ein Regal mit einer Quelle (heute) wie mit mehreren.
                val resolved = shelf.sources.mapNotNull { ss ->
                    active.get(ss.sourceId)?.let { src -> src to ss.containerIds }
                }
                if (config == null || resolved.isEmpty()) {
                    // Getrennt: trotzdem lokale Werke zeigen, sonst „kein Server".
                    val local = downloadRepository.downloads.first().localSeries()
                    emit(
                        if (local.isNotEmpty()) GroupBrowseUiState.Content(shelf, local, config, offline = true)
                        else GroupBrowseUiState.NoServer,
                    )
                    return@flow
                }
                val results = resolved.map { (src, containerIds) ->
                    runCatching { src.browse(0, SourceFilter(containerIds = containerIds)).items }
                }
                emit(
                    if (results.any { it.isSuccess }) {
                        GroupBrowseUiState.Content(
                            shelf = shelf,
                            series = results.mapNotNull { it.getOrNull() }.flatten(),
                            serverConfig = config,
                        )
                    } else {
                        // Keine Quelle erreichbar → nur lokal vorhandene Werke der Regal-Quellen zeigen.
                        val downloads = downloadRepository.downloads.first()
                        val local = shelf.sources.flatMap { downloads.localSeries(it.sourceId) }
                        if (local.isNotEmpty()) {
                            GroupBrowseUiState.Content(shelf, local, config, offline = true)
                        } else {
                            val error = results.firstNotNullOfOrNull { it.exceptionOrNull() }
                            GroupBrowseUiState.Error(uiErrorOf(error))
                        }
                    },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupBrowseUiState.Loading)

    /** Spins the refresh button. Dedicated latch (not [GroupBrowseUiState.Loading]) so a fast/offline
     *  refresh — whose Loading would be conflated away — still shows a visible turn. */
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    fun refresh() {
        refreshTrigger.value++
        viewModelScope.launch { _refreshing.holdSpinning() }
    }
}
