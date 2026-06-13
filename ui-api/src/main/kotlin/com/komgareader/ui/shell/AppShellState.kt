package com.komgareader.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.komgareader.ui.slots.HomeHeaderState

/** Stabile, geräteunabhängige Identität einer Home-Destination. Die Reihenfolge in [AppShellState.destinations]
 *  bestimmt die Anzeige-Reihenfolge; ein Pack ordnet sie an, ändert die Menge aber nie. */
enum class ShellDestinationId { LIBRARY, COLLECTIONS, GROUPS, PLUGINS, SETTINGS }

/**
 * Eine Home-Destination als **benanntes, einzeln renderbares Stück**. `icon`/`label`/`id` sind reine
 * Nav-Daten (das Pack baut daraus sein Nav-Control — die Widget-Wahl IST die Variabilität). `header`
 * (schon gebaute Surface) und `content` (host-gebauter Screen mit voller Logik) werden vom Pack nur
 * platziert, nie nachgebaut. Vgl. `HomeHeaderState` — dieselbe Form eine Ebene höher.
 */
data class ShellDestination(
    val id: ShellDestinationId,
    val icon: ImageVector,
    val label: String,
    /** null = diese Destination bringt eigene Bar / will keinen Shell-Header (z. B. Collections-Detail). */
    val header: HomeHeaderState?,
    /** true = small "dirty" dot on the nav icon (e.g. update available on the settings tab). The pack
     *  renders the dot; its meaning is host-determined (pure nav data). */
    val badge: Boolean = false,
    val content: @Composable () -> Unit,
)

/**
 * Die Capability-Surface des Home-Skeletts. Der Host (Core) baut sie; ein [ShellPack] arrangiert sie.
 * **Satz benannter Stücke, kein opaker Blob** — das trägt den 1→3-Pfad (deklarativer Deskriptor später).
 * E-Ink-Invarianten (Bewegung/Akzent) sind NICHT Teil der Surface — host-erzwungen.
 */
data class AppShellState(
    val destinations: List<ShellDestination>,
    val selectedId: ShellDestinationId,
    val onSelect: (ShellDestinationId) -> Unit,
    /** Optional, host-built overlay centered BELOW the toolbar (above the content) — e.g. the
     *  4-second update message on app start. null = no overlay. The host owns motion/visibility
     *  (E-Ink invariant host-enforced); the pack only places it. */
    val banner: (@Composable () -> Unit)? = null,
) {
    val selected: ShellDestination
        get() = destinations.firstOrNull { it.id == selectedId }
            ?: error("ShellDestination $selectedId nicht in destinations (${destinations.map { it.id }})")
}
