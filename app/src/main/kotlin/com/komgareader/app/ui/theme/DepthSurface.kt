package com.komgareader.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

/**
 * Tiefe geräteklassen-gerecht: auf **LCD** über Schatten (Elevation), auf **E-Ink** (mono **und**
 * Kaleido) über einen Hairline-Border — denn Schatten ghosten auf E-Ink. Die Entscheidung liegt in
 * den [DesignTokens] (`usesShadows`), nicht im Aufrufer; so muss kein Baustein ein `isEink` kennen.
 *
 * Ein Composable-Modifier (liest [LocalDesignTokens]), nutzbar an jeder Karten-/Tile-/Surface-Fläche:
 * `Modifier.depthSurface(shape)`. Ersetzt das verstreute `clip + background + border`-Muster und
 * macht denselben Baustein über alle drei Plattformen korrekt.
 */
@Composable
fun Modifier.depthSurface(
    shape: Shape,
    background: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
): Modifier {
    val tokens = LocalDesignTokens.current
    return if (tokens.usesShadows) {
        this
            .shadow(tokens.cardElevation, shape)
            .clip(shape)
            .background(background)
    } else {
        this
            .clip(shape)
            .background(background)
            .border(EinkTokens.hairline, borderColor, shape)
    }
}
