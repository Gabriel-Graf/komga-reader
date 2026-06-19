package com.komgareader.eink.onyx

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
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
import com.onyx.android.sdk.api.device.epd.UpdateMode
import com.onyx.android.sdk.api.device.epd.UpdateOption
import com.onyx.android.sdk.api.device.screensaver.ScreenSaverUtils
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
    override val capabilities: EinkCapabilities by lazy {
        EinkCapabilities(
            hasEink = true,
            canColor = true,
            canInvert = true,
            // Onyx Boox exposes physical volume keys the reader remaps (long-press shortcuts).
            hasHardwareButtons = true,
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
        )
    }

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
    // Screensaver (Naht B — device standby image). PoC: does the SDK path stick?
    // ---------------------------------------------------------------

    override fun setScreenSaverImage(absolutePath: String): Boolean {
        // ScreenSaverUtils.setScreenResource broadcasts ONYX_SCREENSAVER_ACTION to the system, which
        // installs the file as the standby image (TYPE_SCREENSAVER). showResultHint=false: BOOK_COVER
        // is the default and the worker runs on every book open, so the Onyx "saved" toast would pop
        // up each time — suppress it (it was only on for PoC acceptance on device).
        val ok = runCatching {
            ScreenSaverUtils.setScreenResource(appContext, absolutePath, ScreenSaverUtils.TYPE_SCREENSAVER, false)
        }.isSuccess
        // The Onyx daydream/standby service caches the standby bitmap and does not hot-reload when the
        // image changes — only the FIRST set ever showed. A reload is forced with an "update_standby_pic"
        // broadcast. BUT setScreenResource is ASYNCHRONOUS on Android P+ (it only broadcasts
        // ONYX_SCREENSAVER_ACTION; the system process then copies the file to standby-1.png) — firing the
        // reload immediately races ahead of that copy and reloads the PREVIOUS standby image, so every set
        // after the first appeared to do nothing. Send the reload once immediately AND again after a short
        // delay, by which point the system has written the new standby file.
        val reload = { runCatching { appContext.sendBroadcast(Intent(ScreenSaverUtils.UPDATE_STANDBY_PIC_ACTION)) } }
        reload()
        Handler(Looper.getMainLooper()).postDelayed({ reload() }, STANDBY_RELOAD_DELAY_MS)
        // adb logcat -s OnyxEinkController
        Log.i(TAG, "setScreenSaverImage($absolutePath) -> $ok")
        return ok
    }

    override fun setPowerOffImage(absolutePath: String): Boolean {
        // Same path as the standby image, but TYPE_SHUTDOWN_IMAGE (the screen shown when the device is
        // powered off). The shutdown image is read at power-off, not from a live daydream cache, so no
        // update_standby_pic reload broadcast is needed. showResultHint=false (no per-set toast).
        val ok = runCatching {
            ScreenSaverUtils.setScreenResource(appContext, absolutePath, ScreenSaverUtils.TYPE_SHUTDOWN_IMAGE, false)
        }.isSuccess
        Log.i(TAG, "setPowerOffImage($absolutePath) -> $ok")
        return ok
    }

    companion object {
        private const val TAG = "OnyxEinkController"

        /** Delay before the second standby-reload broadcast, giving the async system copy time to land. */
        private const val STANDBY_RELOAD_DELAY_MS = 1500L

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
