package com.komgareader.domain.render

import kotlin.test.Test
import kotlin.test.assertEquals

class HyphenationResolverTest {
    @Test fun `auto with german document resolves to de`() =
        assertEquals("de", resolveHyphenationLang(setting = "auto", docLanguage = "de"))
    @Test fun `auto normalizes a region tag`() =
        assertEquals("de", resolveHyphenationLang(setting = "auto", docLanguage = "de-DE"))
    @Test fun `auto with english resolves to en`() =
        assertEquals("en", resolveHyphenationLang(setting = "auto", docLanguage = "en-US"))
    @Test fun `auto with unsupported language falls back to off`() =
        assertEquals("", resolveHyphenationLang(setting = "auto", docLanguage = "ja"))
    @Test fun `auto with unknown document language is off`() =
        assertEquals("", resolveHyphenationLang(setting = "auto", docLanguage = ""))
    @Test fun `explicit de wins regardless of document`() =
        assertEquals("de", resolveHyphenationLang(setting = "de", docLanguage = "en"))
    @Test fun `off stays off`() =
        assertEquals("", resolveHyphenationLang(setting = "", docLanguage = "de"))

    @Test fun `auto resolves any supported bundled language`() {
        assertEquals("it", resolveHyphenationLang("auto", "it"))
        assertEquals("fr", resolveHyphenationLang("auto", "fr-FR"))
        assertEquals("ru", resolveHyphenationLang("auto", "ru-RU"))
    }

    @Test fun `auto with a bundled-but-unsupported language is off`() {
        assertEquals("", resolveHyphenationLang("auto", "ja"))
    }
}
