package com.komgareader.app.ui.plugins

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.EinkInfoDialog
import com.komgareader.app.ui.components.EinkOutlinedButton
import com.komgareader.app.ui.components.EinkTextField
import com.komgareader.app.ui.components.EinkToggle
import com.komgareader.app.ui.components.LoadingIndicator
import com.komgareader.ui.icons.AppIcons
import com.komgareader.ui.theme.EinkTokens
import com.komgareader.data.plugin.repo.BrowserRow
import com.komgareader.data.plugin.repo.InstallState
import com.komgareader.data.plugin.repo.PluginKind
import com.komgareader.data.plugin.repo.RepoSource
import com.komgareader.plugin.ColorPresetSpec
import com.komgareader.plugin.host.DataPluginInfo
import com.komgareader.plugin.host.DiscoveredDataPlugin
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
    val languageDataPlugins by viewModel.languageDataPlugins.collectAsState()
    val readerPresetDataPlugins by viewModel.readerPresetDataPlugins.collectAsState()
    val uiPackDataPlugins by viewModel.uiPackDataPlugins.collectAsState()
    val panelModelDataPlugins by viewModel.panelModelDataPlugins.collectAsState()
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
    var presetDetailFor by remember { mutableStateOf<DiscoveredPresetPlugin?>(null) }

    fun uninstall(pkg: String) {
        ctx.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))
    }

    // Schneller Lookup vom InstalledEntry zurück auf das Discovered-Plugin (für ⚙-Aktionen).
    fun sourceFor(pkg: String): DiscoveredPlugin? = sources.firstOrNull { it.packageName == pkg }
    fun presetFor(pkg: String): DiscoveredPresetPlugin? = presets.firstOrNull { it.packageName == pkg }
    fun languageFor(pkg: String): DiscoveredDataPlugin? = languageDataPlugins.firstOrNull { it.packageName == pkg }
    fun readerPresetFor(pkg: String): DiscoveredDataPlugin? = readerPresetDataPlugins.firstOrNull { it.packageName == pkg }
    fun uiPackFor(pkg: String): DiscoveredDataPlugin? = uiPackDataPlugins.firstOrNull { it.packageName == pkg }
    fun panelModelFor(pkg: String): DataPluginInfo? = panelModelDataPlugins.firstOrNull { it.packageName == pkg }

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
        // Installierte (oben) — mit Abschnitts-Überschrift zur klareren Einordnung.
        if (visible.installed.isNotEmpty()) {
            SectionHeading(s.pluginSectionInstalled)
        }
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
                        onInfo = { viewModel.openInfoForInstalled(item) },
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
                        onInfo = { viewModel.openInfoForInstalled(item) },
                        onConfigure = { presetDetailFor = p },
                        onUninstall = { uninstall(p.packageName) },
                    )
                }
                PluginKind.LANGUAGE -> languageFor(item.packageName)?.let { lang ->
                    DataPluginRow(
                        title = lang.displayName,
                        typeLabel = s.pluginTabLanguageLabel,
                        abiLabel = s.pluginAbiLabel,
                        abiVersion = lang.abiVersion,
                        uninstallLabel = s.pluginUninstall,
                        onInfo = { viewModel.openInfoForInstalled(item) },
                        onUninstall = { uninstall(lang.packageName) },
                    )
                }
                PluginKind.READER_PRESET -> readerPresetFor(item.packageName)?.let { rp ->
                    DataPluginRow(
                        title = rp.displayName,
                        typeLabel = s.pluginTabReaderPresetLabel,
                        abiLabel = s.pluginAbiLabel,
                        abiVersion = rp.abiVersion,
                        uninstallLabel = s.pluginUninstall,
                        onInfo = { viewModel.openInfoForInstalled(item) },
                        onUninstall = { uninstall(rp.packageName) },
                    )
                }
                PluginKind.UI_PACK -> uiPackFor(item.packageName)?.let { up ->
                    DataPluginRow(
                        title = up.displayName,
                        typeLabel = s.pluginTabUiPackLabel,
                        abiLabel = s.pluginAbiLabel,
                        abiVersion = up.abiVersion,
                        uninstallLabel = s.pluginUninstall,
                        onInfo = { viewModel.openInfoForInstalled(item) },
                        onUninstall = { uninstall(up.packageName) },
                    )
                }
                PluginKind.PANEL_MODEL -> panelModelFor(item.packageName)?.let { model ->
                    DataPluginRow(
                        title = model.displayName,
                        typeLabel = s.pluginTabPanelModelLabel,
                        abiLabel = s.pluginAbiLabel,
                        abiVersion = model.abiVersion,
                        uninstallLabel = s.pluginUninstall,
                        onInfo = { viewModel.openInfoForInstalled(item) },
                        onUninstall = { uninstall(model.packageName) },
                    )
                }
            }
        }
        if (visible.showDivider) {
            SectionDivider()
        }
        if (loading) LoadingIndicator()
        // Entdeckte (unten) — eigene Abschnitts-Überschrift.
        if (visible.discovered.isNotEmpty()) {
            SectionHeading(s.pluginSectionAvailable)
        }
        visible.discovered.forEach { row ->
            RepoRow(row = row, onInfo = { viewModel.openInfo(row) }, onInstall = { viewModel.install(row) })
        }
    }

    val infoFor by viewModel.infoFor.collectAsState()
    val readmeState by viewModel.readmeState.collectAsState()
    infoFor?.let { row ->
        PluginInfoModal(row = row, readme = readmeState, onDismiss = { viewModel.closeInfo() })
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

    if (!showRepoManagement) {
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

    AddPluginSourceModals(
        trigger = tofuFor,
        onDismiss = { tofuFor = null },
        onAdd = { plugin, values -> viewModel.addPluginSource(plugin, values) },
    )
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

/** Abschnitts-Überschrift („Installiert"/„Verfügbar"): kleine, kräftige Label-Zeile, gedämpft. */
@Composable
private fun SectionHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/**
 * Trenner zwischen installierten und entdeckten Plugins: kurzer, zentrierter, kräftiger Balken mit
 * großzügigem Abstand oben/unten — macht die Sektions-Grenze deutlicher als eine durchgehende Hairline.
 */
@Composable
private fun SectionDivider() {
    // Unten weniger Abstand als oben, weil die folgende „Verfügbar"-Überschrift selbst Abstand mitbringt.
    Box(
        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.55f)
                .height(EinkTokens.strongBorder)
                .clip(RoundedCornerShape(EinkTokens.strongBorder))
                .background(MaterialTheme.colorScheme.outline),
        )
    }
}

