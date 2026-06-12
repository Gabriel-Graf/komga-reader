package com.komgareader.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgareader.app.data.PluginCatalog
import com.komgareader.app.data.SourceRegistration
import com.komgareader.app.data.SyncCoordinator
import com.komgareader.domain.model.ColorProfile
import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.render.NovelFonts
import com.komgareader.domain.repository.CollectionRepository
import com.komgareader.domain.repository.ColorProfileRepository
import com.komgareader.domain.repository.KomgaUrl
import com.komgareader.domain.repository.ServerConfig
import com.komgareader.domain.repository.ServerRepository
import com.komgareader.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val servers: ServerRepository,
    private val colorProfiles: ColorProfileRepository,
    private val registration: SourceRegistration,
    private val collections: CollectionRepository,
    private val coordinator: SyncCoordinator,
    private val catalog: PluginCatalog,
) : ViewModel() {
    val themeMode = settings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, "SYSTEM")
    val language = settings.language.stateIn(viewModelScope, SharingStarted.Eagerly, "de")
    val displayMode = settings.displayMode.stateIn(viewModelScope, SharingStarted.Eagerly, "EINK")
    val downloadDir = settings.downloadDir.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val guidedPanelOverlay = settings.guidedPanelOverlay.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val deviceManagedRefresh = settings.deviceManagedRefresh.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val webtoonOverlapPercent =
        settings.webtoonOverlapPercent.stateIn(viewModelScope, SharingStarted.Eagerly, 25)
    // Roman-Typografie (global). Gespiegelt aus demselben SettingsRepository wie das In-Reader-Panel
    // — eine Quelle, beide Mount-Punkte schreiben hierher (DRY). Defaults = SettingsRepository-Defaults.
    val novelFontSizeEm = settings.novelFontSizeEm.stateIn(viewModelScope, SharingStarted.Eagerly, 1.0f)
    val novelLineHeight = settings.novelLineHeight.stateIn(viewModelScope, SharingStarted.Eagerly, 1.0f)
    val novelMarginPreset = settings.novelMarginPreset.stateIn(viewModelScope, SharingStarted.Eagerly, "NORMAL")
    val novelFontFamily = settings.novelFontFamily.stateIn(viewModelScope, SharingStarted.Eagerly, NovelFonts.DEFAULT)
    val novelTextAlign = settings.novelTextAlign.stateIn(viewModelScope, SharingStarted.Eagerly, "JUSTIFY")
    val novelHyphenationLang = settings.novelHyphenationLang.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val novelFontWeight = settings.novelFontWeight.stateIn(viewModelScope, SharingStarted.Eagerly, 400)
    /** Alle konfigurierten Server (mehrere gleichzeitig, gemischte Quellenarten). */
    val serverList = servers.configs.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val activeColorProfile = colorProfiles.observeActive()
        .stateIn(viewModelScope, SharingStarted.Eagerly, ColorProfile.OFF)

    val availableLanguages = catalog.languagePlugins
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setTheme(value: String) = viewModelScope.launch { settings.setThemeMode(value) }.let {}
    fun setLanguage(value: String) = viewModelScope.launch { settings.setLanguage(value) }.let {}
    fun setDisplayMode(value: String) = viewModelScope.launch { settings.setDisplayMode(value) }.let {}
    fun setDownloadDir(uri: String?) = viewModelScope.launch { settings.setDownloadDir(uri) }.let {}
    fun setGuidedPanelOverlay(value: Boolean) = viewModelScope.launch { settings.setGuidedPanelOverlay(value) }.let {}
    fun setDeviceManagedRefresh(value: Boolean) =
        viewModelScope.launch { settings.setDeviceManagedRefresh(value) }.let {}
    fun setWebtoonOverlap(percent: Int) =
        viewModelScope.launch { settings.setWebtoonOverlapPercent(percent) }.let {}
    fun setNovelFontSizeEm(value: Float) = viewModelScope.launch { settings.setNovelFontSizeEm(value) }.let {}
    fun setNovelLineHeight(value: Float) = viewModelScope.launch { settings.setNovelLineHeight(value) }.let {}
    fun setNovelFontWeight(value: Int) = viewModelScope.launch { settings.setNovelFontWeight(value) }.let {}
    fun setNovelMarginPreset(preset: String) = viewModelScope.launch { settings.setNovelMarginPreset(preset) }.let {}
    fun setNovelTextAlign(align: String) = viewModelScope.launch { settings.setNovelTextAlign(align) }.let {}
    fun setNovelHyphenationLang(lang: String) = viewModelScope.launch { settings.setNovelHyphenationLang(lang) }.let {}
    fun setNovelFontFamily(family: String) = viewModelScope.launch { settings.setNovelFontFamily(family) }.let {}
    /**
     * Legt eine Verbindung an ([id] == 0) oder aktualisiert sie in-place ([id] != 0 = Bearbeiten).
     * [save] ist ein Upsert über die Rowid — der Bearbeiten-Pfad reicht einfach die bestehende [id] durch.
     */
    fun saveServer(
        name: String,
        baseUrl: String,
        apiKey: String,
        username: String,
        password: String,
        kind: SourceKind = SourceKind.KOMGA,
        id: Long = 0,
    ) = viewModelScope.launch {
        servers.save(
            ServerConfig(
                // OPDS-URLs nicht Komga-normalisieren (Feed-Pfad bleibt wie eingegeben).
                name = name,
                baseUrl = if (kind == SourceKind.OPDS) baseUrl.trim() else KomgaUrl.normalize(baseUrl),
                apiKey = apiKey.trimToNull(),
                username = username.trimToNull(),
                password = password.trimToNull(),
                kind = kind,
                id = id,
            )
        )
        // Server verbunden (neu/aktualisiert) → dessen Sammlungen NUR pullen (nie lokale pushen).
        coordinator.onServerChanged()
    }.let {}

    private fun String.trimToNull(): String? = trim().ifBlank { null }

    /** Entfernt eine Server-Verbindung (per Rowid) und räumt die lokalen Sammlungs-Daten dieser Quelle auf. */
    fun removeServer(id: Long) = viewModelScope.launch {
        val cfg = servers.configs.first().firstOrNull { it.id == id }
        servers.remove(id)
        cfg?.let { registration.sourceIdOf(it) }?.let { collections.removeSource(it) }
    }.let {}
}
