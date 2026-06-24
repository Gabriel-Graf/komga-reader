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
        // 200px cover, 1264px screen short edge -> 200 < 1264 -> upgrade.
        assertTrue(needsHighResUpgrade(coverMinEdgePx = 200, screenMinEdgePx = 1264))
    }

    @Test fun `mid-size cover still below screen needs upgrade`() {
        // Real case: a 1067px Komga thumbnail upscaled to a 1264px-wide standby looks blurry,
        // so the full first page must be fetched. This value deliberately sits in the gap the old
        // half-screen threshold missed: screenMinEdge/2 (632) < 1067 < screenMinEdge (1264), so it
        // guards the threshold move from `/2` to full-edge. 1067 < 1264 -> upgrade.
        assertTrue(needsHighResUpgrade(coverMinEdgePx = 1067, screenMinEdgePx = 1264))
    }

    @Test fun `cover at or above screen resolution does not need upgrade`() {
        // No upscale needed -> keep the cover as is.
        assertFalse(needsHighResUpgrade(coverMinEdgePx = 1264, screenMinEdgePx = 1264))
        assertFalse(needsHighResUpgrade(coverMinEdgePx = 1400, screenMinEdgePx = 1264))
    }

    @Test fun `missing or undecodable cover triggers upgrade`() {
        assertTrue(needsHighResUpgrade(coverMinEdgePx = 0, screenMinEdgePx = 1264))
    }

    @Test fun `unknown screen size never forces an upgrade loop`() {
        assertFalse(needsHighResUpgrade(coverMinEdgePx = 200, screenMinEdgePx = 0))
    }
}
