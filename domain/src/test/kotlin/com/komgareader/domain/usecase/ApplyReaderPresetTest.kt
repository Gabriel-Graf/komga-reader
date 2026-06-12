package com.komgareader.domain.usecase

import com.komgareader.domain.model.ReaderPresetOverrides
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplyReaderPresetTest {
    @Test fun appliesOnlyNonNullFields() {
        val applied = linkedMapOf<String, Any>()
        val sink = ReaderPresetSink(
            setDisplayMode = { applied["displayMode"] = it },
            setDeviceManagedRefresh = { applied["deviceManagedRefresh"] = it },
            setWebtoonOverlapPercent = { applied["webtoonOverlapPercent"] = it },
            setNovelFontSizeEm = { applied["novelFontSizeEm"] = it },
            setNovelLineHeight = { applied["novelLineHeight"] = it },
            setNovelMarginPreset = { applied["novelMarginPreset"] = it },
            setNovelFontFamily = { applied["novelFontFamily"] = it },
            setNovelTextAlign = { applied["novelTextAlign"] = it },
            setNovelHyphenationLang = { applied["novelHyphenationLang"] = it },
            setNovelFontWeight = { applied["novelFontWeight"] = it },
            setGuidedPanelOverlay = { applied["guidedPanelOverlay"] = it },
        )
        applyReaderPreset(ReaderPresetOverrides(novelFontSizeEm = 1.2f, novelMarginPreset = "WIDE"), sink)
        assertEquals<Map<String, Any>>(mapOf("novelFontSizeEm" to 1.2f, "novelMarginPreset" to "WIDE"), applied)
    }
}
