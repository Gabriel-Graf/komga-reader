package com.komgareader.ui.shell

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Reine Deskriptor-Auflösung der Shell-Naht: [descriptorFor] bildet den Form-Faktor auf das
 * Nav-Anordnungs-Vokabular ([ShellNavStyle]) ab. Compose-frei, unit-testbar — die Built-in-Auswahl
 * als Daten, die die host-eigene `DeclarativeShell` (app) interpretiert. Trägt den 1→3-Pfad: derselbe
 * Deskriptor kommt später extern (L2).
 */
class ShellDescriptorTest {

    @Test
    fun `EXPANDED wählt das Bottom-Bar-Skelett`() {
        assertEquals(ShellNavStyle.BOTTOM_BAR, descriptorFor(ShellFormFactor.EXPANDED).navStyle)
    }

    @Test
    fun `COMPACT wählt das Drawer-Skelett`() {
        assertEquals(ShellNavStyle.DRAWER, descriptorFor(ShellFormFactor.COMPACT).navStyle)
    }
}
