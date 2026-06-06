package com.komgareader.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgareader.app.data.KomgaSourceProvider
import com.komgareader.domain.model.Series
import com.komgareader.domain.repository.ServerRepository
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
import javax.inject.Inject

sealed interface LibraryUiState {
    data object NoServer : LibraryUiState
    data object Loading : LibraryUiState
    data class Content(val series: List<Series>, val apiKey: String?) : LibraryUiState
    data class Error(val message: String) : LibraryUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val servers: ServerRepository,
    private val sourceProvider: KomgaSourceProvider,
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
                            { LibraryUiState.Content(it.items, config.apiKey) },
                            { LibraryUiState.Error(it.message ?: "Verbindung fehlgeschlagen") },
                        ))
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState.Loading)

    fun refresh() { refreshTrigger.value++ }
}
