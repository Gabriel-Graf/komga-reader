package com.komgareader.data.plugin

import com.komgareader.domain.render.FontSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FontSpecParserTest {
    @Test fun parsesMultipleSpecs() {
        val json = """
            [
              {"family":"Lora","label":"Lora","asset":"fonts/Lora.ttf","license":"OFL-1.1"},
              {"family":"EB Garamond","label":"EB Garamond","asset":"fonts/EBGaramond.ttf","license":"OFL-1.1"}
            ]
        """.trimIndent()
        val result = parseFontSpecs(json, manifestAbi = 2)
        assertEquals(
            listOf(
                FontSpec("Lora", "Lora", "fonts/Lora.ttf", "OFL-1.1"),
                FontSpec("EB Garamond", "EB Garamond", "fonts/EBGaramond.ttf", "OFL-1.1"),
            ),
            result,
        )
    }

    @Test fun labelFallsBackToFamily() {
        val result = parseFontSpecs("""[{"family":"Lora","asset":"fonts/Lora.ttf"}]""", 2)
        assertEquals(listOf(FontSpec("Lora", "Lora", "fonts/Lora.ttf", "")), result)
    }

    @Test fun skipsEntriesMissingFamilyOrAsset() {
        val json = """
            [
              {"label":"No family","asset":"fonts/x.ttf"},
              {"family":"NoAsset"},
              {"family":"Good","asset":"fonts/g.ttf"}
            ]
        """.trimIndent()
        assertEquals(listOf(FontSpec("Good", "Good", "fonts/g.ttf", "")), parseFontSpecs(json, 2))
    }

    @Test fun emptyArrayYieldsEmptyList() {
        assertEquals(emptyList(), parseFontSpecs("[]", 2))
    }

    @Test fun brokenJsonYieldsNull() {
        assertNull(parseFontSpecs("not json", 2))
        assertNull(parseFontSpecs("""{"family":"x"}""", 2)) // top-level object, not array
    }
}
