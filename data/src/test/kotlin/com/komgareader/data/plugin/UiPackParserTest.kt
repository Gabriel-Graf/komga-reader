package com.komgareader.data.plugin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UiPackParserTest {
    private val pkg = "com.example.uipack"
    private val name = "Beispiel-Pack"
    private val abi = 2

    @Test fun parstVollenPack() {
        val json = """
            {"abiVersion":2,
             "shell":{"navStyle":"DRAWER"},
             "icons":{"Home":"Library","Settings":"Palette"},
             "theme":{"accent":"#3A5BC7","cornerRadius":4}}
        """.trimIndent()
        val spec = parseUiPackSpec(json, pkg, name, abi)!!
        assertEquals(pkg, spec.packageName)
        assertEquals(name, spec.displayName)
        assertEquals("DRAWER", spec.navStyle)
        assertEquals(mapOf("Home" to "Library", "Settings" to "Palette"), spec.iconRemap)
        assertEquals("#3A5BC7", spec.accentHex)
        assertEquals(4, spec.cornerRadiusDp)
        assertTrue(spec.hasAnyOverride)
    }

    @Test fun nurIconsSektion() {
        val spec = parseUiPackSpec("""{"icons":{"Home":"Library"}}""", pkg, name, abi)!!
        assertEquals(mapOf("Home" to "Library"), spec.iconRemap)
        assertNull(spec.navStyle)
        assertNull(spec.accentHex)
        assertNull(spec.cornerRadiusDp)
        assertTrue(spec.hasAnyOverride)
    }

    @Test fun nurShellSektion() {
        val spec = parseUiPackSpec("""{"shell":{"navStyle":"BOTTOM_BAR"}}""", pkg, name, abi)!!
        assertEquals("BOTTOM_BAR", spec.navStyle)
        assertTrue(spec.iconRemap.isEmpty())
        assertTrue(spec.hasAnyOverride)
    }

    @Test fun nurThemeSektion() {
        val spec = parseUiPackSpec("""{"theme":{"cornerRadius":8}}""", pkg, name, abi)!!
        assertEquals(8, spec.cornerRadiusDp)
        assertNull(spec.accentHex)
        assertTrue(spec.hasAnyOverride)
    }

    @Test fun leererPackOhneUeberschreibung() {
        val spec = parseUiPackSpec("{}", pkg, name, abi)!!
        assertFalse(spec.hasAnyOverride)
    }

    @Test fun kaputtesJsonGibtNull() {
        assertNull(parseUiPackSpec("nicht json", pkg, name, abi))
        assertNull(parseUiPackSpec("[1,2,3]", pkg, name, abi))
    }

    @Test fun abiAusserhalbDerSpanneGibtNull() {
        // PluginAbi.VERSION = 2 — ABI 3 ist (noch) nicht unterstützt.
        assertNull(parseUiPackSpec("""{"abiVersion":3,"shell":{"navStyle":"DRAWER"}}""", pkg, name, abi))
        // ABI 0 liegt unter MIN_SUPPORTED.
        assertNull(parseUiPackSpec("""{"abiVersion":0,"icons":{"Home":"Library"}}""", pkg, name, abi))
    }

    @Test fun fehlendesAbiErbtManifestWert() {
        val spec = parseUiPackSpec("""{"shell":{"navStyle":"DRAWER"}}""", pkg, name, manifestAbi = 1)!!
        assertEquals(1, spec.abiVersion)
    }

    @Test fun nichtStringIconWerteWerdenUebersprungen() {
        val spec = parseUiPackSpec(
            """{"icons":{"Home":"Library","Bad":42,"Empty":""}}""",
            pkg,
            name,
            abi,
        )!!
        assertEquals(mapOf("Home" to "Library"), spec.iconRemap)
    }
}
