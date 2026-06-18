package com.komgareader.app.data

import com.komgareader.app.ui.reader.ViewerMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScreenSaverCoverPolicyTest {

    @Test fun `webtoon uses the series cover`() {
        assertEquals(ScreenSaverCoverKind.SERIES, coverKindFor(ViewerMode.WEBTOON))
    }

    @Test fun `paged and comic use the work cover`() {
        assertEquals(ScreenSaverCoverKind.WORK, coverKindFor(ViewerMode.PAGED))
        assertEquals(ScreenSaverCoverKind.WORK, coverKindFor(ViewerMode.COMIC))
    }

    @Test fun `cover far below screen needs upgrade`() {
        // 200px cover, 1264px screen short edge -> 200 < 632 -> upgrade.
        assertTrue(needsHighResUpgrade(coverMinEdgePx = 200, screenMinEdgePx = 1264))
    }

    @Test fun `cover near screen resolution does not need upgrade`() {
        // 720px cover, 1264px screen -> 720 >= 632 -> keep.
        assertFalse(needsHighResUpgrade(coverMinEdgePx = 720, screenMinEdgePx = 1264))
    }

    @Test fun `missing or undecodable cover triggers upgrade`() {
        assertTrue(needsHighResUpgrade(coverMinEdgePx = 0, screenMinEdgePx = 1264))
    }

    @Test fun `unknown screen size never forces an upgrade loop`() {
        assertFalse(needsHighResUpgrade(coverMinEdgePx = 200, screenMinEdgePx = 0))
    }
}
