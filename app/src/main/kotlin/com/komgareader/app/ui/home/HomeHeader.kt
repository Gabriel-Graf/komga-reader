package com.komgareader.app.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.komgareader.app.ui.components.EinkSearchBar
import com.komgareader.ui.icons.AppIcons
import com.komgareader.ui.slots.HomeHeaderState

/**
 * Im **Drawer-Shell-Modus** gesetzt (sonst `null`): öffnet die Navigations-Schublade. Ist er gesetzt,
 * zeigt [DefaultHomeHeader] einen **Burger links** (statt StatusCluster) + eine **rechtsbündige Lupe**,
 * die zur zentrierten Suche aufklappt — sonst (BottomBar-Modus) den gewohnten Status + Inline-Suche.
 * App-intern (kein ui-api-Vertrag): der `DrawerShell` stellt ihn bereit, der Header liest ihn. So bleibt
 * **eine** Topbar (der Drawer baut keine zweite mehr), und Uhrzeit/Akku weichen dem Burger.
 */
val LocalDrawerToggle = staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * Mitgeliefertes Default-Layout (Onyx-Look) — zwei Anordnungen, je nach Shell:
 * - **BottomBar-Modus** (Default, `LocalDrawerToggle == null`): StatusCluster links · zentrierte
 *   Inline-Suche (max 408 dp) · Filter · Rechts-Aktionen · Menü-Overlay. Verhaltensgleich wie bisher.
 * - **Drawer-Modus** (`LocalDrawerToggle != null`): **Burger links** (öffnet die Schublade, ersetzt
 *   Uhrzeit/Akku) · rechts **Aktionen + Filter + Lupe**; die Lupe klappt zur **zentrierten** Suche auf
 *   (wie die Sammlungen-Suche). Eine Topbar statt zwei — der Drawer baut keine eigene mehr.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefaultHomeHeader(state: HomeHeaderState) {
    val drawerToggle = LocalDrawerToggle.current
    if (drawerToggle != null) DrawerModeHeader(state, drawerToggle) else BottomBarHeader(state)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BottomBarHeader(state: HomeHeaderState) {
    TopAppBar(title = {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.align(Alignment.CenterStart)) { state.status() }
            Row(
                Modifier.align(Alignment.Center).fillMaxWidth(0.62f).widthIn(max = 408.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeaderSearchField(state, Modifier.weight(1f))
                FilterSlot(state)
            }
            Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                state.actions(this)
            }
            state.menu()
        }
    })
}

/**
 * Drawer-Modus: Burger links statt Status; rechts Aktionen + Filter + Lupe. Tap auf die Lupe öffnet die
 * **zentrierte** Suche (lokaler [searchOpen]-State, analog zur Sammlungen-Suche); Schließen → zurück zur
 * Lupe (und Such-Query geleert).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DrawerModeHeader(state: HomeHeaderState, onMenu: () -> Unit) {
    var searchOpen by remember { mutableStateOf(false) }
    TopAppBar(title = {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (searchOpen) {
                Row(
                    Modifier.align(Alignment.Center).fillMaxWidth().widthIn(max = 560.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HeaderSearchField(state, Modifier.weight(1f))
                    // Filter gehört zur aktiven Suche — zwischen Suchfeld und Schließen-X (statt verdeckt).
                    FilterSlot(state)
                    IconButton(onClick = {
                        state.search.onClear?.invoke()
                        searchOpen = false
                    }) {
                        Icon(AppIcons.Close, contentDescription = null)
                    }
                }
            } else {
                // EINE Row über die ganze Breite: Burger + Titel (mit `weight` → der Titel truncatet,
                // statt von den rechten Aktionen/der Suche überdeckt zu werden), rechts Aktionen + Lupe.
                // Ohne aktive Suche KEIN Filter (der erscheint erst mit der Suche, s. o.).
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onMenu) { Icon(AppIcons.ListView, contentDescription = null) }
                    if (state.title.isNotEmpty()) {
                        Text(
                            state.title,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(start = 4.dp, end = 8.dp),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    state.actions(this)
                    IconButton(onClick = { searchOpen = true }) {
                        Icon(AppIcons.Search, contentDescription = state.search.placeholder)
                    }
                }
            }
            state.menu()
        }
    })
}

/** Die geteilte Suchzeile beider Modi (zieht alle Felder aus [HomeHeaderState.search]). */
@Composable
private fun HeaderSearchField(state: HomeHeaderState, modifier: Modifier) {
    EinkSearchBar(
        query = state.search.query,
        onQueryChange = state.search.onQueryChange,
        onSubmit = state.search.onSubmit,
        placeholder = state.search.placeholder,
        actionLabel = state.search.actionLabel,
        clearLabel = state.search.clearLabel,
        onClear = state.search.onClear,
        leading = state.search.leading,
        modifier = modifier,
    )
}

/** Filter-Icon-Slot mit fester Breite (40 dp) + Anker-Messung fürs Filter-Overlay; leer ohne Filter-Capability. */
@Composable
private fun FilterSlot(state: HomeHeaderState) {
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
