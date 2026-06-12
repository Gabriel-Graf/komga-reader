package com.komgareader.app.ui.plugins

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgareader.app.data.PluginCatalog
import com.komgareader.app.data.installedEntriesOf
import com.komgareader.app.data.SyncCoordinator
import com.komgareader.app.data.pluginServerConfig
import com.komgareader.data.plugin.ColorPresetImporter
import com.komgareader.data.plugin.repo.BrowserRow
import com.komgareader.data.plugin.repo.PluginTypeFilter
import com.komgareader.data.plugin.repo.RepoSource
import com.komgareader.data.plugin.repo.VisibleRows
import com.komgareader.data.plugin.repo.visibleRows
import com.komgareader.domain.model.ColorProfile
import com.komgareader.domain.repository.ColorProfileRepository
import com.komgareader.domain.repository.ServerRepository
import com.komgareader.plugin.ColorPresetSpec
import com.komgareader.plugin.host.DiscoveredPlugin
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PluginsViewModel @Inject constructor(
    private val catalog: PluginCatalog,
    private val coordinator: SyncCoordinator,
    private val servers: ServerRepository,
    private val colorProfiles: ColorProfileRepository,
) : ViewModel() {

    val sources = catalog.sources
    val presetPlugins = catalog.presetPlugins
    val loading = catalog.loading
    val error = catalog.error

    val profiles: StateFlow<List<ColorProfile>> =
        colorProfiles.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val repos: StateFlow<List<RepoSource>> =
        catalog.reposFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    fun setQuery(q: String) { _query.value = q }

    private val _typeFilter = MutableStateFlow(PluginTypeFilter.ALL)
    val typeFilter: StateFlow<PluginTypeFilter> = _typeFilter.asStateFlow()
    fun setTypeFilter(f: PluginTypeFilter) { _typeFilter.value = f }

    /** Gefilterte, anzeigefertige Sicht: installiert oben, entdeckt unten, Divider-Flag. */
    val visible: StateFlow<VisibleRows> =
        combine(catalog.sources, catalog.presetPlugins, catalog.discovered, _query, _typeFilter) { srcs, presets, disc, q, f ->
            visibleRows(installedEntriesOf(srcs, presets), disc, q, f)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VisibleRows(emptyList(), emptyList(), false))

    /** Tab-`onResume`: lokaler Re-Scan über den Koordinator (kein Netz) — entdeckt Install/Uninstall,
     *  prunt Verwaistes, zieht Entdeckungs-Install-States nach. Sync-Entscheidung liegt im Koordinator. */
    fun rescanLocal() = viewModelScope.launch { coordinator.onPluginsTabResumed() }.let {}

    /** Reload-Button: Netz-Repo-Fetch + lokaler Scan über den Koordinator. */
    fun reload() = viewModelScope.launch { coordinator.onManualReload() }.let {}

    fun install(row: BrowserRow) = viewModelScope.launch { catalog.install(row) }.let {}
    fun dismissError() { catalog.dismissError() }

    fun addRepo(url: String) = viewModelScope.launch { catalog.addRepo(url) }.let {}
    fun removeRepo(id: Long) = viewModelScope.launch { catalog.removeRepo(id) }.let {}
    fun setOfficialEnabled(enabled: Boolean) = viewModelScope.launch { catalog.setOfficialEnabled(enabled) }.let {}

    /**
     * Persistiert eine bestätigte Plugin-Quelle als [ServerConfig] (kind = PLUGIN). Extras tragen
     * neben den Nutzerwerten die reservierten Wiring-Keys `__pkg`/`__entry`/`__sig` (TOFU-Pin).
     * Nach dem Speichern Sync anstoßen (neue Sammlungen pullen).
     */
    fun addPluginSource(plugin: DiscoveredPlugin, values: Map<String, String>) = viewModelScope.launch {
        servers.save(pluginServerConfig(plugin, values))
        coordinator.onServerChanged()
    }.let {}

    /**
     * Importiert ein Preset eines Plugins als getaggtes Profil (builtIn bleibt false,
     * `pluginPackage` markiert es als Plugin-eigen → in der UI nicht editierbar). Inkompatible/
     * fehlerhafte Specs werden still verworfen (ABI-/Wert-Validierung in ColorPresetImporter).
     */
    fun importPreset(pkg: String, spec: ColorPresetSpec) = viewModelScope.launch {
        val profile = ColorPresetImporter.toProfileOrNull(spec) ?: return@launch
        colorProfiles.upsert(profile.copy(pluginPackage = pkg))
    }.let {}

    /** Entfernt ein einzeln importiertes Plugin-Profil wieder (per id). */
    fun removeImportedProfile(profile: ColorProfile) = viewModelScope.launch {
        colorProfiles.delete(profile.id)
    }.let {}
}
