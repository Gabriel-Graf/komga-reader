package com.komgareader.ui.slots

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntOffset

/**
 * Die **Capability-Surface** des Home-Headers: ein benannter Satz Fähigkeiten + Callbacks, den der
 * Host (Core) baut und ein [HomeHeaderSlot] (Pack) **arrangiert** — nie
 * neu implementiert („UI neu, Kernlogik gleich"). ABI-fähig geschnitten; E-Ink-Invarianten bleiben
 * host-erzwungen (`LocalDisplayBehavior`/`LocalDesignTokens`), sind NICHT Teil der Surface.
 */
data class HomeHeaderState(
    val status: @Composable () -> Unit,
    /** Titel der aktiven Destination — Orientierung im Drawer-Modus (neben dem Burger). Bottom-Bar zeigt
     *  stattdessen den [status]-Cluster und ignoriert ihn. Leer = kein Titel. */
    val title: String = "",
    val search: HomeHeaderSearch,
    val filter: HomeHeaderFilter?,
    val menu: @Composable () -> Unit,
    val actions: @Composable RowScope.() -> Unit,
)

/** Such-Fähigkeit: Text + Callbacks + optionaler `leading`-Inhalt (z. B. Library-Filter-Chips). */
data class HomeHeaderSearch(
    val query: String,
    val onQueryChange: (String) -> Unit,
    val onSubmit: () -> Unit,
    val placeholder: String,
    val actionLabel: String,
    val clearLabel: String?,
    val onClear: (() -> Unit)?,
    val leading: (@Composable RowScope.() -> Unit)?,
)

/** Generischer Filter-Icon-Slot — Library UND Plugins teilen ihn (DRY). Das zugehörige Menü liefert
 *  der Host über [HomeHeaderState.menu]; hier nur Icon + Anchor + Klick. */
data class HomeHeaderFilter(
    val icon: ImageVector,
    val contentDescription: String,
    val onClick: () -> Unit,
    val onAnchor: (IntOffset) -> Unit,
)
