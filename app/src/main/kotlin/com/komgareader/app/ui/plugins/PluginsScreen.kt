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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.EinkInfoDialog
import com.komgareader.app.ui.icons.AppIcons
import com.komgareader.app.ui.theme.EinkTokens
import com.komgareader.plugin.ColorPresetSpec
import com.komgareader.plugin.host.DiscoveredPlugin
import com.komgareader.plugin.host.DiscoveredPresetPlugin

/**
 * Plugins-Tab (`HomeScreen` TAB_PLUGINS, content-only — TopBar liefert HomeScreen). Listet INSTALLIERTE
 * Plugin-APKs (Quellen + data-only Color-Presets). E-Ink: flach, 1.5px-Border, Lucide via AppIcons,
 * keine Animation. ⚙ konfiguriert (Quelle: TOFU→Config-Flow; Preset: Import-Detail), 🗑 deinstalliert
 * via OS-Intent. Cleanup deinstallierter Plugins läuft über Re-Scan beim `onResume` (kein Intent-Callback).
 */
@Composable
fun PluginsScreen(modifier: Modifier = Modifier, viewModel: PluginsViewModel = hiltViewModel()) {
    val s = LocalStrings.current
    val ctx = LocalContext.current
    val sources by viewModel.sources.collectAsState()
    val presets by viewModel.presetPlugins.collectAsState()

    // Re-Scan beim Tab-Sichtbarwerden (ON_RESUME) — entdeckt Neuinstallationen + prunt Deinstalliertes.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
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

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (sources.isEmpty() && presets.isEmpty()) {
            Text(
                s.pluginTabEmpty,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        sources.forEach { src ->
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
        presets.forEach { p ->
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
        Text(
            s.pluginTabReposHint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }

    tofuFor?.let { plugin ->
        PluginTofuModal(
            plugin = plugin,
            onDismiss = { tofuFor = null },
            onConfirm = { tofuFor = null; configFor = plugin },
        )
    }
    configFor?.let { plugin ->
        PluginConfigModal(
            plugin = plugin,
            onDismiss = { configFor = null },
            onSubmit = { values -> viewModel.addPluginSource(plugin, values); configFor = null },
        )
    }

    presetDetailFor?.let { p ->
        EinkInfoDialog(title = s.pluginPresetsTitle, onDismiss = { presetDetailFor = null }, closeLabel = s.close) {
            p.presets.forEach { spec ->
                PresetImportRow(
                    spec = spec,
                    imported = viewModel.isImported(p.packageName, spec.name),
                    importLabel = s.pluginPresetImport,
                    removeLabel = s.pluginPresetRemove,
                    importedLabel = s.pluginPresetImported,
                    onImport = { viewModel.importPreset(p.packageName, spec) },
                    onRemove = {
                        viewModel.profiles.value
                            .firstOrNull { it.pluginPackage == p.packageName && it.name == spec.name }
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
