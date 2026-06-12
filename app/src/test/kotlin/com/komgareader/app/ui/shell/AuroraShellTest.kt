package com.komgareader.app.ui.shell

import com.komgareader.domain.model.DisplayMode
import com.komgareader.ui.shell.ShellDescriptor
import com.komgareader.ui.shell.ShellNavStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AuroraShellTest {
    @Test fun `smartphone-modus erzwingt floating nav`() =
        assertEquals(ShellDescriptor(ShellNavStyle.FLOATING_NAV), auroraShellOverride(DisplayMode.SMARTPHONE))

    @Test fun `eink-modus kein override`() =
        assertNull(auroraShellOverride(DisplayMode.EINK))
}
