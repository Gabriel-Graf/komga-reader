package com.komgareader.app.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgareader.app.data.ActiveSource
import com.komgareader.app.data.coil.SourceCover
import com.komgareader.domain.model.ContentType
import com.komgareader.domain.model.Shelf
import com.komgareader.domain.model.ShelfSource
import com.komgareader.domain.repository.ServerConfig
import com.komgareader.domain.repository.ServerRepository
import com.komgareader.domain.repository.SettingsRepository
import com.komgareader.domain.repository.ShelfRepository
import com.komgareader.domain.source.ContainerSource
import com.komgareader.domain.source.SourceContainer
import com.komgareader.domain.source.SourceFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupsUiState(
    val shelves: List<Shelf> = emptyList(),
    val serverConfig: ServerConfig? = null,
    val serverSourceId: Long? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GroupsViewModel @Inject constructor(
    private val shelfRepository: ShelfRepository,
    private val serverRepository: ServerRepository,
    private val active: ActiveSource,
    private val settings: SettingsRepository,
) : ViewModel() {

    /** Anzeigemodus des Bibliotheken-Tabs (Liste/Kachel/große Kachel), persistiert. Default LIST. */
    val viewMode: StateFlow<String> =
        settings.librariesViewMode.stateIn(viewModelScope, SharingStarted.Eagerly, "LIST")

    fun setViewMode(mode: String) = viewModelScope.launch { settings.setLibrariesViewMode(mode) }

    val state: StateFlow<GroupsUiState> = combine(
        shelfRepository.shelves,
        serverRepository.config,
    ) { shelves, config -> shelves to config }
        .flatMapLatest { (shelves, config) ->
            flow {
                // Echte id der aktiven Quelle (Naht A, beliebige Quellenart) — kein
                // KOMGA-only-Nachrechnen. Neue Gruppen hängen an dieser Quelle.
                emit(
                    GroupsUiState(
                        shelves = shelves,
                        serverConfig = config,
                        serverSourceId = active.current()?.id,
                    ),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupsUiState())

    private val _containers = MutableStateFlow<List<SourceContainer>>(emptyList())
    val containers: StateFlow<List<SourceContainer>> = _containers

    /** Erste bis zu 4 Cover je Bibliothek (shelfId → Cover) für das Collage-Grid. */
    private val _covers = MutableStateFlow<Map<Long, List<SourceCover>>>(emptyMap())
    val covers: StateFlow<Map<Long, List<SourceCover>>> = _covers

    /**
     * Lädt für jede Bibliothek die ersten vier Titel und cacht deren Cover-URLs.
     * Wird bei Änderung der Bibliotheksliste vom Screen angestoßen.
     */
    fun loadCovers() {
        viewModelScope.launch {
            state.value.shelves.forEach { shelf ->
                // Multi-source: jede Quelle (Naht A) des Regals über ihre `sourceId` auflösen
                // und ihre ersten Cover beisteuern — bis zu 4 über alle Quellen des Regals.
                val covers = shelf.sources.flatMap { ss ->
                    val source = active.get(ss.sourceId) ?: return@flatMap emptyList()
                    runCatching {
                        source.browse(0, SourceFilter(containerIds = ss.containerIds))
                            .items
                            .map { SourceCover(it.sourceId, it.remoteId, isSeries = true) }
                    }.getOrDefault(emptyList())
                }.take(4)
                _covers.value = _covers.value + (shelf.id to covers)
            }
        }
    }

    /** Lädt die Library-Liste der verbundenen Quelle (für das Modal). */
    fun loadContainers() {
        viewModelScope.launch {
            val source = active.current()
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
}
