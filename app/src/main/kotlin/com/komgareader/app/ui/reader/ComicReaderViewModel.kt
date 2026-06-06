package com.komgareader.app.ui.reader

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import com.komgareader.guidedview.GuidedNavigator
import com.komgareader.guidedview.GuidedPosition
import com.komgareader.guidedview.NormRect
import com.komgareader.guidedview.PanelGeometry
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Zustand des Comic-Readers für eine Seite und den Zoom-Status. */
data class ComicUiState(
    val guidedEnabled: Boolean = true,
    val zoomed: Boolean = false,
    val position: GuidedPosition = GuidedPosition(0, 0),
    /** Normalisierte Panels der aktuellen Seite (leer = Vollseite/Fallback). */
    val currentPanels: List<NormRect> = emptyList(),
    val chromeVisible: Boolean = false,
)

@HiltViewModel
class ComicReaderViewModel @Inject constructor(
    @ApplicationContext context: Context,
    imageLoader: ImageLoader,
) : ViewModel() {

    private val loader = ComicPageLoader(context, imageLoader)
    private val panelCache = mutableMapOf<Int, List<NormRect>>()
    private val unitsPerPage = mutableMapOf<Int, Int>()

    private val _uiState = MutableStateFlow(ComicUiState())
    val uiState: StateFlow<ComicUiState> = _uiState.asStateFlow()

    private var pageCount: Int = 0
    private var pages: List<String> = emptyList()
    private var headers: Map<String, String> = emptyMap()

    fun init(pageUrls: List<String>, authHeaders: Map<String, String>, startPage: Int) {
        if (pageUrls.isEmpty()) {
            pages = emptyList(); headers = emptyMap(); pageCount = 0
            _uiState.value = ComicUiState()
            return
        }
        pages = pageUrls
        headers = authHeaders
        pageCount = pageUrls.size
        _uiState.value = ComicUiState(position = GuidedPosition(startPage.coerceIn(0, pageCount - 1), 0))
        ensurePanels(_uiState.value.position.page)
    }

    /** Anzahl Navigations-Einheiten einer Seite (>=1). Unbekannt → 1 (Vollseite). */
    private fun unitsAt(page: Int): Int = unitsPerPage[page] ?: 1

    private fun ensurePanels(page: Int) {
        if (panelCache.containsKey(page) || page !in 0 until pageCount) return
        viewModelScope.launch {
            val det = loader.detect(pages[page], headers)
            val norms = det.panels.map { PanelGeometry.normalize(it, det.pageWidth, det.pageHeight) }
            panelCache[page] = norms
            unitsPerPage[page] = if (norms.size < 2) 1 else norms.size
            if (_uiState.value.position.page == page) {
                _uiState.value = _uiState.value.copy(currentPanels = norms)
            }
        }
    }

    /** Tap auf normalisierten Punkt der Vollseite: Panel treffen → dort zoomen. */
    fun onPageTap(xNorm: Float, yNorm: Float) {
        val s = _uiState.value
        if (!s.guidedEnabled || s.currentPanels.size < 2) { toggleChrome(); return }
        val hit = PanelGeometry.hitTest(xNorm, yNorm, s.currentPanels)
        if (hit == null) { toggleChrome(); return }
        _uiState.value = s.copy(zoomed = true, position = s.position.copy(unit = hit), chromeVisible = false)
    }

    fun next() = step(forward = true)
    fun previous() = step(forward = false)

    private fun step(forward: Boolean) {
        val s = _uiState.value
        if (!s.zoomed) return // Vollseiten-Blättern macht der Screen über den Pager
        val target = if (forward) GuidedNavigator.next(s.position, pageCount, ::unitsAt)
                     else GuidedNavigator.previous(s.position, pageCount, ::unitsAt)
        target ?: return
        ensurePanels(target.page)
        val panels = panelCache[target.page] ?: emptyList()
        // Zielseite ohne erkennbare Panels → Vollseite zeigen (Fallback), nicht zoomen.
        val zoomed = panels.size >= 2
        _uiState.value = s.copy(position = target, currentPanels = panels, zoomed = zoomed)
        ensurePanels(target.page + 1) // Nachbar vorausladen
    }

    fun zoomOut() { _uiState.value = _uiState.value.copy(zoomed = false) }

    fun toggleGuided() {
        val s = _uiState.value
        _uiState.value = s.copy(guidedEnabled = !s.guidedEnabled, zoomed = false)
    }

    fun toggleChrome() {
        _uiState.value = _uiState.value.copy(chromeVisible = !_uiState.value.chromeVisible)
    }

    /** Wird vom Screen aufgerufen, wenn die Vollseite gewechselt hat (Pager-Settle). */
    fun onPageSettled(page: Int) {
        if (page == _uiState.value.position.page) return
        _uiState.value = _uiState.value.copy(
            position = GuidedPosition(page, 0),
            currentPanels = panelCache[page] ?: emptyList(),
            zoomed = false,
        )
        ensurePanels(page)
        ensurePanels(page + 1)
    }
}
