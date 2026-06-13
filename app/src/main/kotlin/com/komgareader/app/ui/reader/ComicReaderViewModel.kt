package com.komgareader.app.ui.reader

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import com.komgareader.app.data.coil.SourceImage
import com.komgareader.app.eink.HardwareButtonBus
import com.komgareader.domain.eink.HardwareButton
import com.komgareader.domain.repository.SettingsRepository
import com.komgareader.guidedview.GuidedNavigator
import com.komgareader.guidedview.GuidedPosition
import com.komgareader.guidedview.NormRect
import com.komgareader.guidedview.PanelGeometry
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    /** Seitenverhältnis (Breite/Höhe) der aktuellen Seite; 0 = unbekannt. */
    val pageAspect: Float = 0f,
)

@HiltViewModel
class ComicReaderViewModel @Inject constructor(
    @ApplicationContext context: Context,
    imageLoader: ImageLoader,
    private val bus: HardwareButtonBus,
    private val settings: SettingsRepository,
) : ViewModel(), Viewer {

    private val loader = ComicPageLoader(context, imageLoader)
    private val panelCache = mutableMapOf<Int, List<NormRect>>()
    private val unitsPerPage = mutableMapOf<Int, Int>()
    private val aspectPerPage = mutableMapOf<Int, Float>()

    private val _uiState = MutableStateFlow(ComicUiState())
    val uiState: StateFlow<ComicUiState> = _uiState.asStateFlow()

    /** Overlay-Sichtbarkeit als eigener Flow für das geteilte [ReaderScaffold]. */
    override val chromeVisible: StateFlow<Boolean> = _uiState
        .map { it.chromeVisible }
        .stateIn(viewModelScope, SharingStarted.Eagerly, _uiState.value.chromeVisible)

    /** Debug: erkannte Panel-Rahmen als farbige Rechtecke über die Seite legen. */
    val showPanelOverlay: StateFlow<Boolean> =
        settings.guidedPanelOverlay.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private var pageCount: Int = 0
    private var pages: List<SourceImage> = emptyList()

    init {
        collectButtonEvents()
    }

    fun init(pageImages: List<SourceImage>, startPage: Int) {
        if (pageImages.isEmpty()) {
            pages = emptyList(); pageCount = 0
            _uiState.value = ComicUiState()
            return
        }
        pages = pageImages
        pageCount = pageImages.size
        _uiState.value = ComicUiState(position = GuidedPosition(startPage.coerceIn(0, pageCount - 1), 0))
        ensurePanels(_uiState.value.position.page)
    }

    /** Anzahl Navigations-Einheiten einer Seite (>=1). Unbekannt → 1 (Vollseite). */
    private fun unitsAt(page: Int): Int = unitsPerPage[page] ?: 1

    /** Detektiert die Panels einer Seite (idempotent gecacht) und gibt sie normalisiert zurück. */
    private suspend fun loadPanels(page: Int): List<NormRect> {
        if (page !in 0 until pageCount) return emptyList()
        panelCache[page]?.let { return it }
        val det = loader.detect(pages[page])
        val norms = det.panels.map { PanelGeometry.normalize(it, det.pageWidth, det.pageHeight) }
        // Degenerate-Guard: weniger als 2 Panels ODER ein Panel >85 % Seitenfläche → Vollseite.
        val usable = if (norms.size < 2 || PanelGeometry.maxAreaFraction(norms) > 0.85f) emptyList() else norms
        panelCache[page] = usable
        unitsPerPage[page] = if (usable.isEmpty()) 1 else usable.size
        if (det.pageWidth > 0 && det.pageHeight > 0) {
            aspectPerPage[page] = det.pageWidth.toFloat() / det.pageHeight
        }
        return usable
    }

    private fun ensurePanels(page: Int) {
        if (panelCache.containsKey(page) || page !in 0 until pageCount) return
        viewModelScope.launch {
            val norms = loadPanels(page)
            if (_uiState.value.position.page == page) {
                val aspect = aspectPerPage[page] ?: _uiState.value.pageAspect
                _uiState.value = _uiState.value.copy(currentPanels = norms, pageAspect = aspect)
            }
        }
    }

    /** Tap auf normalisierten Punkt der Vollseite. */
    fun onPageTap(xNorm: Float, yNorm: Float) {
        val s = _uiState.value
        if (s.guidedEnabled && s.currentPanels.size >= 2) {
            // Guided: getipptes Panel zoomen, Gutter/Rand → Chrome.
            val hit = PanelGeometry.hitTest(xNorm, yNorm, s.currentPanels)
            if (hit != null) {
                _uiState.value = s.copy(zoomed = true, position = s.position.copy(unit = hit), chromeVisible = false)
            } else {
                toggleChrome()
            }
            return
        }
        // Fallback (0/1 Panel) oder Guided aus: Drittel-Zonen zum Seitenblättern.
        when {
            xNorm < 1f / 3f -> pageRelative(-1)
            xNorm > 2f / 3f -> pageRelative(1)
            else -> toggleChrome()
        }
    }

    fun next() = step(forward = true)
    fun previous() = step(forward = false)

    private fun step(forward: Boolean) {
        if (!_uiState.value.zoomed) return // Vollseiten-Blättern macht der Screen über den Pager
        viewModelScope.launch {
            val s = _uiState.value
            // Nachbarseite zuerst sicher detektieren, damit unitsAt() korrekt ist
            // (Rückwärts muss auf dem ECHTEN letzten Panel der Vorseite landen, nicht auf 0).
            val neighbor = if (forward) s.position.page + 1 else s.position.page - 1
            if (neighbor in 0 until pageCount) loadPanels(neighbor)
            val target = (if (forward) GuidedNavigator.next(s.position, pageCount, ::unitsAt)
                          else GuidedNavigator.previous(s.position, pageCount, ::unitsAt))
                ?: return@launch
            val panels = panelCache[target.page] ?: emptyList()
            // Zielseite ohne erkennbare Panels → Vollseite (Fallback), sonst Panel zeigen.
            val zoomed = panels.size >= 2
            val aspect = aspectPerPage[target.page] ?: _uiState.value.pageAspect
            _uiState.value = _uiState.value.copy(position = target, currentPanels = panels, zoomed = zoomed, pageAspect = aspect)
            ensurePanels(target.page + 1) // Nachbar vorausladen
        }
    }

    fun zoomOut() { _uiState.value = _uiState.value.copy(zoomed = false) }

    private fun collectButtonEvents() = viewModelScope.launch {
        bus.events.collect { event ->
            if (pageCount == 0) return@collect
            val forward = when (event.button) {
                HardwareButton.PAGE_NEXT, HardwareButton.VOLUME_DOWN -> true
                HardwareButton.PAGE_PREV, HardwareButton.VOLUME_UP -> false
            }
            if (_uiState.value.zoomed) {
                if (forward) next() else previous()
            } else {
                pageRelative(if (forward) 1 else -1)
            }
        }
    }

    /** Ganze Seite blättern (nicht gezoomt). Der Screen zieht den Pager über position.page nach. */
    private fun pageRelative(delta: Int) {
        if (_uiState.value.zoomed) return
        val target = (_uiState.value.position.page + delta).coerceIn(0, pageCount - 1)
        if (target == _uiState.value.position.page) return
        _uiState.value = _uiState.value.copy(
            position = GuidedPosition(target, 0),
            currentPanels = panelCache[target] ?: emptyList(),
            zoomed = false,
            pageAspect = aspectPerPage[target] ?: _uiState.value.pageAspect,
        )
        ensurePanels(target)
        ensurePanels(target + 1)
    }

    fun toggleGuided() {
        val s = _uiState.value
        _uiState.value = s.copy(guidedEnabled = !s.guidedEnabled, zoomed = false)
    }

    override fun toggleChrome() {
        _uiState.value = _uiState.value.copy(chromeVisible = !_uiState.value.chromeVisible)
    }

    /** Wird vom Screen aufgerufen, wenn die Vollseite gewechselt hat (Pager-Settle). */
    override fun onPageSettled(page: Int) {
        if (page == _uiState.value.position.page) return
        _uiState.value = _uiState.value.copy(
            position = GuidedPosition(page, 0),
            currentPanels = panelCache[page] ?: emptyList(),
            zoomed = false,
            pageAspect = aspectPerPage[page] ?: _uiState.value.pageAspect,
        )
        ensurePanels(page)
        ensurePanels(page + 1)
    }

    /**
     * Tap-Zonen-/Scaffold-Navigation: absolute Vollseite ansteuern. Identisch zum
     * Pager-Settle (Vollseiten-Blättern erfolgt nur ungezoomt), damit der Pager
     * über `position.page` nachzieht. Der Comic-Screen blättert die Vollseite
     * heute selbst über den Pager; diese Methode erfüllt den geteilten Vertrag.
     */
    override fun navigateTo(page: Int) {
        if (_uiState.value.zoomed) return
        onPageSettled(page.coerceIn(0, (pageCount - 1).coerceAtLeast(0)))
    }
}
