package com.komgareader.app.eink

import com.komgareader.domain.eink.ButtonEvent
import com.komgareader.domain.eink.EinkCapabilities
import com.komgareader.domain.eink.EinkContext
import com.komgareader.domain.eink.EinkContextProfile
import com.komgareader.domain.eink.EinkController
import com.komgareader.domain.eink.RefreshMode
import com.komgareader.domain.eink.Region
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Test fake for [EinkController] that records the most recent [refresh] call.
 * Shared across EinkContextControllerTest and ReaderShortcutsViewModelTest.
 */
class RecordingEinkController : EinkController {
    override val capabilities = EinkCapabilities(hasEink = true, canColor = false, canInvert = false)
    override val buttonEvents: Flow<ButtonEvent> = emptyFlow()
    var lastRefreshRegion: Region? = null
    var lastRefreshMode: RefreshMode? = null
    override fun refresh(region: Region, mode: RefreshMode) {
        lastRefreshRegion = region
        lastRefreshMode = mode
    }
    override fun setContrast(level: Int) {}
    override fun setInverted(inverted: Boolean) {}
    override fun applyRefreshMode(id: String?) {}
    override fun applyColorMode(id: String?) {}
    override fun defaultProfile(context: EinkContext) = EinkContextProfile()
}
