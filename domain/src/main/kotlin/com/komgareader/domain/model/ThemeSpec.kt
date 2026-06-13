package com.komgareader.domain.model

/**
 * Voller deklarativer Theme-Deskriptor eines externen UI-Packs (Phase 2). **Nur Primitive** (Hex-Strings,
 * Int, Boolean, Float) — keine Compose-/ui-api-Typen, damit `domain`/`data` rein bleiben. Übersetzung in
 * ColorScheme/Shapes/Typo passiert nur in `:app` (`UiPackSpec.toUiPackOrNull`). Jede Sektion optional.
 */
data class ThemeSpec(
    val light: ColorRolesSpec? = null,
    val dark: ColorRolesSpec? = null,
    val cornerRadiusDp: Int? = null,
    val elevation: Boolean? = null,
    val typography: TypoSpec? = null,
) {
    /** true, wenn mindestens ein Farb-Modus geliefert wird (sonst ist die theme-Farbschicht leer). */
    val hasColors: Boolean get() = light != null || dark != null
}

/** Die acht Farb-Rollen, die ein Pack pro Modus setzen darf (genau die, die der In-Tree-AuroraPack setzt).
 *  Alle als `#RRGGBB`-Hex-Strings, alle optional (fehlend → Host-Default des Modus). `navDock` = Bottom-Nav-Fläche. */
data class ColorRolesSpec(
    val background: String? = null,
    val surface: String? = null,
    val navDock: String? = null,
    val accent: String? = null,
    val onAccent: String? = null,
    val onBackground: String? = null,
    val onSurfaceVariant: String? = null,
    val outline: String? = null,
)

/** Typo-Tuning als Daten: Font-Gewichte (100..900) + Headline-Tracking in em. Keine Font-Dateien (YAGNI). */
data class TypoSpec(
    val headlineWeight: Int? = null,
    val titleWeight: Int? = null,
    val headlineTrackingEm: Float? = null,
)
