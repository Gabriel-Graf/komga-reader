package com.komgareader.app.ui.shell

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class ShellSelectionTest {

    @Test
    fun `schmale breite ist compact`() {
        assertEquals(ShellFormFactor.COMPACT, formFactorFor(widthDp = 411))
    }

    @Test
    fun `600dp und mehr ist expanded`() {
        assertEquals(ShellFormFactor.EXPANDED, formFactorFor(widthDp = 600))
        assertEquals(ShellFormFactor.EXPANDED, formFactorFor(widthDp = 1264))
    }

    @Test
    fun `expanded waehlt die default-shell`() {
        assertSame(DefaultShell, ShellPackRegistry.forFormFactor(ShellFormFactor.EXPANDED))
    }

    @Test
    fun `compact waehlt die phone-shell`() {
        assertSame(PhoneShell, ShellPackRegistry.forFormFactor(ShellFormFactor.COMPACT))
    }
}