/** Eine Plugin-Zeile: Name + Typ-Label + ABI links, ℹ + ⚙ + 🗑 rechts. Flach, 1.5px-Border, keine Animation. */
@Composable
private fun PluginRow(
    title: String,
    typeLabel: String,
    abiLabel: String,
    abiVersion: Int,
    configureLabel: String,
    uninstallLabel: String,
    onInfo: () -> Unit,
    onConfigure: () -> Unit,
    onUninstall: () -> Unit,
) {
    val s = LocalStrings.current
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
        IconButton(onClick = onInfo) {
            Icon(AppIcons.Info, contentDescription = s.pluginInfo, modifier = Modifier.size(22.dp))
        }
        IconButton(onClick = onConfigure) {
            Icon(AppIcons.Settings, contentDescription = configureLabel, modifier = Modifier.size(22.dp))
        }
        IconButton(onClick = onUninstall) {
            Icon(AppIcons.Delete, contentDescription = uninstallLabel, modifier = Modifier.size(22.dp))
        }
    }
}

/**
 * Plugin-Zeile für data-only Plugins (Sprache, Reader-Preset): Name + Typ-Label + ABI links,
 * ℹ + 🗑 rechts — kein ⚙ (keine konfigurierbaren Werte). Flach, 1.5px-Border, keine Animation.
 */
@Composable
private fun DataPluginRow(
    title: String,
    typeLabel: String,
    abiLabel: String,
    abiVersion: Int,
    uninstallLabel: String,
    onInfo: () -> Unit,
    onUninstall: () -> Unit,
) {
    val s = LocalStrings.current
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
        IconButton(onClick = onInfo) {
            Icon(AppIcons.Info, contentDescription = s.pluginInfo, modifier = Modifier.size(22.dp))
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
private fun RepoRow(row: BrowserRow, onInfo: () -> Unit, onInstall: () -> Unit) {
    val s = LocalStrings.current
    val typeLabel = when (row.item.kind) {
        PluginKind.SOURCE -> s.pluginTabSourceLabel
        PluginKind.PRESET -> s.pluginTabPresetLabel
        PluginKind.LANGUAGE -> s.pluginTabLanguageLabel
        PluginKind.READER_PRESET -> s.pluginTabReaderPresetLabel
        PluginKind.UI_PACK -> s.pluginTabUiPackLabel
        PluginKind.PANEL_MODEL -> s.pluginTabPanelModelLabel
    }
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
        IconButton(onClick = onInfo) {
            Icon(AppIcons.Info, contentDescription = s.pluginInfo, modifier = Modifier.size(22.dp))
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
        // Volle Zeilenbreite, Bestätigen als Pfeil-rechts im Feld (kein separater Button mehr).
        EinkTextField(
            value = newUrl,
            onValueChange = { newUrl = it },
            label = s.repoBrowserRepoUrl,
            keyboardType = KeyboardType.Uri,
            confirmLabel = s.repoBrowserAddRepo,
            onConfirm = {
                if (newUrl.isNotBlank()) {
                    onAdd(newUrl.trim())
                    newUrl = ""
                }
            },
        )
    }
}
