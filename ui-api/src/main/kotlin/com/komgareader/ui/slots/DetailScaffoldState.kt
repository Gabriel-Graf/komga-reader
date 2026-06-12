package com.komgareader.ui.slots

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable

/**
 * Capability-Surface des Vollbild-Detail-Gerüsts: Titel + Zurück + Header-Aktionen (→ header-Slot) +
 * optionale Header-Suche ([search], → header-Slot), optionaler Snackbar-Host, und der host-gebaute
 * Body. Ein [DetailSlot]-Pack rahmt diese Stücke; den Body (Hero/Grid/Dialoge/State) baut der Host,
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
