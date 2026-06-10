package com.komgareader.app.ui.collections

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.komgareader.app.ui.icons.AppIcons
import com.komgareader.domain.model.CollectionKind
import com.komgareader.domain.model.CollectionMember
import com.komgareader.domain.model.UserCollection

/**
 * Picker-Sheet „Zu Collection hinzufügen" — wiederverwendbar für Serien und Bücher.
 *
 * **Staged-Auswahl:** Antippen einer Zeile ändert nur die lokale Auswahl, **Bestätigen**
 * übernimmt sie (add/remove gegen den aktuellen Mitgliedsstand), **Abbrechen** verwirft sie.
 * Das **„+" im Kopf** (wo sonst das Schließen-X säße) blendet die Inline-Neuanlage ein.
 *
 * Über [hiltViewModel] mit dem Activity-weiten [CollectionsViewModel] verbunden; öffnende
 * Screens rendern das Sheet nur — kein eigenes VM nötig.
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

    // Lokale (staged) Auswahl: die Collection-IDs, in denen das Werk nach Bestätigen liegen soll.
    // Einmal aus dem aktuellen Mitgliedsstand geseedet, sobald die Liste das erste Mal da ist
    // (neu angelegte Collections setzen den Seed nicht zurück → keine Auswahl geht verloren).
    val staged = remember { mutableStateListOf<Long>() }
    var seeded by remember { mutableStateOf(false) }
    LaunchedEffect(filtered) {
        if (!seeded && filtered.isNotEmpty()) {
            staged.clear()
            staged.addAll(filtered.filter { it.isMemberOf(member) }.map { it.id })
            seeded = true
        }
    }

    EinkModal(
        title = s.addToCollection,
        onDismiss = onDismiss,
        confirmLabel = s.save,
        onConfirm = {
            // Staged-Auswahl gegen den Ist-Stand abgleichen: nur echte Änderungen anwenden.
            filtered.forEach { collection ->
                val want = collection.id in staged
                val have = collection.isMemberOf(member)
                when {
                    want && !have -> viewModel.addMember(collection.id, member)
                    !want && have -> viewModel.removeMember(collection.id, member.sourceId, member.remoteId)
                }
            }
            onDismiss()
        },
        dismissLabel = s.cancel,
        headerAction = {
            IconButton(onClick = { showNewField = !showNewField }) {
                Icon(AppIcons.Plus, contentDescription = s.newCollection)
            }
        },
    ) {
        if (filtered.isEmpty() && !showNewField) {
            Text(
                text = s.collectionsEmpty,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            filtered.forEach { collection ->
                ChoiceRow(
                    label = collection.name,
                    selected = collection.id in staged,
                    dense = true,
                    onSelect = {
                        if (collection.id in staged) staged.remove(collection.id)
                        else staged.add(collection.id)
                    },
                )
            }
        }

        // Inline-Neuanlage über das „+" im Kopf: Feld + „Erstellen". Die neue Collection
        // erscheint danach in der Liste und kann ausgewählt werden.
        if (showNewField) {
            Spacer(Modifier.height(4.dp))
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
        }
    }
}

/** Ist [member] (gleiche Quelle + Remote-ID) in dieser Collection? */
private fun UserCollection.isMemberOf(member: CollectionMember): Boolean =
    members.any { it.sourceId == member.sourceId && it.remoteId == member.remoteId }
