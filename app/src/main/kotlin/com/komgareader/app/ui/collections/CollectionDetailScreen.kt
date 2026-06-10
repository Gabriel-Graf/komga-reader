package com.komgareader.app.ui.collections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.request.ImageRequest
import com.komgareader.app.data.coil.SourceCover
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.ChoiceRow
import com.komgareader.app.ui.components.EinkInfoDialog
import com.komgareader.app.ui.components.EinkModal
import com.komgareader.app.ui.components.EinkOutlinedButton
import com.komgareader.app.ui.components.FilteredAsyncImage
import com.komgareader.app.ui.components.SectionHeader
import com.komgareader.app.ui.components.SubPageScaffold
import com.komgareader.app.ui.components.TileTitleBand
import com.komgareader.app.ui.icons.AppIcons
import com.komgareader.app.ui.theme.EinkTokens
import com.komgareader.domain.model.CollectionKind
import com.komgareader.domain.model.CollectionMember
import com.komgareader.domain.model.SyncStatus
import com.komgareader.domain.repository.CollectionSyncLink

/**
 * Detail-Screen einer einzelnen User-Collection.
 *
 * Zeigt Name (als TopBar-Titel), Mitgliederliste (titel-basiert, ohne Cover),
 * Sync-Status je Quelle und eine Löschen-Aktion mit optionalem Server-Toggle.
 */
@Composable
fun CollectionDetailScreen(
    collectionId: Long,
    onBack: () -> Unit,
    viewModel: CollectionsViewModel = hiltViewModel(),
) {
    val s = LocalStrings.current
    val collections by viewModel.collections.collectAsState()
    val collection = collections.find { it.id == collectionId }

    val syncLinks by viewModel.syncLinks(collectionId).collectAsState(emptyList())

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var serverToo by remember { mutableStateOf(false) }

    SubPageScaffold(
        title = collection?.name ?: s.collections,
        onBack = onBack,
        scrollable = true,
    ) {
        if (collection == null) return@SubPageScaffold

        // Sync-Aktion prominent oben
        EinkOutlinedButton(
            onClick = { viewModel.syncNow(collectionId) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(AppIcons.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text(s.collectionSyncNow)
        }

        // Mitglieder als Cover-Gitter (3 Spalten, nicht-lazy — passt in den scrollenden Scaffold-Body).
        if (collection.members.isNotEmpty()) {
            SectionHeader(
                text = when (collection.kind) {
                    CollectionKind.SERIES -> s.collectionKindSeries
                    CollectionKind.BOOK -> s.collectionKindBook
                },
            )
            val isSeries = collection.kind == CollectionKind.SERIES
            Column(verticalArrangement = Arrangement.spacedBy(EinkTokens.tileGap)) {
                collection.members.chunked(3).forEach { rowMembers ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(EinkTokens.tileGap)) {
                        rowMembers.forEach { member ->
                            MemberTile(
                                member = member,
                                isSeries = isSeries,
                                onRemove = { viewModel.removeMember(collectionId, member.sourceId, member.remoteId) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(3 - rowMembers.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        // Sync-Status-Sektion (nur wenn Links vorhanden)
        if (syncLinks.isNotEmpty()) {
            SectionHeader(text = s.collectionSyncInfoTitle)
            Column(verticalArrangement = Arrangement.spacedBy(EinkTokens.tileGap)) {
                syncLinks.forEach { link ->
                    SyncLinkRow(link = link)
                }
            }
        }

        // Löschen-Button unten
        Spacer(Modifier.height(8.dp))
        EinkOutlinedButton(
            onClick = { showDeleteConfirm = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(AppIcons.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text(s.deleteCollection)
        }
    }

    // Löschen-Bestätigungs-Modal
    if (showDeleteConfirm) {
        EinkModal(
            title = s.deleteCollection,
            onDismiss = { showDeleteConfirm = false },
            confirmLabel = s.deleteCollection,
            onConfirm = {
                viewModel.delete(collectionId, serverToo)
                showDeleteConfirm = false
                onBack()
            },
            dismissLabel = s.cancel,
        ) {
            ChoiceRow(
                label = s.deleteCollectionServerToo,
                selected = serverToo,
                onSelect = { serverToo = !serverToo },
            )
        }
    }
}

/**
 * Mitglied als **Cover-Kachel** (2:3): Cover (quellen-agnostisch über [SourceCover]), Titelband
 * unten, Entfernen-Aktion oben rechts.
 */
@Composable
private fun MemberTile(
    member: CollectionMember,
    isSeries: Boolean,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val ctx = LocalContext.current
    val request = remember(member, isSeries) {
        ImageRequest.Builder(ctx)
            .data(SourceCover(member.sourceId, member.remoteId, isSeries))
            .crossfade(false).build()
    }
    Box(
        modifier
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(8.dp))
            .border(EinkTokens.hairline, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
    ) {
        FilteredAsyncImage(
            model = request,
            contentDescription = member.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        val cornerShape = RoundedCornerShape(bottomStart = 10.dp)
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .clip(cornerShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(EinkTokens.hairline, MaterialTheme.colorScheme.outline, cornerShape),
        ) {
            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                Icon(
                    AppIcons.Delete,
                    contentDescription = s.removeFromCollection,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        TileTitleBand(member.title, Modifier.align(Alignment.BottomStart))
    }
}

/**
 * Zeile für einen Sync-Link: Status-Text rechts, auf INFO/UNSUPPORTED/FORBIDDEN tippbar
 * (öffnet Erklär-Dialog).
 */
@Composable
private fun SyncLinkRow(
    link: CollectionSyncLink,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val isProblematic = link.status in setOf(SyncStatus.UNSUPPORTED, SyncStatus.FORBIDDEN, SyncStatus.LOCAL_ONLY)
    var showInfo by remember { mutableStateOf(false) }

    val rowShape = RoundedCornerShape(EinkTokens.tileRadius)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(EinkTokens.hairline, MaterialTheme.colorScheme.outline, rowShape)
            .then(
                if (isProblematic) Modifier.clickable { showInfo = true } else Modifier,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Quellen-ID als lesbarer Bezeichner (nur die numerische ID verfügbar)
        Text(
            text = "Quelle ${link.sourceId}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = link.status.name,
                style = MaterialTheme.typography.labelMedium,
                color = if (isProblematic) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isProblematic) {
                Icon(
                    imageVector = AppIcons.Info,
                    contentDescription = s.collectionSyncInfoTitle,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }

    if (showInfo) {
        EinkInfoDialog(
            title = s.collectionSyncInfoTitle,
            onDismiss = { showInfo = false },
            closeLabel = s.close,
        ) {
            when (link.status) {
                SyncStatus.FORBIDDEN ->
                    Text(s.collectionSyncForbidden, style = MaterialTheme.typography.bodyMedium)
                else ->
                    Text(s.collectionSyncUnsupported, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
