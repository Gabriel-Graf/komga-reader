package com.komgareader.app.ui.plugins

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.komgareader.plugin.host.DiscoveredPlugin

/**
 * Geteilter TOFU→Config→Persist-Ablauf für das Hinzufügen einer Quellen-Plugin-Verbindung.
 * Hält den 2-Stufen-Modal-Zustand intern und ruft [onAdd] mit dem gewählten Plugin +
 * Config-Werten. Wiederverwendet von Plugins-Tab ([PluginsScreen]) und „Server hinzufügen"
 * ([AddConnectionModal]) — `shared-structure-before-variants`.
 *
 * [trigger] = das aktuell gewählte Plugin (von außen gesetzt); `null` = kein Flow offen.
 * E-Ink-Invariante: genau ein Modal gleichzeitig (TOFU → dann Config, nie beide).
 */
@Composable
fun AddPluginSourceModals(
    trigger: DiscoveredPlugin?,
    onDismiss: () -> Unit,
    onAdd: (DiscoveredPlugin, Map<String, String>) -> Unit,
) {
    var confirmed by remember(trigger) { mutableStateOf(false) }
    val plugin = trigger ?: return
    if (!confirmed) {
        PluginTofuModal(plugin = plugin, onDismiss = onDismiss, onConfirm = { confirmed = true })
    } else {
        PluginConfigModal(
            plugin = plugin,
            onDismiss = onDismiss,
            onSubmit = { values -> onAdd(plugin, values); onDismiss() },
        )
    }
}
