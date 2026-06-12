package com.komgareader.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.komgareader.ui.slots.TileState
import com.komgareader.ui.theme.LocalDesignTokens

/**
 * Aurora-Card-Kachel (`tiles`-Slot): wie [DefaultSeriesTile], aber als erhabene Card — Eckradius aus
 * [LocalDesignTokens] (16dp), Tiefe über Schatten (`usesShadows`). Wird nur im Smartphone-Modus (LCD)
 * eingespeist, daher reiner LCD-Card-Look. Inhalt (Cover/Badge/Titel) teilt sie sich mit
 * [DefaultSeriesTile] über [TileCoverContent] — hier unterscheidet sich **nur der Rahmen**.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AuroraSeriesTile(state: TileState, modifier: Modifier = Modifier) {
    val tokens = LocalDesignTokens.current
    val shape = RoundedCornerShape(tokens.cornerRadius)
    Box(
        modifier
            .aspectRatio(2f / 3f)
            .shadow(if (tokens.usesShadows) tokens.cardElevation + 2.dp else 0.dp, shape, clip = false)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .combinedClickable(onClick = state.onClick, onLongClick = state.onLongClick),
    ) {
        TileCoverContent(state)
    }
}
