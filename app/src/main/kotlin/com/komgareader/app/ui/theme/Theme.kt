package com.komgareader.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.komgareader.app.ui.components.LocalDisplayBehavior

enum class ThemeMode { LIGHT, DARK, SYSTEM }

/**
 * Host-Theme: wählt das [UiPack] der aktuellen Geräteklasse ([LocalDisplayBehavior] → [packFor]) und
 * rendert dessen volles Schema/Tokens/Shapes/Typo. Die drei Plattformen (mono E-Ink · Kaleido · LCD)
 * sind drei Packs hinter einer Naht — kein Fork. Bewegung/Schatten bleiben über die Achsen
 * host-erzwungen; das Pack liefert nur den Look. Default mono = reiner E-Ink-Look auf dem Zielgerät.
 */
@Composable
fun KomgaReaderTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val pack = UiPackRegistry.forBehavior(LocalDisplayBehavior.current)
    MaterialTheme(
        colorScheme = pack.colorScheme(dark),
        shapes = pack.shapes,
        typography = pack.typography,
    ) {
        CompositionLocalProvider(
            LocalUiPack provides pack,
            LocalDesignTokens provides pack.designTokens(dark),
            content = content,
        )
    }
}
