package com.komgareader.app.data

import com.komgareader.domain.eink.EinkContext
import com.komgareader.domain.eink.EinkController
import com.komgareader.domain.eink.RefreshMode
import com.komgareader.domain.eink.Region
import com.komgareader.domain.eink.resolveEinkProfile
import com.komgareader.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Imperative shell for the device-managed E-Ink seam: given the context currently on screen,
 * resolves the effective profile (user override over device default) and applies it via the
 * [EinkController]. App-scope refresh is global, so each context change is a single apply call.
 * On non-Boox devices the controller advertises no modes and every apply is a no-op.
 */
@Singleton
class EinkContextController @Inject constructor(
    private val controller: EinkController,
    private val settings: SettingsRepository,
) {
    suspend fun applyFor(context: EinkContext) {
        val override = settings.einkContextProfiles.first()[context]
        val resolved = resolveEinkProfile(override, controller.defaultProfile(context))
        controller.applyRefreshMode(resolved.refreshModeId)
        controller.applyColorMode(resolved.colorModeId)
    }

    /** User-triggered GC full refresh to clear E-Ink ghosting. No-op on non-E-Ink devices. */
    fun manualFullRefresh() {
        controller.refresh(Region(0, 0, 0, 0), RefreshMode.FULL)
    }
}
