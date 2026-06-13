package com.komgareader.data.eink

import com.komgareader.domain.eink.EinkContext
import com.komgareader.domain.eink.EinkContextProfile
import kotlin.test.Test
import kotlin.test.assertEquals

class EinkContextProfilesCodecTest {

    @Test
    fun `round-trips a full map`() {
        val map = mapOf(
            EinkContext.PAGED to EinkContextProfile("hd", "system"),
            EinkContext.WEBTOON to EinkContextProfile("speed", null),
        )
        assertEquals(map, decodeEinkContextProfiles(encodeEinkContextProfiles(map)))
    }

    @Test
    fun `blank or invalid json decodes to empty map`() {
        assertEquals(emptyMap<EinkContext, EinkContextProfile>(), decodeEinkContextProfiles(null))
        assertEquals(emptyMap<EinkContext, EinkContextProfile>(), decodeEinkContextProfiles(""))
        assertEquals(emptyMap<EinkContext, EinkContextProfile>(), decodeEinkContextProfiles("not json"))
    }

    @Test
    fun `unknown context key is skipped, not fatal`() {
        val json = """{"PAGED":{"refreshModeId":"hd"},"BOGUS":{"refreshModeId":"x"}}"""
        val decoded = decodeEinkContextProfiles(json)
        assertEquals(setOf(EinkContext.PAGED), decoded.keys)
    }
}
