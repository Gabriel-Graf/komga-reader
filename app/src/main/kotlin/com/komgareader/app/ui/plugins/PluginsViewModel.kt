package com.komgareader.app.ui.plugins

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgareader.app.data.ApkSessionInstaller
import com.komgareader.app.data.PluginCatalog
import com.komgareader.app.data.installedEntriesOf
import com.komgareader.app.data.SyncCoordinator
import com.komgareader.app.data.pluginServerConfig
import com.komgareader.app.ui.common.holdSpinning
import com.komgareader.data.plugin.ColorPresetImporter
import com.komgareader.data.plugin.parseConfigSchema
import com.komgareader.data.plugin.repo.BrowsableEntry
import com.komgareader.data.plugin.repo.BrowserRow
import com.komgareader.data.plugin.repo.InstallState
import com.komgareader.data.plugin.repo.InstalledEntry
import com.komgareader.data.plugin.repo.PluginKind
import com.komgareader.data.plugin.repo.PluginTypeFilter
import com.komgareader.data.plugin.repo.RepoPluginEntry
import com.komgareader.data.plugin.repo.RepoSource
import com.komgareader.data.plugin.repo.VisibleRows
import com.komgareader.data.plugin.repo.resolveRepoUrl
import com.komgareader.data.plugin.repo.visibleRows
import com.komgareader.domain.model.ColorProfile
import com.komgareader.domain.repository.ColorProfileRepository
import com.komgareader.domain.repository.ServerRepository
import com.komgareader.domain.repository.SettingsRepository
import com.komgareader.plugin.ColorPresetSpec
import com.komgareader.plugin.ConfigSchema
import com.komgareader.plugin.host.PluginHost
import com.komgareader.plugin.host.DataPluginInfo
import com.komgareader.plugin.host.DiscoveredDataPlugin
import com.komgareader.plugin.host.DiscoveredPlugin
import com.komgareader.plugin.host.DiscoveredPresetPlugin
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** State of the README area in the plugin info modal. */
sealed interface ReadmeState {
    data object Loading : ReadmeState
    data class Loaded(val markdown: String) : ReadmeState
    data object Empty : ReadmeState   // no README / error -> description fallback in the UI
}

