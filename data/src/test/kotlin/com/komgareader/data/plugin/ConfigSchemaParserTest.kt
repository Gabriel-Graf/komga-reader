package com.komgareader.data.plugin

import com.komgareader.plugin.FieldType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfigSchemaParserTest {
    @Test
    fun parses_number_field_with_range() {
        val json = """{"fields":[{"key":"min_confidence","label":"Mindest-Confidence","type":"NUMBER","min":0.1,"max":1.0,"step":0.05,"default":"0.25"}]}"""
        val schema = parseConfigSchema(json)!!
        assertEquals(1, schema.fields.size)
        val f = schema.fields[0]
        assertEquals(FieldType.NUMBER, f.type)
        assertEquals("min_confidence", f.key)
        assertEquals(0.1, f.min); assertEquals(1.0, f.max); assertEquals(0.05, f.step)
        assertEquals("0.25", f.default)
    }

    @Test
    fun unknown_type_is_skipped() {
        val json = """{"fields":[{"key":"x","label":"X","type":"BOGUS"},{"key":"y","label":"Y","type":"BOOL"}]}"""
        val schema = parseConfigSchema(json)!!
        assertEquals(1, schema.fields.size)
        assertEquals("y", schema.fields[0].key)
    }

    @Test
    fun malformed_json_returns_null() {
        assertNull(parseConfigSchema("not json"))
    }

    @Test
    fun empty_fields_yields_empty_schema() {
        val schema = parseConfigSchema("""{"fields":[]}""")!!
        assertTrue(schema.fields.isEmpty())
    }
}
