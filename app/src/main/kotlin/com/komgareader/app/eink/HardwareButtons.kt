package com.komgareader.app.eink

import com.komgareader.domain.eink.ButtonEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Verteilt physische Tasten-Events (Volume = Blättern) an den aktiven Reader. */
@Singleton
class HardwareButtonBus @Inject constructor() {
    private val _events = MutableSharedFlow<ButtonEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<ButtonEvent> = _events
    fun emit(event: ButtonEvent) { _events.tryEmit(event) }
}
