package com.komgareader.app.i18n

import com.komgareader.data.plugin.LanguageSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class LanguageResolverTest {
    @Test fun builtinDe() { assertSame(StringsDe, resolveStrings("de", emptyList())) }
    @Test fun builtinEn() { assertSame(StringsEn, resolveStrings("en", emptyList())) }

    @Test fun pluginLanguageBuildsMapBacked() {
        val es = LanguageSpec("es", "Español", 2, mapOf("libraryTitle" to "Biblioteca"))
        val s = resolveStrings("es", listOf(es))
        assertEquals("Biblioteca", s.libraryTitle)
        assertEquals(StringsEn.appName, s.appName)   // Fallback EN
    }

    @Test fun unknownCodeFallsBackToEnglish() {
        assertSame(StringsEn, resolveStrings("zz", emptyList()))
    }
}
