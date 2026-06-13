package com.komgareader.data.plugin

import com.komgareader.domain.model.UiPackSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UiPackParserThemeTest {
    private fun parse(json: String): UiPackSpec? = parseUiPackSpec(json, "pkg", "Name", manifestAbi = 2)

    @Test fun `volle theme-sektion light+dark+typo`() {
        val s = parse(
            """{"abiVersion":2,"theme":{"cornerRadius":16,"elevation":true,
               "typography":{"headlineWeight":700,"headlineTrackingEm":-0.02},
               "light":{"background":"#CDD1D9","navDock":"#959CAA","accent":"#3D5AFE"},
               "dark":{"background":"#15171C","surface":"#1C1F26","accent":"#3D5AFE"}}}""",
        )!!
        assertEquals("#CDD1D9", s.theme!!.light!!.background)
        assertEquals("#959CAA", s.theme!!.light!!.navDock)
        assertEquals("#15171C", s.theme!!.dark!!.background)
        assertEquals(16, s.theme!!.cornerRadiusDp)
        assertEquals(true, s.theme!!.elevation)
        assertEquals(700, s.theme!!.typography!!.headlineWeight)
        assertEquals(-0.02f, s.theme!!.typography!!.headlineTrackingEm)
    }

    @Test fun `nur dark - light bleibt null`() {
        val s = parse("""{"abiVersion":2,"theme":{"dark":{"background":"#15171C"}}}""")!!
        assertNull(s.theme!!.light)
        assertEquals("#15171C", s.theme!!.dark!!.background)
    }

    @Test fun `legacy flach - theme bleibt null, accent gesetzt`() {
        val s = parse("""{"abiVersion":2,"theme":{"accent":"#3D5AFE","cornerRadius":4}}""")!!
        assertNull(s.theme)
        assertEquals("#3D5AFE", s.accentHex)
        assertEquals(4, s.cornerRadiusDp)
    }
}
