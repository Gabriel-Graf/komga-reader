package com.komgareader.app.ui.plugins.repo

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgareader.data.plugin.repo.BrowsableEntry
import com.komgareader.data.plugin.repo.BrowserRow
import com.komgareader.data.plugin.repo.InstallState
import com.komgareader.data.plugin.repo.PluginRepoClient
import com.komgareader.data.plugin.repo.RepoSource
import com.komgareader.data.plugin.repo.RepoStore
import com.komgareader.data.plugin.repo.installState
import com.komgareader.data.plugin.repo.mergeRepoEntries
import com.komgareader.data.plugin.repo.parseRepoIndex
import com.komgareader.data.plugin.repo.pluginKindOf
import com.komgareader.data.plugin.repo.resolveApkUrl
import com.komgareader.plugin.PluginAbi
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class RepoBrowserViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repoStore: RepoStore,
    private val client: PluginRepoClient,
    private val installer: PluginInstaller,
) : ViewModel() {

    val repos: StateFlow<List<RepoSource>> =
        repoStore.observeActive().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _rows = MutableStateFlow<List<BrowserRow>>(emptyList())
    val rows: StateFlow<List<BrowserRow>> = _rows.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    fun setQuery(q: String) { _query.value = q }

    /** Lädt alle aktiven Repo-Indizes, merged + berechnet den Install-Zustand. */
    fun refresh() = viewModelScope.launch {
        _loading.value = true
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
        _rows.value = withContext(Dispatchers.IO) { merged.map { it.toRow() } }
        _loading.value = false
    }.let {}

    private fun BrowsableEntry.toRow(): BrowserRow {
        val installed = runCatching {
            PackageInfoCompat.getLongVersionCode(
                context.packageManager.getPackageInfo(entry.packageName, 0),
            )
        }.getOrNull()
        val compatible = entry.abiVersion in PluginAbi.MIN_SUPPORTED..PluginAbi.VERSION
        return BrowserRow(this, installState(entry.versionCode, installed), compatible)
    }

    /** Lädt das APK, verifiziert den Fingerprint, startet die Installation (OS-Dialog). */
    fun install(row: BrowserRow) = viewModelScope.launch {
        _error.value = null
        val url = resolveApkUrl(row.item.repoUrl, row.item.entry.apkUrl)
        val dir = File(context.cacheDir, "plugin-repo").apply { mkdirs() }
        val dest = File(dir, "${row.item.entry.packageName}-${row.item.entry.versionCode}.apk")
        val ok = client.download(url, dest)
        if (!ok) { _error.value = "download"; return@launch }
        when (installer.verifyAndInstall(dest, row.item.entry.fingerprint)) {
            is InstallResult.FingerprintMismatch -> _error.value = "fingerprint"
            is InstallResult.Failed -> _error.value = "install"
            is InstallResult.SessionStarted -> Unit // OS-Dialog; Re-Scan beim onResume
        }
    }.let {}

    fun addRepo(url: String) = viewModelScope.launch { repoStore.addUserRepo(url); refresh() }.let {}
    fun removeRepo(id: Long) = viewModelScope.launch { repoStore.removeUserRepo(id); refresh() }.let {}
    fun setOfficialEnabled(enabled: Boolean) = viewModelScope.launch { repoStore.setOfficialEnabled(enabled); refresh() }.let {}
    fun dismissError() { _error.value = null }
}
