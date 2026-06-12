package com.komgareader.domain.model

/**
 * Nutzer-Präferenz für den Shell-Form-Faktor (das Home-Layout-Skelett), orthogonal zum
 * Geräte-/Anzeige-Modus ([DisplayMode], der das Theme wählt).
 *
 * - [AUTO]: aus der Bildschirmbreite ableiten (heutiges Verhalten — <600dp = kompakt).
 * - [COMPACT]: kompaktes Telefon-Skelett (Drawer-Navigation) erzwingen, auch auf breitem Schirm
 *   (z. B. großes E-Ink, das der Nutzer schlank haben will).
 * - [EXPANDED]: breites Tablet-Skelett (Bottom-Bar) erzwingen, auch auf schmalem Schirm.
 */
enum class ShellLayoutMode { AUTO, COMPACT, EXPANDED }
