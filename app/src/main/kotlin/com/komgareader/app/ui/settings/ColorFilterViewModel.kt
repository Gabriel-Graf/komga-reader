package com.komgareader.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgareader.app.data.AuthHeaders
import com.komgareader.app.data.KomgaSourceProvider
import com.komgareader.domain.model.ColorProfile
import com.komgareader.domain.repository.ColorProfileRepository
import com.komgareader.domain.repository.ServerRepository
import com.komgareader.domain.source.SourceFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Vorschau-Bildquelle: ein zufälliges Cover + die zugehörigen Auth-Header. */
data class PreviewCover(val url: String, val headers: Map<String, String>)

/** Editor-Werte (live, noch nicht persistiert). */
data class EditState(
    val baseProfileId: Long,
    val name: String,
    val saturation: Float,
    val contrast: Float,
    val brightness: Float,
    val builtIn: Boolean,
)

@HiltViewModel
class ColorFilterViewModel @Inject constructor(
    private val colorProfiles: ColorProfileRepository,
    private val servers: ServerRepository,
    private val sourceProvider: KomgaSourceProvider,
) : ViewModel() {

    val profiles = colorProfiles.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val active = colorProfiles.observeActive()
        .stateIn(viewModelScope, SharingStarted.Eagerly, ColorProfile.OFF)

    private val _edit = MutableStateFlow<EditState?>(null)
    val edit: StateFlow<EditState?> = _edit

    private val _preview = MutableStateFlow<PreviewCover?>(null)
    val preview: StateFlow<PreviewCover?> = _preview

    // Kandidaten-Cover + Header für die Vorschau; Verlauf der zuvor gezeigten Bilder. Beides nur
    // im Speicher — beim Schließen der Settings (ViewModel weg) bewusst verworfen.
    private var candidates: List<String> = emptyList()
    private var headers: Map<String, String> = emptyMap()
    private val history = ArrayDeque<String>()

    init { loadPreviewCover() }

    private fun loadPreviewCover() = viewModelScope.launch {
        val config = servers.config.first() ?: return@launch
        val source = sourceProvider.from(config) ?: return@launch
        headers = AuthHeaders.forCovers(config)
        candidates = runCatching { source.browse(0, SourceFilter()).items }
            .getOrNull()
            ?.mapNotNull { it.coverUrl }
            ?: emptyList()
        candidates.randomOrNull()?.let { _preview.value = PreviewCover(it, headers) }
    }

    /** Nächstes, zufällig anderes Vorschau-Cover; das aktuelle wandert in den Verlauf. */
    fun nextPreview() {
        val current = _preview.value?.url
        val next = candidates.filter { it != current }.randomOrNull() ?: return
        if (current != null) history.addLast(current)
        _preview.value = PreviewCover(next, headers)
    }

    /** Vorheriges Vorschau-Cover aus dem Verlauf (no-op, wenn keiner vorhanden). */
    fun previousPreview() {
        val prev = history.removeLastOrNull() ?: return
        _preview.value = PreviewCover(prev, headers)
    }

    fun setActive(id: Long) = viewModelScope.launch { colorProfiles.setActive(id) }

    /** Editor mit den Werten von [profile] öffnen. */
    fun beginEdit(profile: ColorProfile) {
        _edit.value = EditState(
            baseProfileId = profile.id, name = profile.name,
            saturation = profile.saturation, contrast = profile.contrast,
            brightness = profile.brightness, builtIn = profile.builtIn,
        )
    }

    /**
     * Neuen, editierbaren Profil-Entwurf öffnen (noch nicht gespeichert, `baseProfileId = 0`).
     * Werte vom aktiven Profil vorbefüllt, damit man von der aktuellen Anmutung (z. B. Go-7)
     * aus weitertunt. `builtIn = false` → Regler sind aktiv.
     */
    fun beginNewProfile() {
        val base = active.value
        _edit.value = EditState(
            baseProfileId = 0L, name = "",
            saturation = base.saturation, contrast = base.contrast,
            brightness = base.brightness, builtIn = false,
        )
    }

    fun cancelEdit() { _edit.value = null }

    fun updateSaturation(delta: Float) = mutate { it.copy(saturation = clamp(it.saturation + delta, 0.5f, 2f)) }
    fun updateContrast(delta: Float) = mutate { it.copy(contrast = clamp(it.contrast + delta, 0.5f, 2f)) }
    fun updateBrightness(delta: Float) = mutate { it.copy(brightness = clamp(it.brightness + delta, -0.5f, 0.5f)) }

    /** Aktuelle Editor-Werte als neues Custom-Profil speichern und aktiv setzen. */
    fun saveAsNew(name: String) = viewModelScope.launch {
        val e = _edit.value ?: return@launch
        val id = colorProfiles.upsert(
            ColorProfile(0, name.ifBlank { "Profil" }, e.saturation, e.contrast, e.brightness, builtIn = false),
        )
        colorProfiles.setActive(id)
        _edit.value = null
    }

    /** Bestehendes Custom-Profil aktualisieren. */
    fun updateExisting() = viewModelScope.launch {
        val e = _edit.value ?: return@launch
        if (e.builtIn) return@launch
        colorProfiles.upsert(ColorProfile(e.baseProfileId, e.name, e.saturation, e.contrast, e.brightness, builtIn = false))
        _edit.value = null
    }

    fun delete(id: Long) = viewModelScope.launch {
        colorProfiles.delete(id)
        if (_edit.value?.baseProfileId == id) _edit.value = null
    }

    private fun mutate(f: (EditState) -> EditState) { _edit.value = _edit.value?.let(f) }
    private fun clamp(v: Float, lo: Float, hi: Float) = v.coerceIn(lo, hi)
}
