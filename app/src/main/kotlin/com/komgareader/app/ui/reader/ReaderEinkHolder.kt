package com.komgareader.app.ui.reader

import androidx.lifecycle.ViewModel
import com.komgareader.eink.onyx.OnyxRefresher
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Schlanker ViewModel-Halter für den [OnyxRefresher].
 *
 * Wird genutzt um den Singleton [OnyxRefresher] sauber in Composables
 * zu injizieren, ohne die Hilt-Entry-Point-API direkt zu nutzen.
 */
@HiltViewModel
class ReaderEinkHolder @Inject constructor(
    val refresher: OnyxRefresher,
) : ViewModel()
