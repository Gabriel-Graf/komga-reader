package com.komgareader.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgareader.app.data.KomgaSourceProvider
import com.komgareader.domain.model.Series
import com.komgareader.domain.repository.ServerRepository
import com.komgareader.domain.source.SourceFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LibraryUiState {
    data object NoServer : LibraryUiState
    data object Loading : LibraryUiState
    data class Content(val series: List<Series>, val apiKey: String) : LibraryUiState
    data class Error(val message: String) : LibraryUiState
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val servers: ServerRepository,
    private val sourceProvider: KomgaSourceProvider,
) : ViewModel() {

    private val _state = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val state: StateFlow<LibraryUiState> = _state

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        _state.value = LibraryUiState.Loading
        val config = servers.config.first()
        val source = sourceProvider.from(config)
        if (config == null || source == null) { _state.value = LibraryUiState.NoServer; return@launch }
        _state.value = try {
            val page = source.browse(page = 0, filter = SourceFilter())
            LibraryUiState.Content(series = page.items, apiKey = config.apiKey)
        } catch (e: Exception) {
            LibraryUiState.Error(e.message ?: "Verbindung fehlgeschlagen")
        }
    }.let {}
}
