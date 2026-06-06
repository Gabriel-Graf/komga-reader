package com.komgareader.app.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgareader.domain.model.ContentType
import com.komgareader.domain.model.Shelf
import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.repository.ServerConfig
import com.komgareader.domain.repository.ServerRepository
import com.komgareader.domain.repository.ShelfRepository
import com.komgareader.domain.source.SourceId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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

    fun addGroup(name: String, contentType: ContentType) {
        val sourceId = state.value.serverSourceId ?: return
        viewModelScope.launch {
            shelfRepository.add(
                Shelf(
                    id = 0,
                    name = name.trim(),
                    contentType = contentType,
                    sourceIds = listOf(sourceId),
                ),
            )
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
