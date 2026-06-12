package com.komgareader.app.ui.shell

import com.komgareader.ui.shell.ShellDescriptor
import com.komgareader.ui.shell.ShellFormFactor
import com.komgareader.ui.shell.ShellNavStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * App-seitige Hälfte der Shell-Auswahl: die [ShellPackRegistry] bildet den Form-Faktor auf die EINE
 * host-eigene [DeclarativeShell] ab, deskriptor-geschaltet. Die [DeclarativeShell] koppelt an
 * app-Komponenten und liegt darum in `:app`; die reine Form-Faktor-/Deskriptor-Auflösung
 * (`formFactorFor`/`resolveFormFactor`/`descriptorFor`) ist im Modul `:ui-api` getestet
 * (`ShellFormFactorTest`/`ShellDescriptorTest`).
 */
class ShellSelectionTest {

    @Test
    fun `expanded liefert eine Bottom-Bar-DeclarativeShell`() {
        val pack = ShellPackRegistry.forFormFactor(ShellFormFactor.EXPANDED) as DeclarativeShell
        assertEquals(ShellNavStyle.BOTTOM_BAR, pack.descriptor.navStyle)
    }

    @Test
    fun `compact liefert eine Drawer-DeclarativeShell`() {
        val pack = ShellPackRegistry.forFormFactor(ShellFormFactor.COMPACT) as DeclarativeShell
        assertEquals(ShellNavStyle.DRAWER, pack.descriptor.navStyle)
    }

    @Test
    fun `ein UI-Pack-Override schlägt den Form-Faktor`() {
        // Auch bei breitem Form-Faktor (Boox/Tablet) gewinnt der vom UI-Pack gelieferte navStyle.
        val pack = ShellPackRegistry.forFormFactor(
            ShellFormFactor.EXPANDED,
            override = ShellDescriptor(ShellNavStyle.DRAWER),
        ) as DeclarativeShell
        assertEquals(ShellNavStyle.DRAWER, pack.descriptor.navStyle)
    }
}
