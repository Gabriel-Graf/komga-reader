package com.komgareader.domain.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HyphenationModeTest {
    @Test fun `auto value is AUTO mode`() = assertEquals(HyphenationMode.AUTO, hyphenationModeOf("auto"))
    @Test fun `empty value is OFF mode`() = assertEquals(HyphenationMode.OFF, hyphenationModeOf(""))
    @Test fun `a language code is LANGUAGE mode`() = assertEquals(HyphenationMode.LANGUAGE, hyphenationModeOf("it"))

    @Test fun `supported list contains the bundled languages`() {
        assertEquals(24, HyphenationLanguages.SUPPORTED.size)
        assertTrue("de" in HyphenationLanguages.SUPPORTED)
        assertTrue("it" in HyphenationLanguages.SUPPORTED)
    }
}
