package com.komgareader.domain.eink

import kotlinx.coroutines.flow.Flow

/** E-Ink-Refresh-Modus, nach steigender Qualität / sinkender Geschwindigkeit. */
enum class RefreshMode { A2, FAST, PARTIAL, FULL }

/** Rechteck-Region eines Refreshs (Pixel). */
data class Region(val x: Int, val y: Int, val width: Int, val height: Int)

/** Physische Geräte-Taste. */
enum class HardwareButton { PAGE_NEXT, PAGE_PREV, VOLUME_UP, VOLUME_DOWN }

data class ButtonEvent(val button: HardwareButton)

/** Geräte-Fähigkeiten zur Laufzeit (Boox vs. generisches Tablet). */
data class EinkCapabilities(
    val hasEink: Boolean,
    val canColor: Boolean,
    val canInvert: Boolean,
)

/**
 * Naht B (Geräteseite): kapselt E-Ink-Spezifika. Boox-Implementierung nutzt das
 * Onyx-SDK; auf Nicht-Boox greift eine No-Op-Implementierung (Standard-Invalidate,
 * Buttons als normale KeyEvents), damit Entwicklung/Tests überall laufen.
 */
interface EinkController {
    val capabilities: EinkCapabilities
    val buttonEvents: Flow<ButtonEvent>
    fun refresh(region: Region, mode: RefreshMode)
    fun setContrast(level: Int)
    fun setInverted(inverted: Boolean)
}
