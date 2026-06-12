package com.komgareader.app.data

import android.content.Context
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import com.komgareader.app.ui.plugins.planPluginPrune
import com.komgareader.app.ui.plugins.repo.InstallResult
import com.komgareader.app.ui.plugins.repo.PluginInstaller
import com.komgareader.data.plugin.repo.BrowsableEntry
import com.komgareader.data.plugin.repo.BrowserRow
import com.komgareader.data.plugin.repo.InstalledEntry
import com.komgareader.data.plugin.repo.PluginKind
import com.komgareader.data.plugin.repo.PluginRepoClient
import com.komgareader.data.plugin.repo.RepoSource
import com.komgareader.data.plugin.repo.RepoStore
import com.komgareader.data.plugin.repo.installState
import com.komgareader.data.plugin.repo.mergeRepoEntries
import com.komgareader.data.plugin.repo.parseRepoIndex
import com.komgareader.data.plugin.repo.pluginKindOf
import com.komgareader.data.plugin.repo.resolveApkUrl
import com.komgareader.data.plugin.LanguageSpec
import com.komgareader.data.plugin.parseLanguageSpec
import com.komgareader.data.plugin.parseReaderPresetSpecs
import com.komgareader.domain.model.ReaderPreset
import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.repository.CollectionRepository
import com.komgareader.plugin.PluginCategory
import com.komgareader.domain.repository.ColorProfileRepository
import com.komgareader.domain.repository.ServerRepository
import com.komgareader.plugin.PluginAbi
import com.komgareader.plugin.host.DiscoveredDataPlugin
import com.komgareader.plugin.host.DiscoveredPlugin
import com.komgareader.plugin.host.DiscoveredPresetPlugin
import com.komgareader.plugin.host.PluginHost
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Pure Abbildung installierter Plugins auf die agnostische [InstalledEntry]-Sicht. */
fun installedEntriesOf(
    sources: List<DiscoveredPlugin>,
    presets: List<DiscoveredPresetPlugin>,
    languages: List<DiscoveredDataPlugin> = emptyList(),
    readerPresets: List<DiscoveredDataPlugin> = emptyList(),
): List<InstalledEntry> =
    sources.map { InstalledEntry(it.packageName, it.metadata.displayName, PluginKind.SOURCE) } +
        presets.map { InstalledEntry(it.packageName, it.displayName, PluginKind.PRESET) } +
        languages.map { InstalledEntry(it.packageName, it.displayName, PluginKind.LANGUAGE) } +
        readerPresets.map { InstalledEntry(it.packageName, it.displayName, PluginKind.READER_PRESET) }

/**
 * Einziger Halter des Plugin-Discovery-Zustands (installierte APK-Quellen/-Presets + entdeckte
 * Repo-Einträge). Von [SyncCoordinator] (App-Start/Reload) und dem dünnen `PluginsViewModel`
 * (Reader) geteilt. Lokaler Scan = kein Netz; Repo-Fetch = Netz (nur App-Start 1× + manueller Reload).
 */
