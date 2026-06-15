package com.komgareader.eink.onyx

import android.content.Context
import android.util.Log
import com.komgareader.domain.eink.ButtonEvent
import com.komgareader.domain.eink.EinkCapabilities
import com.komgareader.domain.eink.EinkContext
import com.komgareader.domain.eink.EinkContextProfile
import com.komgareader.domain.eink.EinkController
import com.komgareader.domain.eink.EinkModeOption
import com.komgareader.domain.eink.RefreshMode
import com.komgareader.domain.eink.Region
import com.onyx.android.sdk.api.device.FrontLightController
import com.onyx.android.sdk.api.device.epd.EpdController
import com.onyx.android.sdk.api.device.epd.UpdateMode
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
    private val appContext: Context,
) : EinkController {

    // canColor = true: correct for Onyx Boox Go Color 7 Gen2 (Kaleido screen). The EpdController API
    // v1.3.5 has no device-class query — hardcoded is the safe choice for the only supported Onyx model.
    // When extending to mono Boox devices, differentiate via Build.MODEL.
    override val capabilities = EinkCapabilities(
        hasEink = true,
        canColor = true,
        canInvert = true,
        refreshModes = listOf(
            EinkModeOption(REFRESH_HD, "HD", "Best quality, full refresh — for reading and detail."),
            EinkModeOption(REFRESH_BALANCED, "Balanced", "A compromise between speed and quality."),
            EinkModeOption(REFRESH_REGAL, "Regal", "Anti-ghosting, optimised for text."),
            EinkModeOption(REFRESH_SPEED, "Speed", "Fast page turns, some ghosting."),
            EinkModeOption(REFRESH_ULTRA, "Ultra", "Maximum speed, lowest quality — for scrolling."),
        ),
        colorModes = listOf(
            EinkModeOption(COLOR_SYSTEM, "System", "Leave the Onyx colour setting untouched."),
            EinkModeOption(COLOR_ON, "Colour", "Onyx colour enhancement on."),
            EinkModeOption(COLOR_MONO, "Mono", "Greyscale, colour off."),
        ),
        // FrontLightController.getBrightnessMinimum/Maximum(Context) return the device-specific
        // range at runtime but cannot be called safely before the SDK is fully initialised.
        // 0..255 is the standard Android / Onyx Go Color 7 Gen2 frontlight range; verify on device
        // with FrontLightController.getBrightnessMinimum(ctx)..getBrightnessMaximum(ctx).
        brightnessRange = 0..255,
    )

    override val buttonEvents: Flow<ButtonEvent> = buttonEvents_

    // ---------------------------------------------------------------
    // EinkController-Interface
    // ---------------------------------------------------------------

    override fun refresh(region: Region, mode: RefreshMode) {
        if (mode != RefreshMode.FULL) {
            Log.d(TAG, "refresh($region, $mode) — non-full delegated to EinkWise context control")
            return
        }
        // Manual GC full update to clear ghosting. applyTransientUpdate is View-free and triggers
        // a one-shot screen repaint at the requested UpdateMode, independent of app-scope settings.
        runCatching { EpdController.applyTransientUpdate(UpdateMode.GC) }
            .onFailure { Log.e(TAG, "applyTransientUpdate(GC) failed", it) }
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

    // ---------------------------------------------------------------
    // Frontlight brightness (Naht B — device capability)
    // ---------------------------------------------------------------

    private var brightnessLevel = 0

    override fun setBrightness(level: Int) {
        val range = capabilities.brightnessRange ?: return
        val clamped = level.coerceIn(range)
        runCatching { FrontLightController.setBrightness(appContext, clamped) }
            .onFailure { Log.e(TAG, "setBrightness($clamped) failed", it) }
        brightnessLevel = clamped
    }

    override fun brightness(): Int = brightnessLevel

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
