package com.komgareader.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.komgareader.app.ui.slots.resolveSlots
import com.komgareader.ui.slots.DialogState
import com.komgareader.ui.slots.LocalResolvedSlots
import com.komgareader.ui.slots.UiSlotPack
import com.komgareader.ui.theme.EinkTokens

/**
 * Swap-Beweis: ein alternatives Dialog-Layout (Titel zentriert, Aktionen vertikal gestapelt,
 * Bestätigen oben), das dieselbe [DialogState]-Surface anders anordnet — ohne eine Aufrufstelle
 * anzufassen. Belegt, dass ein UI-Pack den Dialog-Look über die `dialog`-Region ersetzen kann,
 * während [EinkModal] unverändert aufgerufen wird. NUR Debug/Preview, keine Nutzer-Einstellung.
 */
@Composable
fun AlternativeDialog(state: DialogState) {
    Dialog(onDismissRequest = state.onDismiss) {
        Surface(
            modifier = Modifier
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
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // headerAction wird in diesem Swap-Beweis bewusst weggelassen (kein Bestandteil des
                // zentrierten Alternativ-Layouts). Produktive Packs müssen headerAction rendern.
                if (state.title.isNotBlank()) {
                    Text(
                        state.title,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                state.content(this)
                Button(
                    onClick = state.onConfirm,
                    enabled = state.confirmEnabled,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(state.confirmLabel) }
                EinkOutlinedButton(onClick = state.onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(state.dismissLabel)
                }
            }
        }
    }
}

/**
 * Zeigt, dass derselbe [EinkModal]-Aufruf über ein Pack mit alternativem `dialog`-Slot den
 * [AlternativeDialog] rendert — gleiche Surface, anderer Dialog, Call-Site unverändert.
 */
@Preview(widthDp = 1264, heightDp = 600)
@Composable
private fun AlternativeDialogPreview() {
    CompositionLocalProvider(
        LocalResolvedSlots provides
            resolveSlots(UiSlotPack(dialog = { state -> AlternativeDialog(state) })),
    ) {
        EinkModal(
            title = "Beispiel-Dialog",
            onDismiss = {},
            confirmLabel = "Bestätigen",
            onConfirm = {},
            dismissLabel = "Abbrechen",
        ) {
            Text("Inhalt des Dialogs.")
        }
    }
}
