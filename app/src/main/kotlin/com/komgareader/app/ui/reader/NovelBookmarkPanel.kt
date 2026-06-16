package com.komgareader.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.AnchoredMenuPopup
import com.komgareader.app.ui.components.CompactIconButton
import com.komgareader.app.ui.components.FilterRow
import com.komgareader.app.ui.components.SegmentOption
import com.komgareader.app.ui.components.SegmentedChoiceRow
import com.komgareader.domain.model.BookmarkMarkerStyle
import com.komgareader.domain.model.NovelBookmark
import com.komgareader.ui.icons.AppIcons
import com.komgareader.ui.theme.EinkTokens
import com.komgareader.ui.theme.LocalDesignTokens

/**
 * Full-height bookmarks panel for the novel-reader settings sheet's bookmarks tab. Owns a **pinned**
 * (non-scrolling) top area — the default-marker selector for *new* bookmarks plus a multi-select
 * action bar — and a scrolling list below. Replaces the former frameless `NovelBookmarkList`.
 *
 * Selection state is **local** to this composable ([selected]): a row's checkbox toggles its id, and
 * tapping a row's text jumps **only when nothing is selected**, so multi-select and jump never fight.
 * The screen owns the colour modal — this panel just emits [onPickColor] with the affected ids.
 *
 * Each bookmark carries its own [NovelBookmark.markerStyle] and [NovelBookmark.color]; the per-row
 * swatch shows that colour, and the pinned selector sets the default for *new* bookmarks only.
 *
 * **No animation** (`animation-gating`). Accents via [LocalDesignTokens] (mono on E-Ink). Texts via
 * [LocalStrings] (DE+EN).
 */
