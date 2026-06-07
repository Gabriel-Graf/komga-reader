package com.komgareader.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import com.komgareader.domain.model.ContentType

/**
 * Nicht-modales Multi-Select-Menü zum Filtern nach Werk-Typ (kein „Auto" — das ist ein
 * Filter, keine Zuweisung). Tippen toggelt einen Typ, das Menü bleibt offen; Häkchen
 * markiert aktive Typen. Klappt unter dem Filter-Icon oben rechts nach links auf.
 */
@Composable
fun TypeFilterMenu(
    anchor: IntOffset,
    selected: Set<ContentType>,
    onToggle: (ContentType) -> Unit,
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
        options.forEachIndexed { index, type ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onToggle(type) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    s.localizedContentType(type),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (type in selected) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            if (index < options.lastIndex) HorizontalDivider()
        }
    }
}
