package com.komgareader.app.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgareader.app.data.KomgaSourceProvider
import com.komgareader.domain.model.ContentType
import com.komgareader.domain.model.Shelf
import com.komgareader.domain.model.ShelfSource
import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.repository.ServerConfig
import com.komgareader.domain.repository.ServerRepository
import com.komgareader.domain.repository.ShelfRepository
import com.komgareader.domain.source.ContainerSource
import com.komgareader.domain.source.SourceContainer
import com.komgareader.domain.source.SourceId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupsUiState(
    val shelves: List<Shelf> = emptyList(),
    val serverConfig: ServerConfig? = null,
    val serverSourceId: Long? = null,
)

@HiltViewModel
class GroupsViewModel @Inject constructor(
    private val shelfRepository: ShelfRepository,
    private val serverRepository: ServerRepository,
    private val sourceProvider: KomgaSourceProvider,
) : ViewModel() {

    val state: StateFlow<GroupsUiState> = combine(
        shelfRepository.shelves,
        serverRepository.config,
    ) { shelves, config ->
        GroupsUiState(
            shelves = shelves,
            serverConfig = config,
            serverSourceId = config?.let { computeSourceId(it) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupsUiState())

    private val _containers = MutableStateFlow<List<SourceContainer>>(emptyList())
    val containers: StateFlow<List<SourceContainer>> = _containers

    /** Lädt die Library-Liste der verbundenen Quelle (für das Modal). */
    fun loadContainers() {
        viewModelScope.launch {
            val config = serverRepository.config.first()
            val source = sourceProvider.from(config)
            _containers.value = if (source is ContainerSource) {
                runCatching { source.listContainers() }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
        }
    }

    /** Erstellt (id == 0) oder aktualisiert eine App-Bibliothek. */
    fun saveGroup(id: Long, name: String, containerIds: List<String>, defaultContentType: ContentType?) {
        val sourceId = state.value.serverSourceId ?: return
        viewModelScope.launch {
            val shelf = Shelf(
                id = id,
                name = name.trim(),
                sources = listOf(ShelfSource(sourceId = sourceId, containerIds = containerIds)),
                defaultContentType = defaultContentType,
            )
            if (id == 0L) shelfRepository.add(shelf) else shelfRepository.update(shelf)
        }
    }

    fun deleteGroup(id: Long) {
        viewModelScope.launch { shelfRepository.delete(id) }
    }

    private fun computeSourceId(config: ServerConfig): Long {
        val normalizedBase = if (config.baseUrl.endsWith("/")) config.baseUrl else "${config.baseUrl}/"
        return SourceId.of(config.name, SourceKind.KOMGA, normalizedBase)
    }
}
