package com.komgareader.data.plugin

import com.komgareader.domain.model.ReaderPreset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderPresetParserTest {
    private val abi = 2

    @Test fun parsesPartialPreset() {
        val json = """
            [{"abiVersion":2,"name":"Roman komfortabel",
              "settings":{"novelFontSizeEm":1.2,"novelLineHeight":1.4,"novelMarginPreset":"WIDE"}}]
        """.trimIndent()
        val presets = parseReaderPresetSpecs(json, abi)
        assertEquals(1, presets!!.size)
        val p = presets.single()
        assertEquals("Roman komfortabel", p.name)
        assertEquals(1.2f, p.overrides.novelFontSizeEm)
        assertEquals(1.4f, p.overrides.novelLineHeight)
        assertEquals("WIDE", p.overrides.novelMarginPreset)
        assertNull(p.overrides.displayMode)        // nicht gesetzt → null
        assertNull(p.overrides.webtoonOverlapPercent)
    }

    @Test fun parsesAllFields() {
        val json = """
            [{"abiVersion":2,"name":"Voll","settings":{
              "displayMode":"EINK","deviceManagedRefresh":false,"webtoonOverlapPercent":30,
              "novelFontSizeEm":1.0,"novelLineHeight":1.0,"novelMarginPreset":"NORMAL",
              "novelFontFamily":"DejaVu Sans","novelTextAlign":"JUSTIFY","novelHyphenationLang":"de",
              "novelFontWeight":400,"guidedPanelOverlay":true}}]
        """.trimIndent()
        val o = parseReaderPresetSpecs(json, abi)!!.single().overrides
        assertEquals("EINK", o.displayMode)
        assertEquals(false, o.deviceManagedRefresh)
        assertEquals(30, o.webtoonOverlapPercent)
        assertEquals(400, o.novelFontWeight)
        assertEquals(true, o.guidedPanelOverlay)
    }

    @Test fun skipsEntryWithoutName() {
        val json = """[{"settings":{"novelFontSizeEm":1.0}}]"""
        assertTrue(parseReaderPresetSpecs(json, abi)!!.isEmpty())
    }

    @Test fun nullWhenNotArray() {
        assertNull(parseReaderPresetSpecs("{}", abi))
    }

    @Test fun emptyArrayParsesToEmptyList() {
        assertEquals(emptyList(), parseReaderPresetSpecs("[]", abi))
    }

    @Test fun ignoresUnknownAndWrongTypeKeys() {
        val json = """[{"name":"X","settings":{"bogus":1,"novelFontSizeEm":"notanumber","novelLineHeight":1.3}}]"""
        val o = parseReaderPresetSpecs(json, abi)!!.single().overrides
        assertNull(o.novelFontSizeEm)              // falscher Typ → übersprungen
        assertEquals(1.3f, o.novelLineHeight)
    }
}
