package com.komgareader.app.ui.collections

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.EinkInfoDialog
import com.komgareader.app.ui.components.EinkOutlinedButton
import com.komgareader.app.ui.components.LocalContentBottomInset
import com.komgareader.app.ui.icons.AppIcons
import com.komgareader.app.ui.theme.EinkTokens
import com.komgareader.domain.model.CollectionKind
import com.komgareader.domain.model.SyncStatus
import com.komgareader.domain.model.UserCollection
import com.komgareader.domain.repository.CollectionSyncLink

/**
 * Übersicht aller User-Collections mit Sync-Badge für rein lokale Collections.
 * Jede Kachel ist klickbar (→ Detail-Screen). Das Erstellen wird über [onNewCollection]
 * nach außen delegiert (Task 16 verdrahtet den Modal).
 */
@Composable
fun CollectionsScreen(
    onOpenCollection: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onNewCollection: () -> Unit = {},
    viewModel: CollectionsViewModel = hiltViewModel(),
) {
    val s = LocalStrings.current
    val collections by viewModel.collections.collectAsState()
    val localOnly by viewModel.localOnly.collectAsState()

    // showCreate-Zustand für Task-16-Modal (Platzhalter)
    var showCreate by remember { mutableStateOf(false) }

    if (collections.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp),
            ) {
                Text(
                    s.collectionsEmpty,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                EinkOutlinedButton(
                    onClick = {
                        showCreate = true
                        onNewCollection()
                    },
                    modifier = Modifier.fillMaxWidth(0.7f),
                ) {
                    Text(s.newCollection)
                }
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = EinkTokens.screenPadding,
                end = EinkTokens.screenPadding,
                top = EinkTokens.screenPadding,
                bottom = LocalContentBottomInset.current + EinkTokens.screenPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(EinkTokens.tileGap),
        ) {
            item {
                EinkOutlinedButton(
                    onClick = {
                        showCreate = true
                        onNewCollection()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(AppIcons.Plus, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(s.newCollection)
                }
            }
            items(collections, key = { it.id }) { collection ->
                CollectionTile(
                    collection = collection,
                    isLocalOnly = localOnly[collection.id] == true,
                    syncLinks = viewModel.syncLinks(collection.id).collectAsState(emptyList()).value,
                    onClick = { onOpenCollection(collection.id) },
                )
            }
        }
    }

    // Platzhalter für Modal aus Task 16 — noch kein Inhalt, aber der Zustand existiert.
    if (showCreate) {
        // Modal wird in Task 16 hier eingehängt.
        showCreate = false
    }
}

/**
 * Einzelne Collection-Kachel: Name + Anzahl Mitglieder; ggf. „Nur lokal"-Badge rechts.
 * Der Badge hat einen eigenen Click-Handler und öffnet einen Erklär-Dialog, ohne gleichzeitig
 * [onClick] der ganzen Zeile auszulösen.
 */
@Composable
private fun CollectionTile(
    collection: UserCollection,
    isLocalOnly: Boolean,
    syncLinks: List<CollectionSyncLink>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    var showSyncInfo by remember { mutableStateOf(false) }

    val tileShape = RoundedCornerShape(EinkTokens.tileRadius)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(EinkTokens.hairline, MaterialTheme.colorScheme.outline, tileShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = collection.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val kindLabel = when (collection.kind) {
                CollectionKind.SERIES -> s.collectionKindSeries
                CollectionKind.BOOK -> s.collectionKindBook
            }
            Text(
                text = "${collection.members.size} $kindLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (isLocalOnly) {
            Spacer(Modifier.width(8.dp))
            LocalOnlyBadge(
                onClick = {
                    showSyncInfo = true
                },
            )
        }
    }

    if (showSyncInfo) {
        val hasUnsupported = syncLinks.any { it.status == SyncStatus.UNSUPPORTED }
        val hasForbidden = syncLinks.any { it.status == SyncStatus.FORBIDDEN }
        EinkInfoDialog(
            title = s.collectionSyncInfoTitle,
            onDismiss = { showSyncInfo = false },
            closeLabel = s.close,
        ) {
            if (hasUnsupported) {
                Text(s.collectionSyncUnsupported, style = MaterialTheme.typography.bodyMedium)
            }
            if (hasForbidden) {
                Text(s.collectionSyncForbidden, style = MaterialTheme.typography.bodyMedium)
            }
            if (!hasUnsupported && !hasForbidden) {
                // LOCAL_ONLY ohne spezifischen Grund — allgemeiner Hinweis
                Text(s.collectionSyncUnsupported, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * Badge-Chip „Nur lokal": Icon + Label, Hairline-Border, eigenständiger Click-Bereich.
 * Stoppt den Click-Event, damit er nicht an die umgebende Zeile weitergegeben wird
 * (die Compose-Clickable-Hierarchie fängt den innersten Handler zuerst).
 */
@Composable
private fun LocalOnlyBadge(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val chipShape = RoundedCornerShape(6.dp)
    Row(
        modifier = modifier
            .border(EinkTokens.hairline, MaterialTheme.colorScheme.outline, chipShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = AppIcons.Local,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = s.collectionLocalOnly,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
