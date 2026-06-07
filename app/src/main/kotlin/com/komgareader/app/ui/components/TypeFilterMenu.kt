package com.komgareader.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.i18n.localizedContentType
import com.komgareader.app.ui.icons.AppIcons
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

/** Eine Filter-Zeile: Label links + Häkchen rechts wenn aktiv (E-Ink-ruhig, kein Radio). */
@Composable
private fun FilterRow(label: String, checked: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (checked) {
            Icon(
                AppIcons.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
