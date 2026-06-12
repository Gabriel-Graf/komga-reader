package com.komgareader.app.ui.pack

import androidx.compose.ui.unit.dp
import com.komgareader.domain.model.UiPackSpec
import com.komgareader.ui.icons.DefaultIconPack
import com.komgareader.ui.icons.IconKey
import com.komgareader.ui.shell.ShellNavStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UiPackApplyTest {
    private fun spec(
        navStyle: String? = null,
        iconRemap: Map<String, String> = emptyMap(),
        accentHex: String? = null,
        cornerRadiusDp: Int? = null,
    ) = UiPackSpec("pkg", "Pack", abiVersion = 2, navStyle, iconRemap, accentHex, cornerRadiusDp)

    @Test fun toIconPackMapptGueltigeNamen() {
        val pack = spec(iconRemap = mapOf("Home" to "Library")).toIconPack()!!
        // Home-Semantik soll jetzt den Library-Glyphen tragen.
        assertEquals(DefaultIconPack.resolve(IconKey.Library), pack.resolve(IconKey.Home))
        // Nicht remappte Keys → null (Host fällt auf DefaultIconPack zurück).
        assertNull(pack.resolve(IconKey.Settings))
    }

    @Test fun toIconPackUeberspringtUngueltigeNamen() {
        val pack = spec(iconRemap = mapOf("Home" to "Library", "Nonsense" to "Home", "Settings" to "Quatsch"))
            .toIconPack()!!
        assertEquals(DefaultIconPack.resolve(IconKey.Library), pack.resolve(IconKey.Home))
        assertNull(pack.resolve(IconKey.Settings)) // ungültiger Wert verworfen
    }

    @Test fun toIconPackLeerGibtNull() {
        assertNull(spec(iconRemap = emptyMap()).toIconPack())
        assertNull(spec(iconRemap = mapOf("Quatsch" to "Bloedsinn")).toIconPack())
    }

    @Test fun shellOverrideMapptNavStyle() {
        assertEquals(ShellNavStyle.DRAWER, spec(navStyle = "DRAWER").shellOverride()!!.navStyle)
        assertEquals(ShellNavStyle.BOTTOM_BAR, spec(navStyle = "BOTTOM_BAR").shellOverride()!!.navStyle)
    }

    @Test fun shellOverrideUngueltigGibtNull() {
        assertNull(spec(navStyle = "SIDE_RAIL").shellOverride())
        assertNull(spec(navStyle = null).shellOverride())
    }

    @Test fun tokenOverrideParstHexUndRadius() {
        val to = spec(accentHex = "#3A5BC7", cornerRadiusDp = 4).tokenOverride()!!
        assertEquals(4.dp, to.cornerRadius)
        assertEquals(parseHexColor("#3A5BC7"), to.accent)
    }

    @Test fun tokenOverrideUngueltigerHexGibtNurRadius() {
        val to = spec(accentHex = "nope", cornerRadiusDp = 8).tokenOverride()!!
        assertNull(to.accent)
        assertEquals(8.dp, to.cornerRadius)
    }

    @Test fun tokenOverrideKomplettLeerGibtNull() {
        assertNull(spec().tokenOverride())
        assertNull(spec(accentHex = "kaputt").tokenOverride())
    }

    @Test fun tokenOverrideClamptRadius() {
        assertEquals(32.dp, spec(cornerRadiusDp = 999).tokenOverride()!!.cornerRadius)
        assertEquals(0.dp, spec(cornerRadiusDp = -5).tokenOverride()!!.cornerRadius)
    }

    @Test fun parseHexColorAkzeptiertBeideFormen() {
        assertEquals(parseHexColor("#FFFFFF"), parseHexColor("FFFFFF"))
        assertNull(parseHexColor("#FFF"))
        assertNull(parseHexColor("ZZZZZZ"))
    }
}
