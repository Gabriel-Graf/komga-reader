package com.komgareader.eink.onyx

import android.util.Log
import com.komgareader.domain.eink.ButtonEvent
import com.komgareader.domain.eink.EinkCapabilities
import com.komgareader.domain.eink.EinkContext
import com.komgareader.domain.eink.EinkContextProfile
import com.komgareader.domain.eink.EinkController
import com.komgareader.domain.eink.EinkModeOption
import com.komgareader.domain.eink.RefreshMode
import com.komgareader.domain.eink.Region
import com.onyx.android.sdk.api.device.epd.EpdController
import com.onyx.android.sdk.api.device.epd.UpdateOption
import kotlinx.coroutines.flow.Flow

/**
 * Onyx-Boox-spezifische Implementierung des EinkControllers.
 *
 * Verwendet die [EpdController]-API des Onyx-SDK
 * (`com.onyx.android.sdk:onyxsdk-device:1.3.5`) um E-Ink-Refreshes
 * zu steuern: schnelle DU-Updates beim Blättern, periodisches
 * GC-Full-Refresh gegen Ghosting.
 *
 * Darf NUR auf echter Onyx-Hardware instanziiert werden
 * (Build.MANUFACTURER == "ONYX"). Das AppModule übernimmt diese Wahl.
 */
class OnyxEinkController(
    private val buttonEvents_: Flow<ButtonEvent>,
    private val appPackageName: String,
) : EinkController {

    // canColor = true: correct for Onyx Boox Go Color 7 Gen2 (Kaleido screen). The EpdController API
    // v1.3.5 has no device-class query — hardcoded is the safe choice for the only supported Onyx model.
    // When extending to mono Boox devices, differentiate via Build.MODEL.
    override val capabilities = EinkCapabilities(
        hasEink = true,
        canColor = true,
        canInvert = true,
        refreshModes = listOf(
            EinkModeOption(REFRESH_HD, "HD"),
            EinkModeOption(REFRESH_BALANCED, "Balanced"),
            EinkModeOption(REFRESH_REGAL, "Regal"),
            EinkModeOption(REFRESH_SPEED, "Speed"),
            EinkModeOption(REFRESH_ULTRA, "Ultra"),
        ),
        colorModes = listOf(
            EinkModeOption(COLOR_SYSTEM, "System"),
            EinkModeOption(COLOR_ON, "Colour"),
            EinkModeOption(COLOR_MONO, "Mono"),
        ),
    )

    override val buttonEvents: Flow<ButtonEvent> = buttonEvents_

    // ---------------------------------------------------------------
    // EinkController-Interface
    // ---------------------------------------------------------------

    override fun refresh(region: Region, mode: RefreshMode) {
        Log.d(TAG, "refresh($region, $mode) — delegated to EinkWise context control")
    }

    override fun setContrast(level: Int) {
        // FrontLightController wäre der richtige Ort dafür;
        // vorerst als No-Op mit Log.
        Log.d(TAG, "setContrast($level) — noch nicht implementiert")
    }

    override fun setInverted(inverted: Boolean) {
        runCatching {
            if (inverted) EpdController.enableNightMode() else EpdController.disableNightMode()
            Log.d(TAG, "setInverted($inverted) — Night-Mode gesetzt")
        }.onFailure { e ->
            Log.e(TAG, "Fehler beim Setzen des Night-Mode", e)
        }
    }

    override fun applyRefreshMode(id: String?) {
        val option = when (id) {
            REFRESH_HD -> UpdateOption.NORMAL
            REFRESH_BALANCED -> UpdateOption.FAST_QUALITY
            REFRESH_REGAL -> UpdateOption.REGAL
            REFRESH_SPEED -> UpdateOption.FAST
            REFRESH_ULTRA -> UpdateOption.FAST_X
            else -> return // null/unknown = leave untouched
        }
        runCatching { EpdController.setAppScopeRefreshMode(option) }
            .onFailure { Log.e(TAG, "setAppScopeRefreshMode($option) failed", it) }
    }

    override fun applyColorMode(id: String?) {
        runCatching {
            when (id) {
                COLOR_ON -> EpdController.enableColorAdjust()
                COLOR_MONO -> EpdController.disableColorAdjust()
                else -> Unit // COLOR_SYSTEM / null / unknown = leave untouched
            }
        }.onFailure { Log.e(TAG, "applyColorMode($id) failed", it) }
    }

    override fun defaultProfile(context: EinkContext): EinkContextProfile {
        val refresh = if (context == EinkContext.WEBTOON) REFRESH_SPEED else REFRESH_HD
        return EinkContextProfile(refreshModeId = refresh, colorModeId = COLOR_SYSTEM)
    }

    companion object {
        private const val TAG = "OnyxEinkController"

        const val REFRESH_HD = "hd"
        const val REFRESH_BALANCED = "balanced"
        const val REFRESH_REGAL = "regal"
        const val REFRESH_SPEED = "speed"
        const val REFRESH_ULTRA = "ultra"
        const val COLOR_SYSTEM = "system"
        const val COLOR_ON = "color"
        const val COLOR_MONO = "mono"
    }
}
