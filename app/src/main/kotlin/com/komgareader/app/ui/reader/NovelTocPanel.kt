package com.komgareader.app.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.EinkInfoDialog
import com.komgareader.ui.icons.AppIcons
import com.komgareader.domain.render.Chapter

/** Eine oberste TOC-Ebene mit ihren (tieferen) Unter-Einträgen. */
internal data class TocGroup(val parent: Chapter, val children: List<Chapter>)

/**
 * Gruppiert die flache, tiefenmarkierte Kapitelliste in oberste Ebenen + deren
 * Unterkapitel. Die niedrigste vorkommende Tiefe ist die oberste Ebene; alles Tiefere
 * fällt als Kind unter das jeweils vorhergehende Top-Level. Reine Funktion (unit-testbar).
 */
internal fun groupChapters(chapters: List<Chapter>): List<TocGroup> {
    if (chapters.isEmpty()) return emptyList()
    val baseDepth = chapters.minOf { it.depth }
    val groups = mutableListOf<TocGroup>()
    var parent: Chapter? = null
    var children = mutableListOf<Chapter>()
    for (chapter in chapters) {
        if (chapter.depth <= baseDepth || parent == null) {
            parent?.let { groups.add(TocGroup(it, children)) }
            parent = chapter
            children = mutableListOf()
        } else {
            children.add(chapter)
        }
    }
    parent?.let { groups.add(TocGroup(it, children)) }
    return groups
}

/**
 * Roman-Inhaltsverzeichnis ([EinkInfoDialog]): oberste Ebenen mit **faltbaren**
 * Unterkapiteln (per Default **alles zu**). Ein Tap auf den Titel springt zum Anker und
 * schließt; der Chevron links klappt nur auf/zu. Enge Zeilen, **keine** Trennlinien
 * (ein Inhaltsverzeichnis trägt seine Gliederung über Einrückung + Chevron).
 *
 * **Keine Animation** (`animation-gating`). Texte über [LocalStrings] (DE+EN).
 */
@Composable
fun NovelTocPanel(
    chapters: List<Chapter>,
    onChapterSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current

    EinkInfoDialog(
        title = strings.novelToc,
        onDismiss = onDismiss,
        closeLabel = strings.close,
        contentSpacing = 0.dp,
    ) {
        if (chapters.isEmpty()) {
            Text(
                strings.novelTocEmpty,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@EinkInfoDialog
        }

        val groups = remember(chapters) { groupChapters(chapters) }
        var expanded by remember(chapters) { mutableStateOf(emptySet<Int>()) }

        groups.forEachIndexed { index, group ->
            TocParentRow(
                chapter = group.parent,
                hasChildren = group.children.isNotEmpty(),
                expanded = index in expanded,
                onToggle = {
                    expanded = if (index in expanded) expanded - index else expanded + index
                },
                onSelect = {
                    onChapterSelected(group.parent.anchor)
                    onDismiss()
                },
            )
            if (index in expanded) {
                group.children.forEach { child ->
                    TocChildRow(child) {
                        onChapterSelected(child.anchor)
                        onDismiss()
                    }
                }
            }
        }
    }
}

@Composable
private fun TocParentRow(
    chapter: Chapter,
    hasChildren: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSelect: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (hasChildren) {
            IconButton(onClick = onToggle, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (expanded) AppIcons.ChevronDown else AppIcons.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            Spacer(Modifier.width(36.dp))
        }
        Text(
            text = chapter.title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .clickable(enabled = chapter.anchor.isNotEmpty(), onClick = onSelect)
                .padding(vertical = 10.dp),
        )
    }
}

@Composable
private fun TocChildRow(chapter: Chapter, onSelect: () -> Unit) {
    // Einrückung: Chevron-Spalte (36) + je Tiefe ein Schritt.
    val indent = (36 + chapter.depth.coerceAtLeast(1) * 16).dp
    Text(
        text = chapter.title,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = chapter.anchor.isNotEmpty(), onClick = onSelect)
            .padding(start = indent, top = 8.dp, bottom = 8.dp),
    )
}
