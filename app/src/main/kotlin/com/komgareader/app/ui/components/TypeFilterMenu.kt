package com.komgareader.app.ui.components

import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntOffset
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.i18n.localizedContentType
import com.komgareader.domain.model.ContentType

/**
 * Nicht-modales Filter-Menü fürs Stöbern. Zwei Achsen, kombinierbar (UND):
 * Werk-Typ (Mehrfachauswahl, kein „Auto" — das ist Filter, keine Zuweisung) und
 * „Heruntergeladen" (nur lokal gespeicherte Werke). Tippen toggelt, das Menü bleibt
 * offen; Häkchen markiert aktive Einträge. Klappt unter dem Filter-Icon nach links auf.
 */
@Composable
fun TypeFilterMenu(
    anchor: IntOffset,
    selected: Set<ContentType>,
    onToggle: (ContentType) -> Unit,
    downloadedSelected: Boolean,
    onToggleDownloaded: () -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LocalStrings.current
    val options = listOf(
        ContentType.MANGA,
        ContentType.COMIC,
        ContentType.WEBTOON,
        ContentType.NOVEL,
    )
    AnchoredMenuPopup(anchor = anchor, alignEnd = true, onDismiss = onDismiss) {
        options.forEach { type ->
            FilterRow(label = s.localizedContentType(type), checked = type in selected) { onToggle(type) }
            HorizontalDivider()
        }
        FilterRow(label = s.filterDownloaded, checked = downloadedSelected, onClick = onToggleDownloaded)
    }
}
