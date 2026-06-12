package com.komgareader.app.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import com.komgareader.app.ui.components.BottomNavItem
import com.komgareader.app.ui.components.EinkBottomBar
import com.komgareader.app.ui.components.LocalContentBottomInset
import com.komgareader.ui.shell.AppShellState
import com.komgareader.ui.shell.ShellPack
import com.komgareader.ui.slots.LocalResolvedSlots

/**
 * Mitgeliefertes E-Ink/Tablet-Skelett: persistenter Home-Header oben (über den homeHeader-Slot, nur
 * wenn die aktive Destination einen liefert), Inhalt der aktiven Destination dahinter, schwebende
 * [EinkBottomBar] als Overlay unten. Verhaltens- und pixelgleich zum bisherigen HomeScreen-Scaffold.
 * Ordnet NUR an — keine Tab-/VM-/Dialog-Logik (die besitzt der Host HomeShellHost).
 */
@OptIn(ExperimentalMaterial3Api::class)
object DefaultShell : ShellPack {
    @Composable
    override fun Render(state: AppShellState) {
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
                EinkBottomBar(
                    items = state.destinations.map { BottomNavItem(it.icon, it.label) },
                    selectedIndex = state.destinations.indexOfFirst { it.id == state.selectedId },
                    onSelect = { idx -> state.onSelect(state.destinations[idx].id) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .onSizeChanged { barHeightPx = it.height },
                )
            }
        }
    }
}
