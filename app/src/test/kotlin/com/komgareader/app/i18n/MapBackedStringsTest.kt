package com.komgareader.app.i18n

import kotlin.test.Test
import kotlin.test.assertEquals

class MapBackedStringsTest {
    @Test fun overrideWins() {
        val s = MapBackedStrings(mapOf("libraryTitle" to "Biblioteca"), StringsEn)
        assertEquals("Biblioteca", s.libraryTitle)
    }

    @Test fun missingKeyFallsBackToEnglish() {
        val s = MapBackedStrings(mapOf("libraryTitle" to "Biblioteca"), StringsEn)
        assertEquals(StringsEn.appName, s.appName)   // nicht überschrieben → EN
    }

    @Test fun functionTemplateInterpolates() {
        val s = MapBackedStrings(mapOf("downloadingChapters" to "Cargando {count} capítulos…"), StringsEn)
        assertEquals("Cargando 3 capítulos…", s.downloadingChapters(3))
    }

    @Test fun functionFallsBackWhenAbsent() {
        val s = MapBackedStrings(emptyMap(), StringsEn)
        assertEquals(StringsEn.downloadingChapters(5), s.downloadingChapters(5))
    }
}
