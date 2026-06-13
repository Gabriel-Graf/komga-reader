package com.komgareader.app.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.BadgeDot
import com.komgareader.app.ui.components.BottomNavItem
import com.komgareader.app.ui.components.EinkBottomBar
import com.komgareader.app.ui.components.LocalContentBottomInset
import com.komgareader.app.ui.components.LocalEinkMode
import com.komgareader.app.ui.home.LocalDrawerToggle
import com.komgareader.ui.icons.AppIcons
import com.komgareader.ui.shell.AppShellState
import com.komgareader.ui.shell.ShellDescriptor
import com.komgareader.ui.shell.ShellNavStyle
import com.komgareader.ui.shell.ShellPack
import com.komgareader.ui.slots.LocalResolvedSlots
import com.komgareader.ui.theme.EinkTokens
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
                ShellContent(state)
            }
            bar(
                state.destinations.map { BottomNavItem(it.icon, it.label, it.badge) },
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
 * Nav als Drawer statt Bottom-Bar. Konsumiert DIESELBE [AppShellState] wie [BottomBarShell], rendert
 * dieselben host-gebauten header/content-Stücke. **Eine** Topbar (der bestehende homeHeader) — keine
 * zweite mehr: der Burger lebt im Header, die Schublade wird über [LocalDrawerToggle] geöffnet (so
 * weichen Uhrzeit/Akku dem Burger, und die Suche wird zur Lupe→zentriert, s. [DefaultHomeHeader]).
 * E-Ink-Invarianten bleiben host-erzwungen: Öffnen/Schließen ist über [LocalEinkMode] gegatet
 * (auf E-Ink sofortiger `snapTo` statt Slide-Animation, `animation-gating.md`).
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
    // Öffnen-Aktion für den Burger im Header (statt einer eigenen zweiten TopAppBar).
    val openDrawer: () -> Unit = { scope.launch { if (eink) drawer.snapTo(DrawerValue.Open) else drawer.open() } }
    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet {
                // Drawer-Kopf: Logo + App-Name + Hairline-Trenner — die Tabs kleben nicht mehr am Rand.
                val s = LocalStrings.current
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AppIcons.Library, contentDescription = null, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        s.appName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                HorizontalDivider(
                    thickness = EinkTokens.hairline,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                Spacer(Modifier.height(8.dp))
                state.destinations.forEach { d ->
                    NavigationDrawerItem(
                        icon = {
                            Box {
                                Icon(d.icon, contentDescription = null)
                                if (d.badge) {
                                    BadgeDot(Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-2).dp))
                                }
                            }
                        },
                        label = { Text(d.label) },
                        selected = d.id == state.selectedId,
                        // Weniger rund als der Material-Default-Pill (28 dp) — passt zur Theme-Formsprache.
                        shape = MaterialTheme.shapes.small,
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
        // EINE Topbar: nur der bestehende homeHeader. Der Burger lebt darin — der DrawerShell stellt
        // bloß die Öffnen-Aktion über [LocalDrawerToggle] bereit; [DefaultHomeHeader] rendert ihn links.
        CompositionLocalProvider(LocalDrawerToggle provides openDrawer) {
            Scaffold(
                topBar = { state.selected.header?.let { slots.homeHeader(it) } },
            ) { inner ->
                Box(Modifier.fillMaxSize().padding(inner)) { ShellContent(state) }
            }
        }
    }
}

/**
 * Inhalt der aktiven Destination — geteilt von allen Skeletten (`shared-structure-before-variants`).
 * **Smartphone/LCD** (`allowsMotion`): ein [HorizontalPager] über ALLE Destinations macht den ganzen
 * Inhalt zwischen den Tabs wischbar (Tab-Tap ⇄ Wisch-Geste bleiben synchron). **E-Ink** (`!allowsMotion`):
 * KEIN Pager — direktes Rendern der aktiven Destination, weil jede Wisch-Bewegung Ghosting/Teil-Refresh
 * erzeugt (`animation-gating.md`). Die E-Ink-Invariante ist damit host-erzwungen, nicht pack-abhängig.
 */
@Composable
private fun ShellContent(state: AppShellState) {
    Box(Modifier.fillMaxSize()) {
        if (LocalEinkMode.current) {
            state.selected.content()
        } else {
            val targetIndex = state.destinations.indexOfFirst { it.id == state.selectedId }.coerceAtLeast(0)
            val pagerState = rememberPagerState(initialPage = targetIndex) { state.destinations.size }
            // Tab-Tap (selectedId ändert sich) → Pager nachziehen.
            LaunchedEffect(state.selectedId) {
                val to = state.destinations.indexOfFirst { it.id == state.selectedId }.coerceAtLeast(0)
                if (pagerState.currentPage != to) pagerState.animateScrollToPage(to)
            }
            // Wisch-Geste settled → selectedId nachziehen (idempotent: nur bei echtem Wechsel, kein Loop).
            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.settledPage }.collect { page ->
                    val id = state.destinations[page].id
                    if (id != state.selectedId) state.onSelect(id)
                }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                Box(Modifier.fillMaxSize()) { state.destinations[page].content() }
            }
        }
        // Host-built banner centered BELOW the toolbar (above the content). The host owns
        // visibility/motion (E-Ink invariant host-enforced); the shell only places it.
        state.banner?.let { banner ->
            Box(Modifier.align(Alignment.TopCenter)) { banner() }
        }
    }
}