@HiltViewModel
class PluginsViewModel @Inject constructor(
    private val catalog: PluginCatalog,
    private val coordinator: SyncCoordinator,
    private val servers: ServerRepository,
    private val colorProfiles: ColorProfileRepository,
    private val apkSession: ApkSessionInstaller,
    private val pluginHost: PluginHost,
    private val settings: SettingsRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val sources = catalog.sources
    val presetPlugins = catalog.presetPlugins
    val languageDataPlugins = catalog.languageDataPlugins
    val readerPresetDataPlugins = catalog.readerPresetDataPlugins
    val uiPackDataPlugins = catalog.uiPackDataPlugins
    val panelModelDataPlugins: StateFlow<List<DataPluginInfo>> = catalog.panelModelDataPlugins
    val fontDataPlugins = catalog.fontDataPlugins
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

    private val _infoFor = MutableStateFlow<BrowserRow?>(null)
    val infoFor: StateFlow<BrowserRow?> = _infoFor.asStateFlow()

    private val _readmeState = MutableStateFlow<ReadmeState>(ReadmeState.Empty)
    val readmeState: StateFlow<ReadmeState> = _readmeState.asStateFlow()

    /** In-flight README load; cancelled when a new info modal opens or the modal closes,
     *  so a slow earlier fetch can never overwrite the state of a later/closed modal. */
    private var readmeJob: Job? = null

    /** Opens the info modal for [row] and loads its README if present. */
    fun openInfo(row: BrowserRow) {
        _infoFor.value = row
        readmeJob?.cancel()
        val raw = row.item.entry.readmeUrl
        if (raw.isBlank()) { _readmeState.value = ReadmeState.Empty; return }
        _readmeState.value = ReadmeState.Loading
        readmeJob = viewModelScope.launch {
            val url = resolveRepoUrl(row.item.repoUrl, raw)
            val md = catalog.fetchReadme(url)
            _readmeState.value = if (md.isNullOrBlank()) ReadmeState.Empty else ReadmeState.Loaded(md)
        }
    }

    /** Opens the info modal for an INSTALLED plugin: reuses its repo entry (README/preview/license)
     *  if the package is in a configured repo, else a minimal synthesized entry (name only). */
    fun openInfoForInstalled(entry: InstalledEntry) {
        val fromRepo = catalog.discovered.value.firstOrNull { it.item.entry.packageName == entry.packageName }
        openInfo(fromRepo ?: syntheticRow(entry))
    }

    /** Minimal [BrowserRow] for an installed plugin that is not present in any configured repo:
     *  carries only name/kind so the modal shows a header + the "no description" fallback. */
    private fun syntheticRow(entry: InstalledEntry): BrowserRow = BrowserRow(
        item = BrowsableEntry(
            entry = RepoPluginEntry(packageName = entry.packageName, name = entry.displayName),
            repoName = "",
            repoUrl = "",
            kind = entry.kind,
        ),
        state = InstallState.INSTALLED,
        compatible = true,
    )

    /** Closes the info modal. */
    fun closeInfo() {
        readmeJob?.cancel()
        readmeJob = null
        _infoFor.value = null
        _readmeState.value = ReadmeState.Empty
    }

    /** Gefilterte, anzeigefertige Sicht: installiert oben, entdeckt unten, Divider-Flag. */
    @Suppress("UNCHECKED_CAST")
    val visible: StateFlow<VisibleRows> =
        combine(
            catalog.sources,
            catalog.presetPlugins,
            catalog.languageDataPlugins,
            catalog.readerPresetDataPlugins,
            catalog.uiPackDataPlugins,
            catalog.panelModelDataPlugins,
            catalog.fontDataPlugins,
            catalog.discovered,
            _query,
            _typeFilter,
        ) { arr ->
            val srcs = arr[0] as List<DiscoveredPlugin>
            val presets = arr[1] as List<DiscoveredPresetPlugin>
            val langs = arr[2] as List<DiscoveredDataPlugin>
            val rPresets = arr[3] as List<DiscoveredDataPlugin>
            val uiPacks = arr[4] as List<DiscoveredDataPlugin>
            val panelModels = arr[5] as List<DataPluginInfo>
            val fonts = arr[6] as List<DiscoveredDataPlugin>
            val disc = arr[7] as List<BrowserRow>
            val q = arr[8] as String
            val f = arr[9] as PluginTypeFilter
            val installedEntries = installedEntriesOf(srcs, presets, langs, rPresets, uiPacks, fonts) +
                panelModels.map { InstalledEntry(it.packageName, it.displayName, PluginKind.PANEL_MODEL) }
            visibleRows(installedEntries, disc, q, f)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VisibleRows(emptyList(), emptyList(), false))

    /** Tab-`onResume`: lokaler Re-Scan über den Koordinator (kein Netz) — entdeckt Install/Uninstall,
     *  prunt Verwaistes, zieht Entdeckungs-Install-States nach. Sync-Entscheidung liegt im Koordinator. */
    fun rescanLocal() = viewModelScope.launch { coordinator.onPluginsTabResumed() }.let {}

    /** True while [reload] runs — drives the spinning reload button. */
    private val _reloading = MutableStateFlow(false)
    val reloading: StateFlow<Boolean> = _reloading.asStateFlow()

    /** Reload-Button: Netz-Repo-Fetch + lokaler Scan über den Koordinator. */
    fun reload() = viewModelScope.launch {
        _reloading.holdSpinning { coordinator.onManualReload() }
    }.let {}

    fun install(row: BrowserRow) = viewModelScope.launch { catalog.install(row) }.let {}
    fun dismissError() { catalog.dismissError() }

    /**
     * Sideloads a user-picked local APK (any plugin kind) from a SAF document [uri]. Copies the
     * content stream to a cache file and commits it through the shared [ApkSessionInstaller] (the OS
     * install dialog confirms). No fingerprint check: the file is user-chosen, not from a repo index —
     * the repo install path keeps its fingerprint gate. The tab re-scan on resume discovers it; the
     * font license gate (`scanLocal`) and source-plugin TOFU still apply at discovery/registration.
     */
    fun installLocalApk(uri: Uri) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            runCatching {
                val tmp = File.createTempFile("sideload", ".apk", context.cacheDir)
                val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                    tmp.outputStream().use { input.copyTo(it) }; true
                } ?: false
                if (copied) apkSession.commit(tmp)
                tmp.delete()
            }
        }
    }.let {}

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

    /** Parsed config schema for a data-only plugin, or null if none declared. */
    fun configSchemaFor(pkg: String): ConfigSchema? =
        pluginHost.dataPluginConfigJson(pkg)?.let { parseConfigSchema(it) }

    /** Returns the currently saved values for all schema fields (falls back to field defaults). */
    suspend fun savedConfig(pkg: String, schema: ConfigSchema): Map<String, String> =
        schema.fields.associate { f -> f.key to (settings.pluginConfig(pkg, f.key).first() ?: f.default) }

    /** Persists a map of field values for the given plugin package. */
    suspend fun saveConfig(pkg: String, values: Map<String, String>) {
        values.forEach { (k, v) -> settings.setPluginConfig(pkg, k, v) }
    }
}
