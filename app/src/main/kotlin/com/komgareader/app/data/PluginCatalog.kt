package com.komgareader.app.data

import android.content.Context
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
import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.repository.CollectionRepository
import com.komgareader.domain.repository.ColorProfileRepository
import com.komgareader.domain.repository.ServerRepository
import com.komgareader.plugin.PluginAbi
import com.komgareader.plugin.host.DiscoveredPlugin
import com.komgareader.plugin.host.DiscoveredPresetPlugin
import com.komgareader.plugin.host.PluginHost
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Pure Abbildung installierter Plugins auf die agnostische [InstalledEntry]-Sicht. */
fun installedEntriesOf(
    sources: List<DiscoveredPlugin>,
    presets: List<DiscoveredPresetPlugin>,
): List<InstalledEntry> =
    sources.map { InstalledEntry(it.packageName, it.metadata.displayName, PluginKind.SOURCE) } +
        presets.map { InstalledEntry(it.packageName, it.displayName, PluginKind.PRESET) }

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

    private val _discovered = MutableStateFlow<List<BrowserRow>>(emptyList())
    val discovered: StateFlow<List<BrowserRow>> = _discovered.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Aktive Repo-Quellen (für das Repo-Verwaltungs-Modal). */
    val reposFlow: Flow<List<RepoSource>> = repoStore.observeActive()

    fun dismissError() { _error.value = null }

    /** Installierte als quellen-agnostische [InstalledEntry] (für die pure `visibleRows`-Filterung). */
    fun installedEntries(): List<InstalledEntry> =
        installedEntriesOf(_sources.value, _presetPlugins.value)

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
        _sources.value = srcs
        _presetPlugins.value = presets

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
    }

    /** Netz-Repo-Fetch: aktive Repos laden, mergen, Install-Zustand berechnen. */
    suspend fun fetchRepos() {
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
