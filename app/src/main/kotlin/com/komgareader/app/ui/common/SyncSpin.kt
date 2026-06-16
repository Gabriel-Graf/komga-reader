package com.komgareader.app.ui.common

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Minimum time a sync flag stays true so the spinning icon shows at least one visible turn — long
 * enough to outlast the E-Ink turn cycle ([com.komgareader.app.ui.components.iconAnimationPlan]).
 */
const val MIN_SYNC_SPIN_MS = 700L

/**
 * Holds [this] true while [block] runs, but for at least [minMs]. Without the floor a sync that
 * finishes instantly (offline refresh, no-op pull, empty server list) would pulse the flag
 * true→false within a single frame; `StateFlow` conflates that, the collector never observes `true`,
 * and the icon never animates. The floor guarantees the `true` value is observed and the rotation
 * has time to play. Used by every sync/reload/refresh action behind [SyncIconButton].
 */
suspend fun MutableStateFlow<Boolean>.holdSpinning(
    minMs: Long = MIN_SYNC_SPIN_MS,
    block: suspend () -> Unit = {},
) {
    value = true
    try {
        coroutineScope {
            launch { delay(minMs) }
            block()
        }
    } finally {
        value = false
    }
}
