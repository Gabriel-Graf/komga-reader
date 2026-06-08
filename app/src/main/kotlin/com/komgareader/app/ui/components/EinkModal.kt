package com.komgareader.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.komgareader.app.ui.icons.AppIcons
import com.komgareader.app.ui.theme.EinkTokens

/**
 * Modal im Onyx-Look: **immer schwarzer Rand** (strongBorder/outline), weiße Surface,
 * großer Radius. Ersetzt das nackte Material `AlertDialog`. Titel oben, Inhalt mittig,
 * Aktionen unten, voll-breit geteilt (Abbrechen links, Bestätigen rechts). Genau ein Modal gleichzeitig.
 */
@Composable
fun EinkModal(
    title: String,
    onDismiss: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String,
    modifier: Modifier = Modifier,
    confirmEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .border(
                    EinkTokens.strongBorder,
                    MaterialTheme.colorScheme.outline,
                    MaterialTheme.shapes.large,
                ),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                content()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EinkOutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(dismissLabel) }
                    Button(onClick = onConfirm, enabled = confirmEnabled, modifier = Modifier.weight(1f)) { Text(confirmLabel) }
                }
            }
        }
    }
}

/**
 * Read-only-Modal im Onyx-Look: Titel links, **nur ein X oben rechts** zum Schließen,
 * darunter der Inhalt. Für reine Infos (z. B. Preset-Werte anzeigen) ohne Aktion.
 */
@Composable
fun EinkInfoDialog(
    title: String,
    onDismiss: () -> Unit,
    closeLabel: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    // usePlatformDefaultWidth=false + schmale Surface → kompaktes, inhaltsgerechtes Modal
    // (der Default-Dialog wäre für die wenigen Werte unnötig breit).
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.6f)
                .border(
                    EinkTokens.strongBorder,
                    MaterialTheme.colorScheme.outline,
                    MaterialTheme.shapes.large,
                ),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(20.dp)) {
                // Sticky Header: Titel links, X rechts.
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(AppIcons.Close, contentDescription = closeLabel, modifier = Modifier.size(22.dp))
                    }
                }
                // Scrollender Body: lange Inhalte (z. B. Typo-Panel) bleiben erreichbar.
                Column(
                    Modifier
                        .heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content,
                )
            }
        }
    }
}
