package com.komgareader.app.ui.detail

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import com.komgareader.app.ui.slots.HeaderSearch
import com.komgareader.app.ui.slots.HeaderState
import com.komgareader.app.ui.slots.LocalResolvedSlots

/**
 * Capability-Surface des Vollbild-Detail-Gerüsts: Titel + Zurück + Header-Aktionen (→ header-Slot) +
 * optionale Header-Suche ([search], → header-Slot), optionaler Snackbar-Host, und der host-gebaute
 * Body. Ein `DetailSlot`-Pack rahmt diese Stücke; den Body (Hero/Grid/Dialoge/State) baut der Host,
 * das Pack platziert ihn nur — „UI neu, Kernlogik gleich". E-Ink-Invarianten und der Header-Look sind
 * über den header-Slot bzw. den Host erzwungen, nicht Teil hiervon.
 */
data class DetailScaffoldState(
    val title: String,
    val onBack: () -> Unit,
    val actions: @Composable RowScope.() -> Unit = {},
    val search: HeaderSearch? = null,
    val snackbarHost: @Composable () -> Unit = {},
    val content: @Composable (padding: PaddingValues) -> Unit,
)

/**
 * Der mitgelieferte Default-Renderer des Detail-Gerüsts: ein Material-[Scaffold] mit dem Header über
 * den `header`-Slot, dem Snackbar-Host und dem padding-durchgereichten Body. Exakt das zuvor in den
 * Detail-Routen duplizierte Gerüst — verhaltensgleich.
 */
@Composable
fun DefaultDetailScaffold(state: DetailScaffoldState) {
    Scaffold(
        topBar = {
            LocalResolvedSlots.current.headerSlot(
                HeaderState(state.title, state.onBack, state.actions, state.search),
            )
        },
        snackbarHost = state.snackbarHost,
    ) { padding -> state.content(padding) }
}
