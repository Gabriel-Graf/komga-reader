package com.komgareader.domain.eink

import kotlinx.coroutines.flow.Flow

/** E-Ink-Refresh-Modus, nach steigender Qualität / sinkender Geschwindigkeit. */
enum class RefreshMode { A2, FAST, PARTIAL, FULL }

/** Rechteck-Region eines Refreshs (Pixel). */
data class Region(val x: Int, val y: Int, val width: Int, val height: Int)

/** Physische Geräte-Taste. */
enum class HardwareButton { PAGE_NEXT, PAGE_PREV }

data class ButtonEvent(val button: HardwareButton)

/** Device capabilities at runtime (Boox vs. generic tablet). */
data class EinkCapabilities(
    val hasEink: Boolean,
    val canColor: Boolean,
    val canInvert: Boolean,
    /** Refresh modes this device can switch app-wide; empty = axis unsupported (UI hides it). */
    val refreshModes: List<EinkModeOption> = emptyList(),
    /** System colour modes this device can switch; empty = axis unsupported. */
    val colorModes: List<EinkModeOption> = emptyList(),
    /** Frontlight brightness range, or null if the device has no controllable frontlight. */
    val brightnessRange: IntRange? = null,
    /** True if the device exposes physical buttons the reader remaps (e.g. Onyx volume keys). */
    val hasHardwareButtons: Boolean = false,
)

/**
 * Seam B (device side): encapsulates E-Ink specifics. The Boox implementation uses the
 * Onyx SDK; on non-Boox hardware a no-op implementation takes over (standard invalidate,
 * buttons as normal KeyEvents) so development and tests run everywhere.
 */
interface EinkController {
    val capabilities: EinkCapabilities
    val buttonEvents: Flow<ButtonEvent>
    fun refresh(region: Region, mode: RefreshMode)
    fun setContrast(level: Int)
    fun setInverted(inverted: Boolean)

    /** Applies an advertised refresh mode by id; null/unknown id = graceful no-op. */
    fun applyRefreshMode(id: String?)

    /** Applies an advertised system colour mode by id; null/unknown id = graceful no-op. */
    fun applyColorMode(id: String?)

    /** Device-specific sane default profile for the given context (app stores only overrides). */
    fun defaultProfile(context: EinkContext): EinkContextProfile

    /** Sets the frontlight brightness, clamped to [EinkCapabilities.brightnessRange]; no-op if null. */
    fun setBrightness(level: Int)

    /** Current frontlight brightness (0 if unsupported). */
    fun brightness(): Int
}
