package com.komgareader.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgareader.domain.model.ColorProfile
import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.repository.ColorProfileRepository
import com.komgareader.domain.repository.KomgaUrl
import com.komgareader.domain.repository.ServerConfig
import com.komgareader.domain.repository.ServerRepository
import com.komgareader.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val servers: ServerRepository,
    private val colorProfiles: ColorProfileRepository,
) : ViewModel() {
    val themeMode = settings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, "SYSTEM")
    val language = settings.language.stateIn(viewModelScope, SharingStarted.Eagerly, "de")
    val displayMode = settings.displayMode.stateIn(viewModelScope, SharingStarted.Eagerly, "EINK")
    val downloadDir = settings.downloadDir.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val guidedPanelOverlay = settings.guidedPanelOverlay.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val webtoonOverlapPercent =
        settings.webtoonOverlapPercent.stateIn(viewModelScope, SharingStarted.Eagerly, 25)
    /** Alle konfigurierten Server (mehrere gleichzeitig, gemischte Quellenarten). */
    val serverList = servers.configs.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val activeColorProfile = colorProfiles.observeActive()
        .stateIn(viewModelScope, SharingStarted.Eagerly, ColorProfile.OFF)

    fun setTheme(value: String) = viewModelScope.launch { settings.setThemeMode(value) }.let {}
    fun setLanguage(value: String) = viewModelScope.launch { settings.setLanguage(value) }.let {}
    fun setDisplayMode(value: String) = viewModelScope.launch { settings.setDisplayMode(value) }.let {}
    fun setDownloadDir(uri: String?) = viewModelScope.launch { settings.setDownloadDir(uri) }.let {}
    fun setGuidedPanelOverlay(value: Boolean) = viewModelScope.launch { settings.setGuidedPanelOverlay(value) }.let {}
    fun setWebtoonOverlap(percent: Int) =
        viewModelScope.launch { settings.setWebtoonOverlapPercent(percent) }.let {}
    fun saveServer(
        name: String,
        baseUrl: String,
        apiKey: String,
        username: String,
        password: String,
        kind: SourceKind = SourceKind.KOMGA,
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
            )
        )
    }.let {}

    private fun String.trimToNull(): String? = trim().ifBlank { null }

    /** Entfernt genau eine Server-Verbindung (per Rowid) — die anderen bleiben. */
    fun removeServer(id: Long) = viewModelScope.launch { servers.remove(id) }.let {}
}
