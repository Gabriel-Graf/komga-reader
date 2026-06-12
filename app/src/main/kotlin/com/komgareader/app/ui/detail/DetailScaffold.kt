package com.komgareader.app.ui.detail

import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import com.komgareader.ui.slots.DetailScaffoldState
import com.komgareader.ui.slots.HeaderState
import com.komgareader.ui.slots.LocalResolvedSlots

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
