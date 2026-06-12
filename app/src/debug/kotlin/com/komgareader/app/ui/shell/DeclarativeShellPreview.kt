package com.komgareader.app.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.komgareader.app.ui.theme.KomgaReaderTheme
import com.komgareader.ui.icons.AppIcons
import com.komgareader.ui.shell.AppShellState
import com.komgareader.ui.shell.ShellDescriptor
import com.komgareader.ui.shell.ShellDestination
import com.komgareader.ui.shell.ShellDestinationId
import com.komgareader.ui.shell.ShellNavStyle

/**
 * Swap-Beweis: **dieselbe** [AppShellState] wird einmal als Drawer- und einmal als Bottom-Bar-Skelett
 * gerendert — allein der [ShellDescriptor] schaltet das Skelett. Belegt den 1→3-Pfad in-tree: ein
 * externer Pack (L2) müsste nur den Deskriptor liefern, der Renderer ([DeclarativeShell]) bleibt.
 * NUR Debug/Preview, keine Nutzer-Einstellung.
 */
private fun previewShellState(): AppShellState {
    val destinations = listOf(
        ShellDestination(ShellDestinationId.LIBRARY, AppIcons.Library, "Bibliothek", header = null) {
            Box(Modifier.fillMaxSize()) { Text("Bibliothek-Inhalt", Modifier.padding(16.dp)) }
        },
        ShellDestination(ShellDestinationId.GROUPS, AppIcons.Groups, "Gruppen", header = null) {
            Box(Modifier.fillMaxSize()) { Text("Gruppen-Inhalt", Modifier.padding(16.dp)) }
        },
    )
    return AppShellState(
        destinations = destinations,
        selectedId = ShellDestinationId.LIBRARY,
        onSelect = {},
    )
}

@Preview(name = "DeclarativeShell — Drawer vs. Bottom-Bar", widthDp = 800, heightDp = 480)
@Composable
private fun DeclarativeShellSwapPreview() {
    KomgaReaderTheme {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxSize()) {
                DeclarativeShell(ShellDescriptor(ShellNavStyle.DRAWER)).Render(previewShellState())
            }
            Box(Modifier.weight(1f).fillMaxSize()) {
                DeclarativeShell(ShellDescriptor(ShellNavStyle.BOTTOM_BAR)).Render(previewShellState())
            }
        }
    }
}