@Singleton
class PluginCatalog @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pluginHost: PluginHost,
    private val servers: ServerRepository,
    private val registration: SourceRegistration,
    private val collections: CollectionRepository,
    private val colorProfiles: ColorProfileRepository,
    private val repoStore: RepoStore,
    private val client: PluginRepoClient,
    private val installer: PluginInstaller,
) {
    private val _sources = MutableStateFlow<List<DiscoveredPlugin>>(emptyList())
    val sources: StateFlow<List<DiscoveredPlugin>> = _sources.asStateFlow()

    private val _presetPlugins = MutableStateFlow<List<DiscoveredPresetPlugin>>(emptyList())
    val presetPlugins: StateFlow<List<DiscoveredPresetPlugin>> = _presetPlugins.asStateFlow()

    private val _languagePlugins = MutableStateFlow<List<LanguageSpec>>(emptyList())
    val languagePlugins: StateFlow<List<LanguageSpec>> = _languagePlugins.asStateFlow()

    private val _readerPresetPlugins = MutableStateFlow<List<ReaderPreset>>(emptyList())
    val readerPresetPlugins: StateFlow<List<ReaderPreset>> = _readerPresetPlugins.asStateFlow()

    /** Rohe [DiscoveredDataPlugin]-Sicht der Sprach-Plugins (für den Hub: packageName + displayName). */
    private val _languageDataPlugins = MutableStateFlow<List<DiscoveredDataPlugin>>(emptyList())
    val languageDataPlugins: StateFlow<List<DiscoveredDataPlugin>> = _languageDataPlugins.asStateFlow()

    /** Rohe [DiscoveredDataPlugin]-Sicht der Reader-Preset-Plugins (für den Hub: packageName + displayName). */
    private val _readerPresetDataPlugins = MutableStateFlow<List<DiscoveredDataPlugin>>(emptyList())
    val readerPresetDataPlugins: StateFlow<List<DiscoveredDataPlugin>> = _readerPresetDataPlugins.asStateFlow()

    private val _discovered = MutableStateFlow<List<BrowserRow>>(emptyList())
    val discovered: StateFlow<List<BrowserRow>> = _discovered.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val fetchMutex = Mutex()

    /** Aktive Repo-Quellen (für das Repo-Verwaltungs-Modal). */
    val reposFlow: Flow<List<RepoSource>> = repoStore.observeActive()

    fun dismissError() { _error.value = null }

    /** Installierte als quellen-agnostische [InstalledEntry] (für die pure `visibleRows`-Filterung). */
    fun installedEntries(): List<InstalledEntry> =
        installedEntriesOf(_sources.value, _presetPlugins.value, _languageDataPlugins.value, _readerPresetDataPlugins.value)

    /**
     * Lokaler APK-Scan (kein Netz): Quellen + Presets neu entdecken, danach Verwaistes prunen
     * (deinstallierte APKs → Profile/Plugin-Quellen löschen). Verschoben aus PluginsViewModel.refresh().
     */
    suspend fun scanLocal() {
        val srcs = withContext(Dispatchers.IO) {
            runCatching { pluginHost.discoverPlugins() }
                .onFailure { Log.w("PluginCatalog", "discoverPlugins failed", it) }
                .getOrDefault(emptyList())
        }
        val presets = withContext(Dispatchers.IO) {
            runCatching { pluginHost.discoverColorPresetPlugins() }
                .onFailure { Log.w("PluginCatalog", "discoverColorPresetPlugins failed", it) }
                .getOrDefault(emptyList())
        }
        val rawLanguages = withContext(Dispatchers.IO) {
            runCatching { pluginHost.discoverDataPlugins(PluginCategory.LANGUAGE) }
                .onFailure { Log.w("PluginCatalog", "discoverLanguagePlugins failed", it) }
                .getOrDefault(emptyList())
        }
        val rawReaderPresets = withContext(Dispatchers.IO) {
            runCatching { pluginHost.discoverDataPlugins(PluginCategory.READER_PRESET) }
                .onFailure { Log.w("PluginCatalog", "discoverReaderPresetPlugins failed", it) }
                .getOrDefault(emptyList())
        }
        _sources.value = srcs
        _presetPlugins.value = presets
        _languageDataPlugins.value = rawLanguages
        _readerPresetDataPlugins.value = rawReaderPresets
        _languagePlugins.value = rawLanguages.mapNotNull { parseLanguageSpec(it.assetJson, it.abiVersion) }
        _readerPresetPlugins.value = rawReaderPresets.flatMap { parseReaderPresetSpecs(it.assetJson, it.abiVersion).orEmpty() }

        val installed = (srcs.map { it.packageName } + presets.map { it.packageName }).toSet()
        val taggedPkgs = colorProfiles.observeAll().first().mapNotNull { it.pluginPackage }
        val pluginConfigs = servers.configs.first().filter { it.kind == SourceKind.PLUGIN }
        val plan = planPluginPrune(installed, taggedPkgs, pluginConfigs)
        plan.presetPackagesToDrop.forEach { colorProfiles.deleteByPluginPackage(it) }
        plan.sourceConfigIdsToRemove.forEach { id ->
            val cfg = pluginConfigs.firstOrNull { it.id == id }
            servers.remove(id)
            cfg?.let { registration.sourceIdOf(it) }?.let { collections.removeSource(it) }
        }

        // Install-States der entdeckten Repo-Einträge lokal nachziehen (kein Netz): nach
        // Install/Uninstall (onResume-Rescan) muss ein deinstalliertes Plugin in der Entdeckungs-
        // Liste sofort wieder als installierbar erscheinen statt „Importiert" — der Netz-Index
        // bleibt gleich, nur der lokale Paket-Stand ändert sich, also bloß neu ableiten.
        if (_discovered.value.isNotEmpty()) {
            _discovered.value = withContext(Dispatchers.IO) { _discovered.value.map { it.item.toRow() } }
        }
    }

    /** Netz-Repo-Fetch: aktive Repos laden, mergen, Install-Zustand berechnen. */
    suspend fun fetchRepos() {
        fetchMutex.withLock {
            _loading.value = true
            try {
                val sources = repoStore.observeActive().first()
                val collected = mutableListOf<BrowsableEntry>()
                for (src in sources) {
                    val body = client.fetchIndex(src.url) ?: continue
                    val index = parseRepoIndex(body) ?: continue
                    if (!src.official) repoStore.rememberName(src.url, index.name)
                    val repoLabel = index.name.ifBlank { src.name }
                    index.plugins.forEach {
                        collected += BrowsableEntry(it, repoLabel, src.url, pluginKindOf(it.type))
                    }
                }
                val merged = mergeRepoEntries(collected)
                _discovered.value = withContext(Dispatchers.IO) { merged.map { it.toRow() } }
            } finally {
                _loading.value = false
            }
        }
    }

    private fun BrowsableEntry.toRow(): BrowserRow {
        val installedVersion = runCatching {
            PackageInfoCompat.getLongVersionCode(
                context.packageManager.getPackageInfo(entry.packageName, 0),
            )
        }.getOrNull()
        val compatible = entry.abiVersion in PluginAbi.MIN_SUPPORTED..PluginAbi.VERSION
        return BrowserRow(this, installState(entry.versionCode, installedVersion), compatible)
    }

    /** Lädt das APK, verifiziert den Fingerprint, startet die Installation (OS-Dialog). */
    suspend fun install(row: BrowserRow) {
        _error.value = null
        val url = resolveApkUrl(row.item.repoUrl, row.item.entry.apkUrl)
        val dir = File(context.cacheDir, "plugin-repo").apply { mkdirs() }
        val dest = File(dir, "${row.item.entry.packageName}-${row.item.entry.versionCode}.apk")
        val ok = client.download(url, dest)
        if (!ok) { _error.value = "download"; return }
        when (installer.verifyAndInstall(dest, row.item.entry.fingerprint)) {
            is InstallResult.FingerprintMismatch -> _error.value = "fingerprint"
            is InstallResult.Failed -> _error.value = "install"
            is InstallResult.SessionStarted -> Unit // OS-Dialog; Re-Scan beim onResume
        }
    }

    suspend fun addRepo(url: String) { repoStore.addUserRepo(url); fetchRepos() }
    suspend fun removeRepo(id: Long) { repoStore.removeUserRepo(id); fetchRepos() }
    suspend fun setOfficialEnabled(enabled: Boolean) { repoStore.setOfficialEnabled(enabled); fetchRepos() }
}
