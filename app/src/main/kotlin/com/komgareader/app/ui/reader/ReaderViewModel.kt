package com.komgareader.app.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgareader.app.data.KomgaSourceProvider
import com.komgareader.app.eink.HardwareButtonBus
import com.komgareader.domain.eink.HardwareButton
import com.komgareader.domain.model.ReadProgress
import com.komgareader.domain.repository.ServerRepository
import com.komgareader.domain.source.PageRef
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReaderUiState(
    val pages: List<PageRef> = emptyList(),
    val apiKey: String = "",
    val initialPage: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null,
    val chromeVisible: Boolean = true,
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val servers: ServerRepository,
    private val sourceProvider: KomgaSourceProvider,
    private val bus: HardwareButtonBus,
) : ViewModel() {

    private val bookId: String = checkNotNull(savedStateHandle["bookId"])

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    /** Von Buttons angeforderte Seite; Pager beobachtet dies und scrollt. */
    private val _requestedPage = MutableStateFlow(-1)
    val requestedPage: StateFlow<Int> = _requestedPage.asStateFlow()

    private val _currentPage = MutableStateFlow(0)

    init {
        loadBook()
        collectButtonEvents()
    }

    private fun loadBook() = viewModelScope.launch {
        val config = servers.config.first()
        val source = sourceProvider.from(config)
        if (config == null || source == null) {
            _state.value = _state.value.copy(isLoading = false, error = "Kein Server verbunden.")
            return@launch
        }
        runCatching {
            val pages = source.pages(bookId)
            val startPage = runCatching { source.pullProgress(bookId) }
                .getOrNull()
                ?.let { progress -> (progress.page - 1).coerceIn(0, pages.size - 1) }
                ?: 0
            _state.value = _state.value.copy(
                pages = pages,
                apiKey = config.apiKey,
                initialPage = startPage,
                isLoading = false,
            )
            _currentPage.value = startPage
        }.onFailure { e ->
            _state.value = _state.value.copy(
                isLoading = false,
                error = e.message ?: "Seiten konnten nicht geladen werden",
            )
        }
    }

    private fun collectButtonEvents() = viewModelScope.launch {
        bus.events.collect { event ->
            val pages = _state.value.pages
            if (pages.isEmpty()) return@collect
            val current = _currentPage.value
            val next = when (event.button) {
                HardwareButton.PAGE_NEXT, HardwareButton.VOLUME_DOWN ->
                    (current + 1).coerceAtMost(pages.size - 1)
                HardwareButton.PAGE_PREV, HardwareButton.VOLUME_UP ->
                    (current - 1).coerceAtLeast(0)
            }
            if (next != current) {
                _currentPage.value = next
                _requestedPage.value = next
            }
        }
    }

    fun onPageSettled(index: Int) {
        _currentPage.value = index
        val pages = _state.value.pages
        if (pages.isEmpty()) return
        viewModelScope.launch {
            val config = servers.config.first()
            val source = sourceProvider.from(config) ?: return@launch
            runCatching {
                source.pushProgress(
                    bookId,
                    ReadProgress(
                        bookId = 0,
                        page = index + 1,
                        totalPages = pages.size,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    /** Tap-Zonen-Navigation: Seite ansteuern (Pager beobachtet requestedPage). */
    fun navigateTo(index: Int) {
        if (index != _currentPage.value) {
            _currentPage.value = index
            _requestedPage.value = index
        }
    }

    fun toggleChrome() {
        _state.value = _state.value.copy(chromeVisible = !_state.value.chromeVisible)
    }
}
