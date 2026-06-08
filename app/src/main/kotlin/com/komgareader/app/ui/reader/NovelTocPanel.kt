package com.komgareader.app.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.EinkInfoDialog
import com.komgareader.domain.render.Chapter

/**
 * Roman-Inhaltsverzeichnis im Onyx-Look ([EinkInfoDialog] — ein Modal über dem
 * Reader, Hardware-Back/X schließt). Listet die [chapters] tiefenmarkiert auf;
 * ein Tap springt zum (layout-unabhängigen) Anker und schließt das Panel. Der
 * Reader rendert daraufhin die neue Seite + löst einen Full-Refresh aus.
 *
 * **Keine Animation:** reine Sofort-State-Wechsel (E-Ink, `animation-gating`).
 * Alle Texte über [LocalStrings] (DE+EN). Leeres TOC: lokalisierter Hinweis.
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
    ) {
        if (chapters.isEmpty()) {
            Text(
                strings.novelTocEmpty,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            chapters.forEach { chapter ->
                TocRow(
                    chapter = chapter,
                    onSelect = {
                        onChapterSelected(chapter.anchor)
                        onDismiss()
                    },
                )
            }
        }
    }
}

@Composable
private fun TocRow(chapter: Chapter, onSelect: () -> Unit) {
    // Einrückung nach Verschachtelungstiefe (0 = oberste Ebene).
    val indent = (chapter.depth.coerceAtLeast(0) * 16).dp
    Text(
        text = chapter.title,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = chapter.anchor.isNotEmpty(), onClick = onSelect)
            .padding(start = indent, top = 12.dp, bottom = 12.dp),
    )
}
