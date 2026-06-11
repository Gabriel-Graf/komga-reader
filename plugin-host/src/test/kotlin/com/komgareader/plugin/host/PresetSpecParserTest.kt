package com.komgareader.plugin.host

import com.komgareader.plugin.ColorPresetSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PresetSpecParserTest {

    private val abi = 1

    @Test
    fun parsesArrayOfSpecs() {
        val json = """
            [
              {"abiVersion":1,"name":"Kindle Sepia","saturation":0.9,"contrast":1.1,"brightness":0.05},
              {"abiVersion":1,"name":"Kindle Mono","saturation":0.5,"contrast":1.2,"brightness":0.0}
            ]
        """.trimIndent()
        val specs = parsePresetSpecs(json, abi)
        assertEquals(
            listOf(
                ColorPresetSpec(1, "Kindle Sepia", 0.9f, 1.1f, 0.05f),
                ColorPresetSpec(1, "Kindle Mono", 0.5f, 1.2f, 0.0f),
            ),
            specs,
        )
    }

    @Test
    fun usesDiscoveredAbiWhenEntryOmitsIt() {
        val specs = parsePresetSpecs("""[{"name":"X","saturation":1.0,"contrast":1.0,"brightness":0.0}]""", abi)
        assertEquals(1, specs!!.single().abiVersion)
    }

    @Test
    fun emptyArrayParsesToEmptyList() {
        assertEquals(emptyList(), parsePresetSpecs("[]", abi))
    }

    @Test
    fun malformedJsonReturnsNull() {
        assertNull(parsePresetSpecs("not json", abi))
        assertNull(parsePresetSpecs("""{"name":"obj-not-array"}""", abi))
    }

    @Test
    fun entryMissingRequiredFieldIsSkipped() {
        val json = """[{"saturation":1.0,"contrast":1.0,"brightness":0.0},{"name":"Ok","saturation":1.0,"contrast":1.0,"brightness":0.0}]"""
        val specs = parsePresetSpecs(json, abi)
        assertTrue(specs!!.single().name == "Ok")
    }
}
