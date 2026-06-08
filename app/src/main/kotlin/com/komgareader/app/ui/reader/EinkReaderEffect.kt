package com.komgareader.app.ui.reader

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.komgareader.eink.onyx.OnyxRefresher

/**
 * Seiteneffekt-Composable für E-Ink-Refresh-Steuerung im Reader.
 *
 * Aktiviert beim Betreten des Reader-Screens den A2/DW-Schnell-Modus
 * (schnelle Seitenumbrüche) und stellt beim Verlassen den Standard-Modus
 * wieder her. Auf Nicht-Boox-Geräten sind alle Aufrufe No-Ops.
 */
@Composable
fun EinkReaderEffect(refresher: OnyxRefresher) {
    DisposableEffect(refresher) {
        refresher.enterFastMode()
        onDispose {
            refresher.exitFastMode()
        }
    }
}

// Die frühere index-modulo `triggerGhostClearIfNeeded` ist durch den geräteunabhängigen,
// event-gezählten `RefreshScheduler` (domain/eink) ersetzt — siehe ReaderViewModel.refreshScheduler.
