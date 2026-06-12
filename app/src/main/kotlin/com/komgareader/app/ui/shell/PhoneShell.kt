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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.komgareader.app.ui.components.LocalEinkMode
import com.komgareader.app.ui.icons.AppIcons
import com.komgareader.app.ui.slots.LocalResolvedSlots
import kotlinx.coroutines.launch

/**
 * Swap-Beweis-Built-in für den compact Form-Faktor: dasselbe Können, fundamental andere Anordnung —
 * Nav als Drawer (Burger oben links) statt Bottom-Bar. Konsumiert DIESELBE [AppShellState] wie
 * [DefaultShell], rendert dieselben host-gebauten header/content-Stücke. Kein Core-Code wird berührt.
 * E-Ink-Invarianten bleiben host-erzwungen: das Drawer-Öffnen/Schließen ist über [LocalEinkMode]
 * gegatet (auf E-Ink sofortiger `snapTo` statt Slide-Animation, `animation-gating.md`).
 */
@OptIn(ExperimentalMaterial3Api::class)
object PhoneShell : ShellPack {
    @Composable
    override fun Render(state: AppShellState) {
        val slots = LocalResolvedSlots.current
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
}
