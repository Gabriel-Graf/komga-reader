package com.komgareader.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgareader.app.data.ActiveSource
import com.komgareader.app.data.coil.SourceCover
import com.komgareader.data.plugin.ColorPresetImporter
import com.komgareader.domain.model.ColorProfile
import com.komgareader.domain.model.DitherMode
import com.komgareader.domain.repository.ColorProfileRepository
import com.komgareader.domain.source.SourceFilter
import com.komgareader.plugin.ColorPresetSpec
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject


/** Editor-Werte (live, noch nicht persistiert). */
data class EditState(
    val baseProfileId: Long,
    val name: String,
    val saturation: Float,
    val contrast: Float,
    val brightness: Float,
    val blackPoint: Float,
    val whitePoint: Float,
    val gamma: Float,
    val sharpenAmount: Float,
    val sharpenRadius: Int,
    val ditherMode: DitherMode,
    val ditherLevels: Int,
    val builtIn: Boolean,
)

@HiltViewModel
class ColorFilterViewModel @Inject constructor(
    private val colorProfiles: ColorProfileRepository,
    private val activeSource: ActiveSource,
) : ViewModel() {

    val profiles = colorProfiles.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val active = colorProfiles.observeActive()
        .stateIn(viewModelScope, SharingStarted.Eagerly, ColorProfile.OFF)

    private val _edit = MutableStateFlow<EditState?>(null)
    val edit: StateFlow<EditState?> = _edit

    private val _preview = MutableStateFlow<SourceCover?>(null)
    val preview: StateFlow<SourceCover?> = _preview

    // True, sobald ein Verlauf existiert (steuert die Sichtbarkeit des „Vorheriges"-Buttons).
    private val _canGoBack = MutableStateFlow(false)
    val canGoBack: StateFlow<Boolean> = _canGoBack

    // Kandidaten-Cover für die Vorschau; Verlauf der zuvor gezeigten Bilder. Beides nur
    // im Speicher — beim Schließen der Settings (ViewModel weg) bewusst verworfen.
    private var candidates: List<SourceCover> = emptyList()
    private val history = ArrayDeque<SourceCover>()

    init { loadPreviewCover() }

    private fun loadPreviewCover() = viewModelScope.launch {
        val source = activeSource.current() ?: return@launch
        candidates = runCatching { source.browse(0, SourceFilter()).items }
            .getOrNull()
            ?.map { SourceCover(it.sourceId, it.remoteId, isSeries = true) }
            ?: emptyList()
        candidates.randomOrNull()?.let { _preview.value = it }
    }

    /** Nächstes, zufällig anderes Vorschau-Cover; das aktuelle wandert in den Verlauf. */
    fun nextPreview() {
        val current = _preview.value
        val next = candidates.filter { it != current }.randomOrNull() ?: return
        if (current != null) history.addLast(current)
        _canGoBack.value = history.isNotEmpty()
        _preview.value = next
    }

    /** Vorheriges Vorschau-Cover aus dem Verlauf (no-op, wenn keiner vorhanden). */
    fun previousPreview() {
        val prev = history.removeLastOrNull() ?: return
        _canGoBack.value = history.isNotEmpty()
        _preview.value = prev
    }

    fun setActive(id: Long) = viewModelScope.launch { colorProfiles.setActive(id) }

    /** Editor mit den Werten von [profile] öffnen. */
    fun beginEdit(profile: ColorProfile) {
        _edit.value = EditState(
            baseProfileId = profile.id, name = profile.name,
            saturation = profile.saturation, contrast = profile.contrast, brightness = profile.brightness,
            blackPoint = profile.blackPoint, whitePoint = profile.whitePoint, gamma = profile.gamma,
            sharpenAmount = profile.sharpenAmount, sharpenRadius = profile.sharpenRadius,
            ditherMode = profile.ditherMode, ditherLevels = profile.ditherLevels, builtIn = profile.builtIn,
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
            saturation = base.saturation, contrast = base.contrast, brightness = base.brightness,
            blackPoint = base.blackPoint, whitePoint = base.whitePoint, gamma = base.gamma,
            sharpenAmount = base.sharpenAmount, sharpenRadius = base.sharpenRadius,
            ditherMode = base.ditherMode, ditherLevels = base.ditherLevels, builtIn = false,
        )
    }

    fun cancelEdit() { _edit.value = null }

    fun updateSaturation(delta: Float) = mutate { it.copy(saturation = clamp(it.saturation + delta, ColorProfile.SATURATION_MIN, ColorProfile.SATURATION_MAX)) }
    fun updateContrast(delta: Float) = mutate { it.copy(contrast = clamp(it.contrast + delta, ColorProfile.CONTRAST_MIN, ColorProfile.CONTRAST_MAX)) }
    fun updateBrightness(delta: Float) = mutate { it.copy(brightness = clamp(it.brightness + delta, ColorProfile.BRIGHTNESS_MIN, ColorProfile.BRIGHTNESS_MAX)) }
    fun updateBlackPoint(delta: Float) = mutate { it.copy(blackPoint = clamp(it.blackPoint + delta, 0f, 0.4f)) }
    fun updateWhitePoint(delta: Float) = mutate { it.copy(whitePoint = clamp(it.whitePoint + delta, 0.6f, 1f)) }
    fun updateGamma(delta: Float) = mutate { it.copy(gamma = clamp(it.gamma + delta, 0.4f, 2.5f)) }
    fun updateSharpen(delta: Float) = mutate { it.copy(sharpenAmount = clamp(it.sharpenAmount + delta, 0f, 2f)) }
    fun updateSharpenRadius(delta: Int) = mutate { it.copy(sharpenRadius = (it.sharpenRadius + delta).coerceIn(1, 3)) }
    fun setDitherMode(mode: DitherMode) = mutate { it.copy(ditherMode = mode) }
    fun updateDitherLevels(delta: Int) = mutate { it.copy(ditherLevels = (it.ditherLevels + delta).coerceIn(2, 64)) }

    /** Aktuelle Editor-Werte als neues Custom-Profil speichern und aktiv setzen. */
    fun saveAsNew(name: String) = viewModelScope.launch {
        val e = _edit.value ?: return@launch
        val id = colorProfiles.upsert(e.toProfile(id = 0L, name = name.ifBlank { "Profil" }))
        colorProfiles.setActive(id)
        _edit.value = null
    }

    /** Bestehendes Custom-Profil aktualisieren. */
    fun updateExisting() = viewModelScope.launch {
        val e = _edit.value ?: return@launch
        if (e.builtIn) return@launch
        colorProfiles.upsert(e.toProfile(id = e.baseProfileId, name = e.name))
        _edit.value = null
    }

    private fun EditState.toProfile(id: Long, name: String) = ColorProfile(
        id = id, name = name, saturation = saturation, contrast = contrast, brightness = brightness,
        blackPoint = blackPoint, whitePoint = whitePoint, gamma = gamma,
        sharpenAmount = sharpenAmount, sharpenRadius = sharpenRadius,
        ditherMode = ditherMode, ditherLevels = ditherLevels, builtIn = false,
    )

    fun delete(id: Long) = viewModelScope.launch {
        colorProfiles.delete(id)
        if (_edit.value?.baseProfileId == id) _edit.value = null
    }

    /** Zeigt einen Fehler-Dialog nach einem fehlgeschlagenen Preset-Import. */
    private val _importError = MutableStateFlow(false)
    val importError: StateFlow<Boolean> = _importError

    fun dismissImportError() { _importError.value = false }

    /**
     * Importiert ein Color-Preset aus dem gelesenen JSON-Text (null = Datei konnte nicht gelesen
     * werden). Das JSON muss die Felder `abiVersion`, `name`, `saturation`, `contrast`,
     * `brightness` enthalten. Fehlerhafte, nicht-endliche oder inkompatible Specs sowie
     * ein null-Argument (Lesefehler) setzen [importError].
     */
    fun importPresetJson(json: String?) = viewModelScope.launch {
        if (json == null) { _importError.value = true; return@launch }
        val spec = runCatching {
            val obj = org.json.JSONObject(json)
            ColorPresetSpec(
                abiVersion = obj.getInt("abiVersion"),
                name = obj.getString("name"),
                saturation = obj.getDouble("saturation").toFloat(),
                contrast = obj.getDouble("contrast").toFloat(),
                brightness = obj.getDouble("brightness").toFloat(),
            )
        }.getOrNull()
        val profile = spec?.let { ColorPresetImporter.toProfileOrNull(it) }
        if (profile == null) {
            _importError.value = true
            return@launch
        }
        val id = colorProfiles.upsert(profile)
        colorProfiles.setActive(id)
    }

    private fun mutate(f: (EditState) -> EditState) { _edit.value = _edit.value?.let(f) }
    private fun clamp(v: Float, lo: Float, hi: Float) = v.coerceIn(lo, hi)
}
