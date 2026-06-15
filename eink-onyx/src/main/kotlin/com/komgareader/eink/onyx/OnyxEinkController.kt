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
import com.onyx.android.sdk.api.device.brightness.BaseBrightnessProvider
import com.onyx.android.sdk.api.device.brightness.BrightnessController
import com.onyx.android.sdk.api.device.brightness.BrightnessType
import com.onyx.android.sdk.api.device.brightness.CTMBrightnessProvider
import com.onyx.android.sdk.api.device.brightness.ColdBrightnessProvider
import com.onyx.android.sdk.api.device.brightness.FLBrightnessProvider
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
    // Lazy: brightnessRange is derived from the live device (see brightnessProvider below), which is
    // only safe to query once the SDK is up — capabilities is first read at composition, not construction.
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
            // Index space over the device's own value list (0 = off, maxIndex = brightest); null when
            // the device has no controllable frontlight. Derived from the real provider, NOT a
            // hardcoded 0..255 scale (which only fits legacy single-light FL devices, not the
            // warm/cold Kaleido frontlight of the Go Color 7 Gen2).
            brightnessRange = brightnessMaxIndex.takeIf { it > 0 }?.let { 0..it },
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
    // Frontlight brightness (Naht B — device capability)
    // ---------------------------------------------------------------

    /**
     * The device's brightness channel as an index-based provider. The Go Color 7 Gen2 has a SPLIT
     * warm+cold frontlight (`BrightnessType.WARM_AND_COLD`), for which the legacy
     * `FrontLightController.setBrightness` is a silent no-op — verified against onyxsdk-device 1.3.5
     * bytecode, it early-returns when `hasFLBrightness()` is false and never drives the warm/cold
     * LEDs. We map each device class to its OWN concrete provider (matching the SDK's own
     * `BrightnessController.initProviderMap`): WARM_AND_COLD → the COLD light (the main reading
     * light), CTM → the CTM brightness channel, FL → the legacy single light; `NONE` → no frontlight.
     *
     * Lazily resolved (queries `Device.currentDevice()`, safe once the SDK is up — first read at
     * composition, not construction). Any failure degrades to "no frontlight" so the UI hides the bar.
     */
    private val brightnessProvider: BaseBrightnessProvider? by lazy {
        runCatching {
            when (BrightnessController.getBrightnessType(appContext)) {
                BrightnessType.WARM_AND_COLD -> ColdBrightnessProvider(appContext)
                BrightnessType.CTM -> CTMBrightnessProvider(appContext)
                BrightnessType.FL -> FLBrightnessProvider(appContext)
                else -> null // NONE
            }
        }.onFailure { Log.e(TAG, "brightness provider init failed", it) }.getOrNull()
    }

    /** Highest selectable index of [brightnessProvider] (0 if none) — the UI range is `0..maxIndex`. */
    private val brightnessMaxIndex: Int by lazy {
        runCatching { brightnessProvider?.maxIndex ?: 0 }.getOrDefault(0)
    }

    /** Last set index — fallback for [brightness] if the provider read fails. */
    private var brightnessLevel = 0

    override fun setBrightness(level: Int) {
        val provider = brightnessProvider ?: run {
            Log.w(TAG, "setBrightness: device has no controllable frontlight"); return
        }
        val idx = level.coerceIn(0, brightnessMaxIndex)
        val ok = runCatching {
            if (idx <= 0) {
                provider.close() // bottom of the range = light off (index 0 of a value list may still glow)
            } else {
                if (!provider.isLightOn) provider.open()
                provider.setIndex(idx)
            }
        }.getOrElse { Log.e(TAG, "setBrightness($idx) failed", it); false }
        // logcat confirmation for on-device verification:  adb logcat -s OnyxEinkController
        Log.i(TAG, "setBrightness(level=$level idx=$idx max=$brightnessMaxIndex) -> $ok")
        if (ok) brightnessLevel = idx
    }

    override fun brightness(): Int =
        runCatching { brightnessProvider?.index ?: brightnessLevel }.getOrDefault(brightnessLevel)

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
