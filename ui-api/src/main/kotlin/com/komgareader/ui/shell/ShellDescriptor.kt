package com.komgareader.ui.shell

/**
 * Endliches Nav-Anordnungs-Vokabular des Home-Skeletts. Die einzige reale Variabilität zwischen den
 * heutigen Built-ins. Additiv erweiterbar (SIDE_RAIL etc.), ohne bestehende Deskriptoren zu brechen.
 */
enum class ShellNavStyle { BOTTOM_BAR, DRAWER, FLOATING_NAV }

/**
 * Daten-Deskriptor eines Home-Skeletts: **was wohin**, nicht **wie** (das `wie` rendert die host-eigene
 * `DeclarativeShell` aus den host-gebauten [AppShellState]-Stücken). Kein Compose, kein opaker Blob →
 * trägt den 1→3-Pfad: ein externer APK-Pack liefert später genau dieses Datum (L2), der Host rendert.
 */
data class ShellDescriptor(val navStyle: ShellNavStyle)

/** Pure Form-Faktor → Deskriptor-Auflösung: EXPANDED = Bottom-Bar (Tablet/E-Ink), COMPACT = Drawer (Phone).
 *  Compose-frei, unit-testbar — die Built-in-Auswahl als Daten. */
fun descriptorFor(formFactor: ShellFormFactor): ShellDescriptor = when (formFactor) {
    ShellFormFactor.EXPANDED -> ShellDescriptor(ShellNavStyle.BOTTOM_BAR)
    ShellFormFactor.COMPACT -> ShellDescriptor(ShellNavStyle.DRAWER)
}
