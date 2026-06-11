package com.komgareader.app.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.komgareader.app.ui.components.EinkSearchBar

/**
 * Die **Capability-Surface** des Home-Headers: ein benannter Satz Fähigkeiten + Callbacks, den der
 * Host (Core) baut und ein [com.komgareader.app.ui.slots.HomeHeaderSlot] (Pack) **arrangiert** — nie
 * neu implementiert („UI neu, Kernlogik gleich"). ABI-fähig geschnitten; E-Ink-Invarianten bleiben
 * host-erzwungen (`LocalDisplayBehavior`/`LocalDesignTokens`), sind NICHT Teil der Surface.
 */
data class HomeHeaderState(
    val status: @Composable () -> Unit,
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

/**
 * Das mitgelieferte Default-Layout (Onyx-Look): StatusCluster links · zentrierte Suche (max 408dp) ·
 * 40dp-Filter-Icon-Slot · Rechts-Aktionen · Menü-Overlay. Verhaltensgleich zum bisherigen
 * inline-`TopAppBar`-Block in HomeScreen — der `TopAppBar`-Wrapper trägt die gewohnte Bar-Höhe und
 * das Title-Inset (~16dp links), damit der StatusCluster nicht am Display-Rand klebt und der obere
 * Abstand über alle Tabs gleich ist.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefaultHomeHeader(state: HomeHeaderState) {
    TopAppBar(title = {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.align(Alignment.CenterStart)) { state.status() }
        Row(
            Modifier.align(Alignment.Center).fillMaxWidth(0.62f).widthIn(max = 408.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EinkSearchBar(
                query = state.search.query,
                onQueryChange = state.search.onQueryChange,
                onSubmit = state.search.onSubmit,
                placeholder = state.search.placeholder,
                actionLabel = state.search.actionLabel,
                clearLabel = state.search.clearLabel,
                onClear = state.search.onClear,
                leading = state.search.leading,
                modifier = Modifier.weight(1f),
            )
            // Filter-Slot: feste Breite auf ALLEN Tabs (Suchfeld überall gleich breit). Nur gefüllt,
            // wenn der Tab eine Filter-Capability liefert.
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                state.filter?.let { f ->
                    IconButton(
                        onClick = f.onClick,
                        modifier = Modifier.onGloballyPositioned { coords ->
                            val pos = coords.positionInWindow()
                            f.onAnchor(
                                IntOffset(
                                    (pos.x + coords.size.width).toInt(),
                                    (pos.y + coords.size.height).toInt(),
                                ),
                            )
                        },
                    ) {
                        Icon(f.icon, contentDescription = f.contentDescription)
                    }
                }
            }
        }
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
            state.actions(this)
        }
        state.menu()
    }
    })
}
