package com.komgareader.app.ui.plugins

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.EinkInfoDialog
import com.komgareader.app.ui.components.EinkOutlinedButton
import com.komgareader.app.ui.components.EinkToggle
import com.komgareader.app.ui.components.LoadingIndicator
import com.komgareader.app.ui.icons.AppIcons
import com.komgareader.app.ui.theme.EinkTokens
import com.komgareader.data.plugin.repo.BrowserRow
import com.komgareader.data.plugin.repo.InstallState
import com.komgareader.data.plugin.repo.PluginKind
import com.komgareader.data.plugin.repo.RepoSource
import com.komgareader.plugin.ColorPresetSpec
import com.komgareader.plugin.host.DiscoveredPlugin
import com.komgareader.plugin.host.DiscoveredPresetPlugin

/**
 * Vereinte Plugins-Seite (`HomeScreen` TAB_PLUGINS, content-only — TopBar liefert HomeScreen).
 * Installierte Plugin-APKs (Quellen + data-only Color-Presets) oben, optionaler Divider,
 * entdeckte Repo-Einträge unten. Such-/Typ-Filter-State kommt vom VM. Repo-Mgmt-Modal wird
 * von außen (HomeScreen) über [showRepoManagement]/[onRepoManagementDismiss] gesteuert.
 * E-Ink: flach, 1.5px-Border, Lucide via AppIcons, keine Animation.
 */
@Composable
fun PluginsScreen(
    modifier: Modifier = Modifier,
    showRepoManagement: Boolean = false,
    onRepoManagementDismiss: () -> Unit = {},
    viewModel: PluginsViewModel = hiltViewModel(),
) {
    val s = LocalStrings.current
    val ctx = LocalContext.current
    val visible by viewModel.visible.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val sources by viewModel.sources.collectAsState()
    val presets by viewModel.presetPlugins.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val repos by viewModel.repos.collectAsState()
    val error by viewModel.error.collectAsState()

    // Re-Scan beim Tab-Sichtbarwerden (ON_RESUME) — entdeckt Neuinstallationen + prunt Deinstalliertes.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.rescanLocal()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var tofuFor by remember { mutableStateOf<DiscoveredPlugin?>(null) }
    var configFor by remember { mutableStateOf<DiscoveredPlugin?>(null) }
    var presetDetailFor by remember { mutableStateOf<DiscoveredPresetPlugin?>(null) }

    fun uninstall(pkg: String) {
        ctx.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))
    }

    // Schneller Lookup vom InstalledEntry zurück auf das Discovered-Plugin (für ⚙-Aktionen).
    fun sourceFor(pkg: String): DiscoveredPlugin? = sources.firstOrNull { it.packageName == pkg }
    fun presetFor(pkg: String): DiscoveredPresetPlugin? = presets.firstOrNull { it.packageName == pkg }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (visible.installed.isEmpty() && visible.discovered.isEmpty() && !loading) {
            Text(
                s.pluginTabEmpty,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Installierte (oben)
        visible.installed.forEach { item ->
            when (item.kind) {
                PluginKind.SOURCE -> sourceFor(item.packageName)?.let { src ->
                    PluginRow(
                        title = src.metadata.displayName,
                        typeLabel = s.pluginTabSourceLabel,
                        abiLabel = s.pluginAbiLabel,
                        abiVersion = src.abiVersion,
                        configureLabel = s.pluginConfigure,
                        uninstallLabel = s.pluginUninstall,
                        onConfigure = { tofuFor = src },
                        onUninstall = { uninstall(src.packageName) },
                    )
                }
                PluginKind.PRESET -> presetFor(item.packageName)?.let { p ->
                    PluginRow(
                        title = p.displayName,
                        typeLabel = s.pluginTabPresetLabel,
                        abiLabel = s.pluginAbiLabel,
                        abiVersion = p.abiVersion,
                        configureLabel = s.pluginConfigure,
                        uninstallLabel = s.pluginUninstall,
                        onConfigure = { presetDetailFor = p },
                        onUninstall = { uninstall(p.packageName) },
                    )
                }
            }
        }
        if (visible.showDivider) {
            HorizontalDivider(thickness = EinkTokens.hairline, color = MaterialTheme.colorScheme.outlineVariant)
        }
        if (loading) LoadingIndicator()
        // Entdeckte (unten)
        visible.discovered.forEach { row ->
            RepoRow(row = row, onInstall = { viewModel.install(row) })
        }
    }

    if (showRepoManagement) {
        RepoManagementModal(
            repos = repos,
            onDismiss = onRepoManagementDismiss,
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

    tofuFor?.let { plugin ->
        PluginTofuModal(plugin = plugin, onDismiss = { tofuFor = null }, onConfirm = { tofuFor = null; configFor = plugin })
    }
    configFor?.let { plugin ->
        PluginConfigModal(plugin = plugin, onDismiss = { configFor = null }, onSubmit = { values -> viewModel.addPluginSource(plugin, values); configFor = null })
    }
    presetDetailFor?.let { p ->
        EinkInfoDialog(title = s.pluginPresetsTitle, onDismiss = { presetDetailFor = null }, closeLabel = s.close) {
            p.presets.forEach { spec ->
                PresetImportRow(
                    spec = spec,
                    imported = profiles.any { it.pluginPackage == p.packageName && it.name == spec.name },
                    importLabel = s.pluginPresetImport,
                    removeLabel = s.pluginPresetRemove,
                    importedLabel = s.pluginPresetImported,
                    onImport = { viewModel.importPreset(p.packageName, spec) },
                    onRemove = {
                        profiles.firstOrNull { it.pluginPackage == p.packageName && it.name == spec.name }
                            ?.let { viewModel.removeImportedProfile(it) }
                    },
                )
            }
        }
    }
}

/** Eine Plugin-Zeile: Name + Typ-Label + ABI links, ⚙ + 🗑 rechts. Flach, 1.5px-Border, keine Animation. */
@Composable
private fun PluginRow(
    title: String,
    typeLabel: String,
    abiLabel: String,
    abiVersion: Int,
    configureLabel: String,
    uninstallLabel: String,
    onConfigure: () -> Unit,
    onUninstall: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(EinkTokens.hairline, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                "$typeLabel · $abiLabel $abiVersion",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onConfigure) {
            Icon(AppIcons.Settings, contentDescription = configureLabel, modifier = Modifier.size(22.dp))
        }
        IconButton(onClick = onUninstall) {
            Icon(AppIcons.Delete, contentDescription = uninstallLabel, modifier = Modifier.size(22.dp))
        }
    }
}

/** Ein Preset im Detail-Modal: Name links, Import-/Entfernen-Aktion rechts. */
@Composable
private fun PresetImportRow(
    spec: ColorPresetSpec,
    imported: Boolean,
    importLabel: String,
    removeLabel: String,
    importedLabel: String,
    onImport: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(spec.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (imported) {
            Text(
                importedLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp),
            )
            IconButton(onClick = onRemove) {
                Icon(AppIcons.Delete, contentDescription = removeLabel, modifier = Modifier.size(20.dp))
            }
        } else {
            IconButton(onClick = onImport) {
                Icon(AppIcons.Download, contentDescription = importLabel, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/**
 * Eine Browser-Zeile: Name + Typ/Version/ABI/Repo-Untertitel links, Install-/Update-/Status rechts.
 * Spiegelt den flachen 1.5px-Border-Stil von [PluginRow]. Keine Animation.
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
