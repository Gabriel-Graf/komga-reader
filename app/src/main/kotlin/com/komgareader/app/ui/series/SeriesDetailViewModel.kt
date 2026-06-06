package com.komgareader.app.ui.series

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgareader.app.data.KomgaSourceProvider
import com.komgareader.domain.model.Book
import com.komgareader.domain.repository.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface SeriesDetailUiState {
    data object Loading : SeriesDetailUiState
    data object NoServer : SeriesDetailUiState
    data class Content(val books: List<Book>, val seriesTitle: String, val apiKey: String) : SeriesDetailUiState
    data class Error(val message: String) : SeriesDetailUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SeriesDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val servers: ServerRepository,
    private val sourceProvider: KomgaSourceProvider,
) : ViewModel() {

    private val seriesId: String = checkNotNull(savedStateHandle["seriesId"])

    val state: StateFlow<SeriesDetailUiState> =
        servers.config.flatMapLatest { config ->
            flow {
                emit(SeriesDetailUiState.Loading)
                val source = sourceProvider.from(config)
                if (config == null || source == null) { emit(SeriesDetailUiState.NoServer); return@flow }
                emit(runCatching { source.books(seriesId) }
                    .fold(
                        { SeriesDetailUiState.Content(it, seriesId, config.apiKey) },
                        { SeriesDetailUiState.Error(it.message ?: "Bücher konnten nicht geladen werden") },
                    ))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SeriesDetailUiState.Loading)
}
