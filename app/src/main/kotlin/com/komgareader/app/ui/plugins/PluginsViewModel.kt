package com.komgareader.app.ui.plugins

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgareader.app.data.CollectionSyncManager
import com.komgareader.app.data.SourceRegistration
import com.komgareader.data.plugin.ColorPresetImporter
import com.komgareader.domain.model.ColorProfile
import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.repository.CollectionRepository
import com.komgareader.domain.repository.ColorProfileRepository
import com.komgareader.domain.repository.ServerConfig
import com.komgareader.domain.repository.ServerRepository
import com.komgareader.plugin.ColorPresetSpec
import com.komgareader.plugin.host.DiscoveredPlugin
import com.komgareader.plugin.host.DiscoveredPresetPlugin
import com.komgareader.plugin.host.PluginHost
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PluginsViewModel @Inject constructor(
    private val pluginHost: PluginHost,
    private val servers: ServerRepository,
    private val registration: SourceRegistration,
    private val collections: CollectionRepository,
    private val colorProfiles: ColorProfileRepository,
    private val sync: CollectionSyncManager,
) : ViewModel() {

    private val _sources = MutableStateFlow<List<DiscoveredPlugin>>(emptyList())
    val sources: StateFlow<List<DiscoveredPlugin>> = _sources.asStateFlow()

    private val _presetPlugins = MutableStateFlow<List<DiscoveredPresetPlugin>>(emptyList())
    val presetPlugins: StateFlow<List<DiscoveredPresetPlugin>> = _presetPlugins.asStateFlow()

    /** Alle Profile (für „bereits importiert?"-Abgleich pro Preset-Plugin nach (pkg, name)). */
    val profiles = colorProfiles.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * Re-Scan beim Tab-Öffnen/`onResume`: Quellen + Presets neu entdecken, danach aufräumen,
     * was zu deinstallierten Plugin-APKs gehört (Uninstall hat kein verlässliches Callback).
     */
    fun refresh() = viewModelScope.launch {
        val srcs = runCatching { pluginHost.discoverPlugins() }.getOrDefault(emptyList())
        val presets = runCatching { pluginHost.discoverColorPresetPlugins() }.getOrDefault(emptyList())
        _sources.value = srcs
        _presetPlugins.value = presets

        val installed = (srcs.map { it.packageName } + presets.map { it.packageName }).toSet()
        // Suspending lesen (nicht profiles.value): beim allerersten refresh() kann der Eagerly-StateFlow
        // noch auf der initialen emptyList() stehen → der Prune würde getaggte Profile übersehen.
        val taggedPkgs = colorProfiles.observeAll().first().mapNotNull { it.pluginPackage }
        val pluginConfigs = servers.configs.first().filter { it.kind == SourceKind.PLUGIN }
        val plan = planPluginPrune(installed, taggedPkgs, pluginConfigs)
        plan.presetPackagesToDrop.forEach { colorProfiles.deleteByPluginPackage(it) }
        plan.sourceConfigIdsToRemove.forEach { id ->
            val cfg = pluginConfigs.firstOrNull { it.id == id }
            servers.remove(id)
            cfg?.let { registration.sourceIdOf(it) }?.let { collections.removeSource(it) }
        }
    }.let {}

    /**
     * Persistiert eine bestätigte Plugin-Quelle als [ServerConfig] (kind = PLUGIN). Extras tragen
     * neben den Nutzerwerten die reservierten Wiring-Keys `__pkg`/`__entry`/`__sig` (TOFU-Pin).
     * Verschoben aus SettingsViewModel — der Add-Flow lebt jetzt im Plugin-Tab.
     */
    fun addPluginSource(plugin: DiscoveredPlugin, values: Map<String, String>) = viewModelScope.launch {
        servers.save(
            ServerConfig(
                name = plugin.metadata.displayName,
                baseUrl = values["url"]?.trim() ?: "",
                kind = SourceKind.PLUGIN,
                extras = values + mapOf(
                    "__pkg" to plugin.packageName,
                    "__entry" to plugin.entryClass,
                    "__sig" to plugin.signatureSha256,
                ),
            )
        )
        runCatching { sync.pullOnlySync() }
    }.let {}

    /** True, wenn aus [pkg] bereits ein Profil mit [name] importiert wurde (Abgleich nach Tag+Name). */
    fun isImported(pkg: String, name: String): Boolean =
        profiles.value.any { it.pluginPackage == pkg && it.name == name }

    /**
     * Importiert ein Preset eines Plugins als getaggtes Profil (builtIn bleibt false, aber
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
