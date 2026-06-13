package com.komgareader.domain.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UiPackSpecThemeTest {
    private fun spec(theme: ThemeSpec? = null) =
        UiPackSpec(packageName = "p", displayName = "d", abiVersion = 2, theme = theme)

    @Test fun `theme mit farben zaehlt als override`() =
        assertTrue(spec(ThemeSpec(dark = ColorRolesSpec(background = "#15171C"))).hasAnyOverride)

    @Test fun `leerer spec ist kein override`() = assertFalse(spec().hasAnyOverride)

    @Test fun `ThemeSpec hasColors nur bei light oder dark`() {
        assertTrue(ThemeSpec(light = ColorRolesSpec(accent = "#3D5AFE")).hasColors)
        assertFalse(ThemeSpec(cornerRadiusDp = 16).hasColors)
    }
}
