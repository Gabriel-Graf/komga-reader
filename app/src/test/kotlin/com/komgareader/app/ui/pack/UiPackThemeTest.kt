package com.komgareader.app.ui.pack

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.komgareader.domain.model.ColorRolesSpec
import com.komgareader.domain.model.ThemeSpec
import com.komgareader.domain.model.UiPackSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UiPackThemeTest {
    private fun spec(theme: ThemeSpec?) = UiPackSpec("p", "Aurora", 2, theme = theme)

    @Test fun `kein theme - null`() {
        assertNull(spec(null).toUiPackOrNull())
        assertNull(spec(ThemeSpec(cornerRadiusDp = 16)).toUiPackOrNull())
    }

    @Test fun `voller pack baut ColorScheme + Shapes + Tokens`() {
        val pack = spec(
            ThemeSpec(
                dark = ColorRolesSpec(background = "#15171C", surface = "#1C1F26", navDock = "#1C1F26", accent = "#3D5AFE", onAccent = "#FFFFFF"),
                light = ColorRolesSpec(background = "#CDD1D9", navDock = "#959CAA", accent = "#3D5AFE"),
                cornerRadiusDp = 16, elevation = true,
            ),
        ).toUiPackOrNull()!!
        val csD = pack.colorScheme(dark = true)
        assertEquals(Color(0xFF15171C), csD.background)
        assertEquals(Color(0xFF1C1F26), csD.surface)
        assertEquals(Color(0xFF1C1F26), csD.surfaceVariant)
        assertEquals(Color(0xFF3D5AFE), csD.primary)
        assertEquals(Color(0xFF959CAA), pack.colorScheme(dark = false).surfaceVariant)
        assertEquals(RoundedCornerShape(16.dp), pack.shapes.medium)
        val t = pack.designTokens(dark = true)
        assertEquals(Color(0xFF3D5AFE), t.accent)
        assertEquals(16.dp, t.cornerRadius)
        assertTrue(t.usesShadows)
    }

    @Test fun `nur-dark spec mirror't in light`() {
        val pack = spec(ThemeSpec(dark = ColorRolesSpec(background = "#15171C", accent = "#3D5AFE"))).toUiPackOrNull()!!
        assertEquals(Color(0xFF15171C), pack.colorScheme(dark = false).background)
    }
}
