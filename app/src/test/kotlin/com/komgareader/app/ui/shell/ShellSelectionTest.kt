package com.komgareader.app.ui.shell

import com.komgareader.domain.model.ShellLayoutMode
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

    @Test
    fun `AUTO leitet bei schmaler breite compact ab`() {
        assertEquals(ShellFormFactor.COMPACT, resolveFormFactor(ShellLayoutMode.AUTO, widthDp = 411))
    }

    @Test
    fun `AUTO leitet bei breiter breite expanded ab`() {
        assertEquals(ShellFormFactor.EXPANDED, resolveFormFactor(ShellLayoutMode.AUTO, widthDp = 1264))
    }

    @Test
    fun `COMPACT-override erzwingt compact auch bei breitem screen`() {
        assertEquals(ShellFormFactor.COMPACT, resolveFormFactor(ShellLayoutMode.COMPACT, widthDp = 1264))
    }

    @Test
    fun `EXPANDED-override erzwingt expanded auch bei schmalem screen`() {
        assertEquals(ShellFormFactor.EXPANDED, resolveFormFactor(ShellLayoutMode.EXPANDED, widthDp = 320))
    }
}
