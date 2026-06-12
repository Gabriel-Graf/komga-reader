package com.komgareader.app.ui.shell

import com.komgareader.ui.shell.ShellFormFactor
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * App-seitige Hälfte der Shell-Auswahl: die [ShellPackRegistry] bildet den Form-Faktor auf die
 * konkreten Built-in-Shells ab. Die Built-ins ([DefaultShell]/[PhoneShell]) koppeln an app-Komponenten
 * und liegen darum in `:app`; die reine Form-Faktor-Auflösung (`formFactorFor`/`resolveFormFactor`) ist
 * im Modul `:ui-api` getestet (`ShellFormFactorTest`).
 */
class ShellSelectionTest {

    @Test
    fun `expanded wählt die default-shell`() {
        assertSame(DefaultShell, ShellPackRegistry.forFormFactor(ShellFormFactor.EXPANDED))
    }

    @Test
    fun `compact wählt die phone-shell`() {
        assertSame(PhoneShell, ShellPackRegistry.forFormFactor(ShellFormFactor.COMPACT))
    }
}
