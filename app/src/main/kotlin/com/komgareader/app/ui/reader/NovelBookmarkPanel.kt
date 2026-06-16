package com.komgareader.app.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.domain.model.NovelBookmark
import com.komgareader.ui.icons.AppIcons

/**
 * Lists the novel's bookmarks (#number · optional name · snippet) as a frameless list — for the
 * settings bottom sheet's bookmarks tab (mirrors [NovelTocList], no dialog frame:
 * shared-structure-before-variants). Tap a row to jump to the bookmark's xpointer (then
 * [onJumped] lets the caller close the sheet); the trailing buttons rename and delete.
 *
 * **No animation** (`animation-gating`), monochrome. Texts via [LocalStrings] (DE+EN).
 */
@Composable
fun NovelBookmarkList(
    bookmarks: List<NovelBookmark>,
    onJump: (String) -> Unit,
    onRename: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onJumped: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current

    if (bookmarks.isEmpty()) {
        Text(
            strings.novelBookmarksEmpty,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }

    Column(modifier.fillMaxWidth()) {
        bookmarks.forEach { bookmark ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        onJump(bookmark.xpointer)
                        onJumped()
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = strings.novelBookmarkNumber(bookmark.number),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(Modifier.weight(1f)) {
                    if (!bookmark.label.isNullOrBlank()) {
                        Text(
                            text = bookmark.label!!,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = bookmark.snippet,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { onRename(bookmark.id) }) {
                    Icon(AppIcons.Edit, contentDescription = strings.novelBookmarkRename)
                }
                IconButton(onClick = { onDelete(bookmark.id) }) {
                    Icon(AppIcons.Delete, contentDescription = strings.novelBookmarkDelete)
                }
            }
        }
    }
}
