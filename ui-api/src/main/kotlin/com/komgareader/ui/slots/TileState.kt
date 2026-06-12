package com.komgareader.ui.slots

import com.komgareader.domain.model.Series

/**
 * Capability-Surface der Kachel-Region (`tiles`-Slot): das Werk + sein Lokal-Status + die
 * Navigations-Callbacks. Ein [TilesSlot]-Pack rendert daraus eine Kachel; das Cover-Laden und der
 * E-Ink-Filter (`FilteredAsyncImage`, `crossfade(false)`) sind **host-erzwungen** und nicht Teil
 * dieser Surface — ein Pack arrangiert nur Look/Struktur, nicht die Bild-/E-Ink-Policy.
 */
data class TileState(
    val series: Series,
    val isLocal: Boolean,
    val onClick: () -> Unit,
    val onLongClick: () -> Unit,
)
