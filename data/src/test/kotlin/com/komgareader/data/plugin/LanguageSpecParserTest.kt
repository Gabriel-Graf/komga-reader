package com.komgareader.data.plugin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LanguageSpecParserTest {
    private val abi = 2

    @Test fun parsesLanguage() {
        val json = """
            {"abiVersion":2,"code":"es","name":"Español",
             "strings":{"appName":"Komga Reader","libraryTitle":"Biblioteca",
                        "downloadingChapters":"Cargando {count} capítulos…"}}
        """.trimIndent()
        val spec = parseLanguageSpec(json, abi)!!
        assertEquals("es", spec.code)
        assertEquals("Español", spec.name)
        assertEquals("Biblioteca", spec.strings["libraryTitle"])
        assertEquals("Cargando {count} capítulos…", spec.strings["downloadingChapters"])
    }

    @Test fun nullWhenCodeMissing() {
        assertNull(parseLanguageSpec("""{"name":"X","strings":{}}""", abi))
    }

    @Test fun nullWhenNameMissing() {
        assertNull(parseLanguageSpec("""{"code":"es","strings":{}}""", abi))
    }

    @Test fun nullWhenStringsMissing() {
        assertNull(parseLanguageSpec("""{"code":"es","name":"Español"}""", abi))
    }

    @Test fun nullWhenNotObject() {
        assertNull(parseLanguageSpec("[]", abi))
    }

    @Test fun skipsNonStringValuesInStrings() {
        val spec = parseLanguageSpec("""{"code":"es","name":"X","strings":{"a":"ok","b":99}}""", abi)!!
        assertEquals(mapOf("a" to "ok"), spec.strings)
    }
}
