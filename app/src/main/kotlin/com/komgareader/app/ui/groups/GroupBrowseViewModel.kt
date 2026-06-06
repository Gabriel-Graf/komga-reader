package com.komgareader.app.ui.groups

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgareader.app.data.KomgaSourceProvider
import com.komgareader.domain.model.Series
import com.komgareader.domain.model.Shelf
import com.komgareader.domain.repository.ServerConfig
import com.komgareader.domain.repository.ServerRepository
import com.komgareader.domain.repository.ShelfRepository
import com.komgareader.domain.source.SourceFilter
import com.komgareader.domain.usecase.ResolveViewerType
import com.komgareader.app.ui.reader.ViewerMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface GroupBrowseUiState {
    data object Loading : GroupBrowseUiState
    data object NoServer : GroupBrowseUiState
    data class Content(
        val shelf: Shelf,
        val series: List<Series>,
        val serverConfig: ServerConfig?,
        val viewerMode: ViewerMode,
    ) : GroupBrowseUiState
    data class Error(val message: String) : GroupBrowseUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GroupBrowseViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val shelfRepository: ShelfRepository,
    private val serverRepository: ServerRepository,
    private val sourceProvider: KomgaSourceProvider,
) : ViewModel() {

    private val shelfId: Long = checkNotNull(savedStateHandle["shelfId"])
    private val refreshTrigger = MutableStateFlow(0)
    private val resolveViewerType = ResolveViewerType()

    val state: StateFlow<GroupBrowseUiState> = combine(
        shelfRepository.shelves,
        serverRepository.config,
        refreshTrigger,
    ) { shelves, config, _ -> shelves to config }
        .flatMapLatest { (shelves, config) ->
            flow {
                val shelf = shelves.firstOrNull { it.id == shelfId }
                if (shelf == null) {
                    emit(GroupBrowseUiState.Error("Gruppe nicht gefunden"))
                    return@flow
                }
                emit(GroupBrowseUiState.Loading)
                val source = sourceProvider.from(config)
                if (config == null || source == null) {
                    emit(GroupBrowseUiState.NoServer)
                    return@flow
                }
                val mode = mapViewerType(resolveViewerType.forContentType(shelf.contentType))
                emit(runCatching { source.browse(0, SourceFilter()) }
                    .fold(
                        { result ->
                            GroupBrowseUiState.Content(
                                shelf = shelf,
                                series = result.items,
                                serverConfig = config,
                                viewerMode = mode,
                            )
                        },
                        { GroupBrowseUiState.Error(it.message ?: "Verbindung fehlgeschlagen") },
                    ))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupBrowseUiState.Loading)

    fun refresh() { refreshTrigger.value++ }

    private fun mapViewerType(type: com.komgareader.domain.model.ViewerType): ViewerMode = when (type) {
        com.komgareader.domain.model.ViewerType.WEBTOON -> ViewerMode.WEBTOON
        else -> ViewerMode.PAGED
    }
}
