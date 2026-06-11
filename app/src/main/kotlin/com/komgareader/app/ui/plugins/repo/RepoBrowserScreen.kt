package com.komgareader.app.ui.plugins.repo

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.EinkInfoDialog
import com.komgareader.app.ui.components.EinkOutlinedButton
import com.komgareader.app.ui.components.EinkToggle
import com.komgareader.app.ui.components.LoadingIndicator
import com.komgareader.app.ui.components.SubPageScaffold
import com.komgareader.app.ui.icons.AppIcons
import com.komgareader.app.ui.theme.EinkTokens
import com.komgareader.data.plugin.repo.InstallState
import com.komgareader.data.plugin.repo.PluginKind
import com.komgareader.data.plugin.repo.RepoSource

/**
 * Plugin-Repo-Browser (gepushte Vollseite): listet entdeckbare Plugins aus den aktiven Repos,
 * durchsuchbar, mit Repo-Verwaltung (offizielles Repo an/aus, Nutzer-Repos hinzufügen/entfernen)
 * und Install-/Update-Aktion pro Eintrag. E-Ink: flach, 1.5px-Border, Lucide via AppIcons,
 * keine Animation, [EinkInfoDialog] für Dialoge, alle Texte über [LocalStrings].
 */
@Composable
fun RepoBrowserScreen(onBack: () -> Unit, viewModel: RepoBrowserViewModel = hiltViewModel()) {
    val s = LocalStrings.current
    val rows by viewModel.rows.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val query by viewModel.query.collectAsState()
    val repos by viewModel.repos.collectAsState()
    val error by viewModel.error.collectAsState()
    var showRepoMgmt by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    // scrollable = false: eigenes Listen-Layout (LazyColumn) statt des Scaffold-eigenen verticalScroll.
    SubPageScaffold(title = s.repoBrowserTitle, onBack = onBack, scrollable = false) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::setQuery,
                    label = { Text(s.repoBrowserSearch) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { showRepoMgmt = true }) {
                    Icon(AppIcons.Settings, contentDescription = s.repoBrowserManage, modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = { viewModel.refresh() }) {
                    Icon(AppIcons.Refresh, contentDescription = s.repoBrowserRefresh, modifier = Modifier.size(22.dp))
                }
            }
            if (loading) LoadingIndicator()
            val filtered = rows.filter {
                query.isBlank() ||
                    it.item.entry.name.contains(query, ignoreCase = true) ||
                    it.item.entry.description.contains(query, ignoreCase = true)
            }
            if (!loading && filtered.isEmpty()) {
                Text(s.repoBrowserEmpty, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.item.entry.packageName }) { row ->
                    RepoRow(row = row, onInstall = { viewModel.install(row) })
                }
            }
        }
    }

    if (showRepoMgmt) {
        RepoManagementModal(
            repos = repos,
            onDismiss = { showRepoMgmt = false },
            onAdd = viewModel::addRepo,
            onRemove = viewModel::removeRepo,
            onToggleOfficial = viewModel::setOfficialEnabled,
        )
    }

    error?.let { code ->
        val msg = when (code) {
            "fingerprint" -> s.repoBrowserErrorFingerprint
            "download" -> s.repoBrowserErrorDownload
            else -> s.repoBrowserErrorInstall
        }
        EinkInfoDialog(title = s.repoBrowserErrorTitle, onDismiss = { viewModel.dismissError() }, closeLabel = s.close) {
            Text(msg, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * Eine Browser-Zeile: Name + Typ/Version/ABI/Repo-Untertitel links, Install-/Update-/Status rechts.
 * Spiegelt den flachen 1.5px-Border-Stil von `PluginRow` (P1). Keine Animation.
 */
@Composable
private fun RepoRow(row: BrowserRow, onInstall: () -> Unit) {
    val s = LocalStrings.current
    val typeLabel = if (row.item.kind == PluginKind.PRESET) s.pluginTabPresetLabel else s.pluginTabSourceLabel
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(EinkTokens.hairline, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(row.item.entry.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                "$typeLabel · v${row.item.entry.versionName} · ${s.pluginAbiLabel} ${row.item.entry.abiVersion} · ${row.item.repoName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            !row.compatible -> Text(
                s.repoBrowserIncompatible,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            row.state == InstallState.INSTALLED -> Text(
                s.pluginPresetImported,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            row.state == InstallState.UPDATE_AVAILABLE -> EinkOutlinedButton(onClick = onInstall) {
                Icon(AppIcons.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(s.repoBrowserUpdate, modifier = Modifier.padding(start = 6.dp))
            }
            else -> IconButton(onClick = onInstall) {
                Icon(AppIcons.Download, contentDescription = s.repoBrowserInstall, modifier = Modifier.size(22.dp))
            }
        }
    }
}

/**
 * Repo-Verwaltung als nur-schließbares [EinkInfoDialog]: offizielles Repo an/aus, Nutzer-Repos
 * auflisten + einzeln entfernen, neue Repo-URL hinzufügen. Flach, keine Animation.
 */
@Composable
private fun RepoManagementModal(
    repos: List<RepoSource>,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (Long) -> Unit,
    onToggleOfficial: (Boolean) -> Unit,
) {
    val s = LocalStrings.current
    var newUrl by remember { mutableStateOf("") }
    EinkInfoDialog(title = s.repoBrowserManage, onDismiss = onDismiss, closeLabel = s.close) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(s.repoBrowserOfficial, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            EinkToggle(
                checked = repos.any { it.official },
                onCheckedChange = onToggleOfficial,
            )
        }
        repos.filter { !it.official }.forEach { repo ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(repo.name.ifBlank { repo.url }, style = MaterialTheme.typography.bodyLarge)
                    if (repo.name.isNotBlank()) {
                        Text(
                            repo.url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                repo.id?.let { id ->
                    IconButton(onClick = { onRemove(id) }) {
                        Icon(AppIcons.Delete, contentDescription = s.repoBrowserRemoveRepo, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newUrl,
                onValueChange = { newUrl = it },
                label = { Text(s.repoBrowserRepoUrl) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            EinkOutlinedButton(
                onClick = {
                    if (newUrl.isNotBlank()) {
                        onAdd(newUrl.trim())
                        newUrl = ""
                    }
                },
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text(s.repoBrowserAddRepo)
            }
        }
    }
}
