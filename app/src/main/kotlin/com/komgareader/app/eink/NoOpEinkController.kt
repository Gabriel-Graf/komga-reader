package com.komgareader.app.eink

import com.komgareader.domain.eink.ButtonEvent
import com.komgareader.domain.eink.EinkCapabilities
import com.komgareader.domain.eink.EinkContext
import com.komgareader.domain.eink.EinkContextProfile
import com.komgareader.domain.eink.EinkController
import com.komgareader.domain.eink.RefreshMode
import com.komgareader.domain.eink.Region
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Fallback für Nicht-Boox-Geräte/Emulator: kein E-Ink-Steuern, Buttons aus dem Bus. */
class NoOpEinkController @Inject constructor(bus: HardwareButtonBus) : EinkController {
    override val capabilities = EinkCapabilities(hasEink = false, canColor = true, canInvert = true)
    override val buttonEvents: Flow<ButtonEvent> = bus.events
    override fun refresh(region: Region, mode: RefreshMode) { /* No-Op */ }
    override fun setContrast(level: Int) { /* No-Op */ }
    override fun setInverted(inverted: Boolean) { /* No-Op */ }
    override fun applyRefreshMode(id: String?) { /* No-Op */ }
    override fun applyColorMode(id: String?) { /* No-Op */ }
    override fun defaultProfile(context: EinkContext) = EinkContextProfile()
    override fun setBrightness(level: Int) { /* No-Op */ }
    override fun brightness(): Int = 0
}
