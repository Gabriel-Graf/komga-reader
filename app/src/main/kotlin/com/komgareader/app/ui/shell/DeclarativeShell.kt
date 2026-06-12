package com.komgareader.app.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import com.komgareader.app.ui.components.BottomNavItem
import com.komgareader.app.ui.components.EinkBottomBar
import com.komgareader.app.ui.components.LocalContentBottomInset
import com.komgareader.app.ui.components.LocalEinkMode
import com.komgareader.ui.icons.AppIcons
import com.komgareader.ui.shell.AppShellState
import com.komgareader.ui.shell.ShellDescriptor
import com.komgareader.ui.shell.ShellNavStyle
import com.komgareader.ui.shell.ShellPack
import com.komgareader.ui.slots.LocalResolvedSlots
import com.komgareader.ui.theme.LocalDesignTokens
import kotlinx.coroutines.launch

/**
 * Die EINE host-eigene Shell: interpretiert einen [ShellDescriptor] (Daten) und rendert die host-gebauten
 * [AppShellState]-Stücke (header/content/nav) im vom Deskriptor benannten Skelett. Ersetzt die früheren
 * bespoke Built-ins DefaultShell/PhoneShell — dieselbe Anordnung, jetzt deskriptor-geschaltet. Das ist
 * der In-Tree-Beleg des 1→3-Pfads: ein externer Pack (L2) liefert nur den Deskriptor, dieser Renderer bleibt.
 * E-Ink-Invarianten (Drawer-snapTo statt Slide) bleiben host-erzwungen.
 */
class DeclarativeShell(val descriptor: ShellDescriptor) : ShellPack {
    @Composable
    override fun Render(state: AppShellState) = when (descriptor.navStyle) {
        ShellNavStyle.BOTTOM_BAR -> BottomBarShell(state)
        ShellNavStyle.DRAWER -> DrawerShell(state)
        ShellNavStyle.FLOATING_NAV -> FloatingNavShell(state)
    }
}

/**
 * Geteiltes Overlay-Bar-Skelett: persistenter Home-Header oben (homeHeader-Slot, nur wenn die aktive
 * Destination einen liefert), Inhalt der aktiven Destination dahinter, eine schwebende Bar als Overlay
 * unten. Die konkrete Bar kommt als [bar]-Slot — so teilen sich [BottomBarShell] und [FloatingNavShell]
 * das Gerüst (Inset-Mechanik via `onSizeChanged`/[LocalContentBottomInset]) an EINER Stelle
 * (`shared-structure-before-variants`). Ordnet NUR an — keine Tab-/VM-/Dialog-Logik (die besitzt der
 * Host HomeShellHost).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverlayBarShell(
    state: AppShellState,
    bar: @Composable (items: List<BottomNavItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier) -> Unit,
) {
    val slots = LocalResolvedSlots.current
    Scaffold(
        topBar = { state.selected.header?.let { slots.homeHeader(it) } },
    ) { inner ->
        var barHeightPx by remember { mutableIntStateOf(0) }
        val barInset = with(LocalDensity.current) { barHeightPx.toDp() }
        Box(Modifier.fillMaxSize().padding(inner)) {
            CompositionLocalProvider(LocalContentBottomInset provides barInset) {
                Box(Modifier.fillMaxSize()) { state.selected.content() }
            }
            bar(
                state.destinations.map { BottomNavItem(it.icon, it.label) },
                state.destinations.indexOfFirst { it.id == state.selectedId },
                { idx -> state.onSelect(state.destinations[idx].id) },
                Modifier.align(Alignment.BottomCenter).onSizeChanged { barHeightPx = it.height },
            )
        }
    }
}

/** Mitgeliefertes E-Ink/Tablet-Skelett: schwebende [EinkBottomBar] als Overlay-Bar (s. [OverlayBarShell]). */
@Composable
private fun BottomBarShell(state: AppShellState) =
    OverlayBarShell(state) { items, selected, onSelect, modifier ->
        EinkBottomBar(items = items, selectedIndex = selected, onSelect = onSelect, modifier = modifier)
    }

/** FLOATING_NAV-Skelett: schwebende Pill-Nav [FloatingNavBar] als Overlay-Bar (s. [OverlayBarShell]). */
@Composable
private fun FloatingNavShell(state: AppShellState) =
    OverlayBarShell(state) { items, selected, onSelect, modifier ->
        FloatingNavBar(items = items, selectedIndex = selected, onSelect = onSelect, modifier = modifier)
    }

/**
 * Swap-Beweis-Skelett für den compact Form-Faktor: dasselbe Können, fundamental andere Anordnung —
 * Nav als Drawer (Burger oben links) statt Bottom-Bar. Konsumiert DIESELBE [AppShellState] wie
 * [BottomBarShell], rendert dieselben host-gebauten header/content-Stücke. Kein Core-Code wird berührt.
 * E-Ink-Invarianten bleiben host-erzwungen: das Drawer-Öffnen/Schließen ist über [LocalEinkMode]
 * gegatet (auf E-Ink sofortiger `snapTo` statt Slide-Animation, `animation-gating.md`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DrawerShell(state: AppShellState) {
    val slots = LocalResolvedSlots.current
    val tokens = LocalDesignTokens.current
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    // Animation-Gating: auf E-Ink kein Slide — sofortiger Zustandswechsel (snapTo).
    val eink = LocalEinkMode.current
    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet {
                state.destinations.forEach { d ->
                    NavigationDrawerItem(
                        icon = { Icon(d.icon, contentDescription = null) },
                        label = { Text(d.label) },
                        selected = d.id == state.selectedId,
                        // Aktive Zeile folgt den Host-Mono-Tokens (konsistent mit EinkBottomBar) —
                        // E-Ink: schwarzer Hintergrund/weißer Text; Kaleido/LCD: Akzent.
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = tokens.accent,
                            selectedIconColor = tokens.onAccent,
                            selectedTextColor = tokens.onAccent,
                        ),
                        onClick = {
                            state.onSelect(d.id)
                            scope.launch { if (eink) drawer.snapTo(DrawerValue.Closed) else drawer.close() }
                        },
                    )
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = { Text(state.selected.label) },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { if (eink) drawer.snapTo(DrawerValue.Open) else drawer.open() } }) {
                                Icon(AppIcons.ListView, contentDescription = null)
                            }
                        },
                    )
                    state.selected.header?.let { slots.homeHeader(it) }
                }
            },
        ) { inner ->
            Box(Modifier.fillMaxSize().padding(inner)) { state.selected.content() }
        }
    }
}
