package com.komgareader.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.komgareader.ui.theme.LocalDesignTokens

/**
 * Small "dirty" dot (accent-tinted, with a thin surface ring to separate it from the icon). Laid as
 * an overlay over a nav/section icon (e.g. "update available"). Token-driven: E-Ink = black/white,
 * Kaleido/LCD = accent colour — the E-Ink design language is preserved (no Material badge pill).
 */
@Composable
fun BadgeDot(modifier: Modifier = Modifier, size: Dp = 9.dp) {
    val accent = LocalDesignTokens.current.accent
    androidx.compose.foundation.layout.Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(accent)
            .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
    )
}
