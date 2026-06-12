package com.komgareader.app.ui.slots

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.komgareader.app.ui.theme.KomgaReaderTheme
import com.komgareader.ui.icons.AppIcons
import com.komgareader.ui.slots.HeaderSearch
import com.komgareader.ui.slots.HeaderState
import com.komgareader.ui.slots.LocalResolvedSlots
import com.komgareader.ui.slots.UiSlotPack
import com.komgareader.ui.slots.header

/**
 * **Beweis der Slot-Naht — kein Kern-Umbau nötig.** Ein zweites [UiSlotPack] mit einem alternativen
 * Header (zentrierter Titel statt links) wird allein über `KomgaReaderTheme(slotPack = …)` aktiv —
 * der Consumer ([com.komgareader.app.ui.components.SubPageScaffold] u. a.) ruft unverändert
 * `LocalResolvedSlots.current.header(...)` (Kompat-Pfad) und rendert plötzlich den anderen Header.
 *
 * Bewusst nur ein **Debug/Preview-Pfad**, **keine** Nutzer-Einstellung — der Pack-Lader/-Wähler ist
 * Soll (Skins-Plan P2/P3). Die E-Ink-Invarianten bleiben **host-erzwungen**: dieser alternative Slot
 * liefert nur Struktur (Zentrierung), nicht Bewegung/Akzent — die liegen weiter an den Host-Locals.
 */
@OptIn(ExperimentalMaterial3Api::class)
private val CenteredHeaderPack = UiSlotPack(
    // Zentrierter Titel als Alternativ-Struktur (Swap-Beweis). Verhaltensgleich zum Default-Pack:
    // onBack, actions und die optionale Suche werden vollständig übernommen — nur das Titel-Alignment
    // ändert sich. Bewegung/Akzent bleiben host-erzwungen (LocalDisplayBehavior/LocalEinkMode).
    header = { state ->
        TopAppBar(
            title = { Text(state.title, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
            navigationIcon = {
                if (state.onBack != null) {
                    IconButton(onClick = state.onBack!!) { Icon(AppIcons.Back, contentDescription = null) }
                }
            },
            actions = {
                val search = state.search
                if (search != null) {
                    IconButton(onClick = search.onOpen) { Icon(AppIcons.Search, contentDescription = null) }
                }
                state.actions(this)
            },
        )
    },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeaderSlotDemo(slotPack: UiSlotPack) {
    KomgaReaderTheme(slotPack = slotPack) {
        Scaffold(
            topBar = { LocalResolvedSlots.current.header("Serien-Detail", {}) {} },
        ) { padding ->
            Text("Body bleibt unverändert.", modifier = Modifier.padding(padding).fillMaxWidth())
        }
    }
}

/** Default-Pack: Header linksbündig (heutiges Verhalten), suchlos. */
@Preview(name = "Header-Slot · Default (linksbündig)", widthDp = 411, heightDp = 200)
@Composable
private fun HeaderSlotDefaultPreview() {
    HeaderSlotDemo(slotPack = UiSlotPack())
}

/** Alternatives Pack: Header zentriert — gleiche Call-Site, nur das Pack getauscht. */
@Preview(name = "Header-Slot · Alternativ (zentriert)", widthDp = 411, heightDp = 200)
@Composable
private fun HeaderSlotCenteredPreview() {
    HeaderSlotDemo(slotPack = CenteredHeaderPack)
}

/**
 * Such-Zustand des Default-Headers ([HeaderSearch] mit `active = true`): das Suchfeld tritt an die
 * Stelle des Titels (Back links). Beweist die optionale Such-Capability der header-Region.
 */
@Preview(name = "Header-Slot · Default (Suche aktiv)", widthDp = 411, heightDp = 200)
@Composable
private fun HeaderSlotSearchPreview() {
    KomgaReaderTheme {
        Scaffold(
            topBar = {
                LocalResolvedSlots.current.headerSlot(
                    HeaderState(
                        title = "Sammlung",
                        onBack = {},
                        search = HeaderSearch(
                            active = true,
                            query = "",
                            onQueryChange = {},
                            onOpen = {},
                            onClose = {},
                            placeholder = "In Sammlung suchen",
                        ),
                    ),
                )
            },
        ) { padding ->
            Text("Body bleibt unverändert.", modifier = Modifier.padding(padding).fillMaxWidth())
        }
    }
}
