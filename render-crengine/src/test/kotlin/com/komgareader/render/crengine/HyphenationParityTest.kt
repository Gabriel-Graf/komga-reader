package com.komgareader.render.crengine

import com.komgareader.domain.render.HyphenationLanguages
import kotlin.test.Test
import kotlin.test.assertEquals

class HyphenationParityTest {
    @Test fun `pattern dict codes match the domain supported list`() {
        assertEquals(HyphenationLanguages.SUPPORTED.toSet(), ReflowCss.PATTERN_DICTS.keys)
    }
    @Test fun `extraction list equals the pattern files`() {
        assertEquals(ReflowCss.PATTERN_DICTS.values.toSet(), CrengineDocumentFactory.hyphPatternFiles().toSet())
    }
}
