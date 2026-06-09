package com.komgareader.app.ui.collections

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.ChoiceRow
import com.komgareader.app.ui.components.EinkModal
import com.komgareader.app.ui.components.EinkOutlinedButton
import com.komgareader.domain.model.CollectionKind
import com.komgareader.domain.model.CollectionMember

/**
 * Picker-Sheet „Zu Collection hinzufügen" — wiederverwendbar für Serien und Bücher.
 *
 * Zeigt alle Collections des passenden [kind] als auswählbare Zeilen; ein Tipp toggelt
 * die Zugehörigkeit sofort (add/remove ohne separaten Bestätigen-Button). Am Ende
 * eine Inline-Neuanlage: Feld + „Erstellen". Der Modal lässt sich über „Schließen"
 * (oder Back) verlassen.
 *
 * Wird über [hiltViewModel] mit dem Activity-weiten [CollectionsViewModel] verbunden;
 * die Screens, die dieses Sheet öffnen, müssen es nur rendern — kein eigenes VM nötig.
 */
@Composable
fun AddToCollectionSheet(
    kind: CollectionKind,
    member: CollectionMember,
    onDismiss: () -> Unit,
    viewModel: CollectionsViewModel = hiltViewModel(),
) {
    val s = LocalStrings.current
    val allCollections by viewModel.collections.collectAsState()
    val filtered = allCollections.filter { it.kind == kind }

    var showNewField by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    EinkModal(
        title = s.addToCollection,
        onDismiss = onDismiss,
        confirmLabel = s.close,
        onConfirm = onDismiss,
        dismissLabel = s.cancel,
        confirmEnabled = true,
    ) {
        // Liste bestehender Collections dieses Typs
        if (filtered.isEmpty()) {
            Text(
                text = s.collectionsEmpty,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            filtered.forEach { collection ->
                val isMember = collection.members.any { m ->
                    m.sourceId == member.sourceId && m.remoteId == member.remoteId
                }
                ChoiceRow(
                    label = collection.name,
                    selected = isMember,
                    dense = true,
                    onSelect = {
                        if (isMember) {
                            viewModel.removeMember(collection.id, member.sourceId, member.remoteId)
                        } else {
                            viewModel.addMember(collection.id, member)
                        }
                    },
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // Neue Collection anlegen
        if (showNewField) {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text(s.collectionName) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            EinkOutlinedButton(
                onClick = {
                    if (newName.isNotBlank()) {
                        viewModel.create(newName.trim(), kind)
                        newName = ""
                        showNewField = false
                    }
                },
                enabled = newName.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(s.create)
            }
        } else {
            EinkOutlinedButton(
                onClick = { showNewField = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(s.newCollection)
            }
        }
    }
}
