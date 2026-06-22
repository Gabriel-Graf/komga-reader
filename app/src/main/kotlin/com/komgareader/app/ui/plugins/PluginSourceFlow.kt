package com.komgareader.app.ui.plugins

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.EinkModal
import com.komgareader.app.ui.settings.PluginConfigForm
import com.komgareader.app.ui.settings.rememberPluginFormState
import com.komgareader.plugin.host.DiscoveredPlugin

/**
 * TOFU-Bestätigungs-Dialog (Schritt 1 des Plugin-Flusses). Zeigt Anzeigename, Paketname
 * und Zertifikat-Fingerabdruck (SHA-256) des Plugins. Erst nach expliziter Bestätigung
 * werden die Plugin-Felder konfiguriert (Schritt 2). Abbrechen bricht den gesamten Fluss ab.
 *
 * E-Ink-Invariante: Kein zweites Modal gleichzeitig — dieser Dialog ersetzt das Add-Modal
 * (der aufrufende State wechselt von [ConnectionModal.Add] zu [ConnectionModal.PluginTofu]).
 */
@Composable
internal fun PluginTofuModal(
    plugin: DiscoveredPlugin,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val s = LocalStrings.current

    EinkModal(
        title = s.pluginTrustTitle,
        onDismiss = onDismiss,
        confirmLabel = s.pluginTrustConfirm,
        onConfirm = onConfirm,
        dismissLabel = s.cancel,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(s.pluginTrustBody, style = MaterialTheme.typography.bodyMedium)
            // Paketname + Zertifikat-Fingerabdruck: monospaced, damit Hex-String lesbar ist.
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PluginInfoRow(label = plugin.metadata.displayName, value = plugin.packageName)
                PluginInfoRow(
                    label = "SHA-256",
                    value = plugin.signatureSha256
                        .chunked(8)
                        .joinToString(" "),
                )
            }
        }
    }
}

/** Kompakte Label-/Wert-Zeile im TOFU-Dialog: Label gedämpft links, Wert rechts. */
@Composable
internal fun PluginInfoRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * Plugin-Konfigurations-Modal (Schritt 2 des Plugin-Flusses). Rendert generisch die
 * Schema-Felder des Plugins via [PluginConfigForm] (state-Variante ohne eigenen Submit-Button).
 * Der [EinkModal]-eigene Bestätigen-Button übernimmt den Submit — [formState.isValid] steuert,
 * ob er aktiv ist.
 *
 * E-Ink-Invariante: Kein zweites Modal gleichzeitig — dieser Dialog ersetzt [PluginTofuModal].
 */
@Composable
internal fun PluginConfigModal(
    plugin: DiscoveredPlugin,
    onDismiss: () -> Unit,
    onSubmit: (Map<String, String>) -> Unit,
    initialValues: Map<String, String> = emptyMap(),
) {
    val s = LocalStrings.current
    // Formular-Zustand im Aufrufer gehalten, damit EinkModal confirmEnabled + onConfirm
    // auf den aktuellen Formular-Zustand zugreifen kann (Compose-State-Hoisting).
    // initialValues vorbelegt = Bearbeiten (gespeicherte Plugin-Config); leer = Hinzufügen.
    val formState = rememberPluginFormState(plugin.configSchema, initialValues)

    EinkModal(
        title = plugin.metadata.displayName,
        onDismiss = onDismiss,
        confirmLabel = s.save,
        onConfirm = { onSubmit(formState.snapshot()) },
        confirmEnabled = formState.isValid,
        dismissLabel = s.cancel,
    ) {
        PluginConfigForm(formState)
    }
}
