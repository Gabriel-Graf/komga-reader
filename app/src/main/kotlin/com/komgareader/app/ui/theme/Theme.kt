package com.komgareader.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.komgareader.app.ui.components.LocalDisplayBehavior
import com.komgareader.app.ui.pack.TokenOverride
import com.komgareader.app.ui.slots.resolveSlots
import com.komgareader.ui.slots.LocalResolvedSlots
import com.komgareader.ui.slots.UiSlotPack
import com.komgareader.ui.theme.LocalDesignTokens
import com.komgareader.ui.theme.LocalUiPack
import com.komgareader.ui.theme.UiPackRegistry

enum class ThemeMode { LIGHT, DARK, SYSTEM }

/**
 * Host-Theme: wählt das [UiPack] der aktuellen Geräteklasse ([LocalDisplayBehavior] → [packFor]) und
 * rendert dessen volles Schema/Tokens/Shapes/Typo. Die drei Plattformen (mono E-Ink · Kaleido · LCD)
 * sind drei Packs hinter einer Naht — kein Fork. Bewegung/Schatten bleiben über die Achsen
 * host-erzwungen; das Pack liefert nur den Look. Default mono = reiner E-Ink-Look auf dem Zielgerät.
 */
@Composable
fun KomgaReaderTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    slotPack: UiSlotPack = UiSlotPack(),
    tokenOverride: TokenOverride? = null,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val behavior = LocalDisplayBehavior.current
    val pack = UiPackRegistry.forBehavior(behavior)
    // Token-Override eines externen UI-Packs (L2) auf die geräteklassen-gewählten Tokens anwenden:
    // Eckradius gilt immer (invariant-neutral); der **Akzent** ist E-Ink-host-erzwungen — nur wenn die
    // Geräteklasse Akzentfarbe erlaubt (mono E-Ink ignoriert ihn, bleibt Schwarz). onAccent bleibt vom Pack.
    val baseTokens = pack.designTokens(dark)
    val tokens = tokenOverride?.let { o ->
        baseTokens.copy(
            accent = if (behavior.allowsAccentColor) o.accent ?: baseTokens.accent else baseTokens.accent,
            cornerRadius = o.cornerRadius ?: baseTokens.cornerRadius,
        )
    } ?: baseTokens
    MaterialTheme(
        colorScheme = pack.colorScheme(dark),
        shapes = pack.shapes,
        typography = pack.typography,
    ) {
        CompositionLocalProvider(
            LocalUiPack provides pack,
            LocalDesignTokens provides tokens,
            // Slot-Naht: das aktive Slot-Pack auflösen (fehlende Regionen → Default). Standard ist
            // das mitgelieferte Pack; ein alternatives Pack ([slotPack]) ersetzt einzelne Regionen,
            // ohne dass die Consumer (Call-Sites unten) sich ändern. Bewegung/Akzent bleiben über
            // die obigen Locals **host-erzwungen** — ein Slot liefert nur Inhalt, nie die Policy.
            LocalResolvedSlots provides resolveSlots(slotPack),
            content = content,
        )
    }
}
