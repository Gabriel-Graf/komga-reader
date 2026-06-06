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

/**
 * Bestimmt, ob ein GC-Full-Refresh fällig ist, und löst ihn aus.
 * Wird bei jedem Seitenumbruch im Reader aufgerufen.
 *
 * @param pageIndex       aktueller 0-basierter Seitenindex
 * @param refresher       [OnyxRefresher]-Singleton
 * @param ghostInterval   alle N Seiten wird ein GC-Refresh ausgelöst
 */
fun triggerGhostClearIfNeeded(
    pageIndex: Int,
    refresher: OnyxRefresher,
    ghostInterval: Int = OnyxRefresher.GHOST_CLEAR_INTERVAL,
): Boolean {
    // pageIndex 0 nicht zählen (initialer Aufruf)
    if (pageIndex == 0) return false
    return (pageIndex % ghostInterval) == 0
}
