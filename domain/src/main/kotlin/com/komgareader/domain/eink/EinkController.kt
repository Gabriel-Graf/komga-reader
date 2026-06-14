package com.komgareader.domain.eink

import kotlinx.coroutines.flow.Flow

/** E-Ink-Refresh-Modus, nach steigender Qualität / sinkender Geschwindigkeit. */
enum class RefreshMode { A2, FAST, PARTIAL, FULL }

/** Rechteck-Region eines Refreshs (Pixel). */
data class Region(val x: Int, val y: Int, val width: Int, val height: Int)

/** Physische Geräte-Taste. */
enum class HardwareButton { PAGE_NEXT, PAGE_PREV, VOLUME_UP, VOLUME_DOWN }

/** Press duration class, so a held button can mean a different action than a tap. */
enum class PressKind { SHORT, LONG }

data class ButtonEvent(val button: HardwareButton, val press: PressKind = PressKind.SHORT)

/** Device capabilities at runtime (Boox vs. generic tablet). */
data class EinkCapabilities(
    val hasEink: Boolean,
    val canColor: Boolean,
    val canInvert: Boolean,
    /** Refresh modes this device can switch app-wide; empty = axis unsupported (UI hides it). */
    val refreshModes: List<EinkModeOption> = emptyList(),
    /** System colour modes this device can switch; empty = axis unsupported. */
    val colorModes: List<EinkModeOption> = emptyList(),
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
}
