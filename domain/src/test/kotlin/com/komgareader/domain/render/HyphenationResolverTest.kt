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
        assertEquals("", resolveHyphenationLang(setting = "auto", docLanguage = "fr"))
    @Test fun `auto with unknown document language is off`() =
        assertEquals("", resolveHyphenationLang(setting = "auto", docLanguage = ""))
    @Test fun `explicit de wins regardless of document`() =
        assertEquals("de", resolveHyphenationLang(setting = "de", docLanguage = "en"))
    @Test fun `off stays off`() =
        assertEquals("", resolveHyphenationLang(setting = "", docLanguage = "de"))
}