@Composable
fun NovelBookmarkPanel(
    bookmarks: List<NovelBookmark>,
    defaultMarkerStyle: String,
    onDefaultMarkerStyle: (String) -> Unit,
    onJump: (String) -> Unit,
    onJumped: () -> Unit,
    onRename: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onPickColor: (List<Long>) -> Unit,
    onApplyMode: (List<Long>, String) -> Unit,
    onDeleteMany: (List<Long>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val selected = remember { mutableStateListOf<Long>() }

    // Drop selections for bookmarks that disappeared (deleted elsewhere) so the count stays honest.
    val presentIds = remember(bookmarks) { bookmarks.map { it.id }.toSet() }
    selected.retainAll(presentIds)
    val selectedIds = selected.toList()

    Column(modifier.fillMaxSize()) {
        BookmarkDefaultMarkerRow(
            defaultMarkerStyle = defaultMarkerStyle,
            onDefaultMarkerStyle = onDefaultMarkerStyle,
        )
        BookmarkActionBar(
            allIds = presentIds.toList(),
            selectedIds = selectedIds,
            onToggleAll = {
                if (selected.size == presentIds.size) selected.clear()
                else {
                    selected.clear()
                    selected.addAll(presentIds)
                }
            },
            onPickColor = { onPickColor(selectedIds) },
            onApplyMode = { style -> onApplyMode(selectedIds, style) },
            onDeleteMany = {
                onDeleteMany(selectedIds)
                selected.clear()
            },
        )
        Box(
            Modifier
                .fillMaxWidth()
                .size(EinkTokens.hairline)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )

        if (bookmarks.isEmpty()) {
            Text(
                strings.novelBookmarksEmpty,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(bookmarks, key = { it.id }) { bookmark ->
                BookmarkRow(
                    bookmark = bookmark,
                    checked = bookmark.id in selected,
                    selectionActive = selectedIds.isNotEmpty(),
                    onToggle = {
                        if (bookmark.id in selected) selected.remove(bookmark.id)
                        else selected.add(bookmark.id)
                    },
                    onJump = {
                        onJump(bookmark.xpointer)
                        onJumped()
                    },
                    onPickColor = { onPickColor(listOf(bookmark.id)) },
                    onRename = { onRename(bookmark.id) },
                    onDelete = { onDelete(bookmark.id) },
                )
            }
        }
    }
}

/** Pinned row 1: the three marker modes, setting the default for *new* bookmarks only. */
@Composable
private fun BookmarkDefaultMarkerRow(
    defaultMarkerStyle: String,
    onDefaultMarkerStyle: (String) -> Unit,
) {
    val strings = LocalStrings.current
    SegmentedChoiceRow(
        label = strings.novelBookmarkDefaultMarker,
        options = listOf(
            SegmentOption(BookmarkMarkerStyle.UNDERLINE.name, strings.novelBookmarkMarkerUnderline),
            SegmentOption(BookmarkMarkerStyle.FLAG.name, strings.novelBookmarkMarkerFlag),
            SegmentOption(BookmarkMarkerStyle.MARGIN.name, strings.novelBookmarkMarkerMargin),
        ),
        selectedKey = defaultMarkerStyle,
        onSelect = onDefaultMarkerStyle,
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}

/**
 * Pinned row 2: select-all toggle + selected count on the left; Colour / Marker-mode / Delete
 * actions on the right, enabled only when the selection is non-empty.
 */
@Composable
private fun BookmarkActionBar(
    allIds: List<Long>,
    selectedIds: List<Long>,
    onToggleAll: () -> Unit,
    onPickColor: () -> Unit,
    onApplyMode: (String) -> Unit,
    onDeleteMany: () -> Unit,
) {
    val strings = LocalStrings.current
    val hasSelection = selectedIds.isNotEmpty()
    val allSelected = allIds.isNotEmpty() && selectedIds.size == allIds.size

    var markerMenuOpen by remember { mutableStateOf(false) }
    var markerAnchor by remember { mutableStateOf(IntOffset.Zero) }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Select-all checkbox: same accent token as the row checkboxes (one selection signal).
        SelectionCheckbox(
            checked = allSelected,
            enabled = allIds.isNotEmpty(),
            contentDescription = strings.novelBookmarkSelectAll,
            onToggle = onToggleAll,
        )
        Text(
            text = strings.novelBookmarkSelectedCount(selectedIds.size),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
        IconButton(onClick = onPickColor, enabled = hasSelection) {
            Icon(
                AppIcons.Palette,
                contentDescription = strings.novelBookmarkColor,
                tint = actionTint(hasSelection),
            )
        }
        IconButton(
            onClick = { if (hasSelection) markerMenuOpen = true },
            enabled = hasSelection,
            modifier = Modifier.onGloballyPositioned {
                val p = it.positionInWindow()
                markerAnchor = IntOffset((p.x + it.size.width).toInt(), (p.y + it.size.height).toInt())
            },
        ) {
            Icon(
                AppIcons.Bookmark,
                contentDescription = strings.novelBookmarkApplyMode,
                tint = actionTint(hasSelection),
            )
        }
        if (markerMenuOpen) {
            AnchoredMenuPopup(
                anchor = markerAnchor,
                alignEnd = true,
                onDismiss = { markerMenuOpen = false },
            ) {
                MarkerModeMenuItem(BookmarkMarkerStyle.UNDERLINE.name, strings.novelBookmarkMarkerUnderline) {
                    onApplyMode(it); markerMenuOpen = false
                }
                MarkerModeMenuItem(BookmarkMarkerStyle.FLAG.name, strings.novelBookmarkMarkerFlag) {
                    onApplyMode(it); markerMenuOpen = false
                }
                MarkerModeMenuItem(BookmarkMarkerStyle.MARGIN.name, strings.novelBookmarkMarkerMargin) {
                    onApplyMode(it); markerMenuOpen = false
                }
            }
        }
        IconButton(onClick = onDeleteMany, enabled = hasSelection) {
            Icon(
                AppIcons.Delete,
                contentDescription = strings.novelBookmarkDelete,
                tint = actionTint(hasSelection),
            )
        }
    }
}

/** One marker-mode entry in the apply-mode popup (reuses the E-Ink [FilterRow] look, no check). */
@Composable
private fun MarkerModeMenuItem(style: String, label: String, onPick: (String) -> Unit) {
    FilterRow(label = label, checked = false, onClick = { onPick(style) })
}

/**
 * A single scrolling bookmark row — kept deliberately COMPACT (≈half the previous height, request
 * 2026-06-16): ONE text line (the label, or the bookmarked word when unnamed) instead of a
 * label+2-line-snippet stack, and 36dp [CompactIconButton]s instead of the 48dp [IconButton]s that
 * forced the old tall row.
 */
@Composable
private fun BookmarkRow(
    bookmark: NovelBookmark,
    checked: Boolean,
    selectionActive: Boolean,
    onToggle: () -> Unit,
    onJump: () -> Unit,
    onPickColor: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val strings = LocalStrings.current
    // Prefer the user's label; fall back to the captured word so an unnamed bookmark still reads.
    val primary = bookmark.label?.takeIf { it.isNotBlank() } ?: bookmark.snippet
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SelectionCheckbox(
            checked = checked,
            enabled = true,
            contentDescription = strings.novelBookmarkNumber(bookmark.number),
            onToggle = onToggle,
        )
        Text(
            text = strings.novelBookmarkNumber(bookmark.number),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Text-tap jumps only when no multi-selection is active, so the two gestures never fight.
        Text(
            text = primary,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .clickable(enabled = !selectionActive, onClick = onJump),
        )
        ColorSwatch(color = Color(bookmark.color), onClick = onPickColor)
        CompactIconButton(AppIcons.Edit, strings.novelBookmarkRename, onRename)
        CompactIconButton(AppIcons.Delete, strings.novelBookmarkDelete, onDelete)
    }
}

/**
 * Square checkbox in the single accent signal: filled accent + onAccent check when [checked],
 * else a hairline-bordered empty box. No Material `Checkbox` (its grey disabled/ripple is wrong on
 * E-Ink). Reads [LocalDesignTokens] so mono E-Ink draws it black/white, Kaleido/LCD coloured.
 */
@Composable
private fun SelectionCheckbox(
    checked: Boolean,
    enabled: Boolean,
    contentDescription: String,
    onToggle: () -> Unit,
) {
    val tokens = LocalDesignTokens.current
    val shape = RoundedCornerShape(4.dp)
    Box(
        Modifier
            .size(22.dp)
            .clip(shape)
            .then(
                if (checked) Modifier.background(tokens.accent)
                else Modifier.border(EinkTokens.hairline, MaterialTheme.colorScheme.outline, shape),
            )
            .clickable(enabled = enabled, onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                AppIcons.Check,
                contentDescription = contentDescription,
                tint = tokens.onAccent,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** The per-bookmark content-colour swatch; tap opens the colour picker for that one id. */
@Composable
private fun ColorSwatch(color: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        Modifier
            .size(22.dp)
            .clip(shape)
            .background(color)
            .border(EinkTokens.hairline, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick),
    )
}

/** Strong [onSurface] when actionable, muted [onSurfaceVariant] when disabled — readable on E-Ink. */
@Composable
private fun actionTint(enabled: Boolean): Color =
    if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
