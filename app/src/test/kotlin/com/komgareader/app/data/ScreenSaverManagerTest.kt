package com.komgareader.app.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Unit tests for [fitCentered] — the screensaver letterbox math. Root cause of the "custom/cover
 * image doesn't show on standby" bug: the Onyx screensaver only accepts an image at the exact device
 * resolution, so a native-size cover (720x1024) must be fit-centered onto a screen-sized canvas.
 */
class ScreenSaverManagerTest {

    @Test
    fun `a screen-sized image fills the whole target`() {
        val r = fitCentered(srcW = 1264, srcH = 1680, dstW = 1264, dstH = 1680)
        assertEquals(FitRect(0, 0, 1264, 1680), r)
    }

    @Test
    fun `a tall narrow cover is height-fit and horizontally centered`() {
        // 720x1024 into 1264x1680: scale = min(1264/720, 1680/1024) = 1680/1024 = 1.640625
        // width = 720 * 1.640625 = 1181 (truncated), height = 1024 * 1.640625 = 1680
        // left = (1264 - 1181) / 2 = 41, top = (1680 - 1680) / 2 = 0
        val r = fitCentered(srcW = 720, srcH = 1024, dstW = 1264, dstH = 1680)
        assertEquals(FitRect(41, 0, 41 + 1181, 1680), r)
    }

    @Test
    fun `a wide image is width-fit and vertically centered`() {
        // 1000x500 into 1264x1680: scale = min(1264/1000=1.264, 1680/500=3.36) = 1.264
        // width = 1000 * 1.264 = 1264, height = 500 * 1.264 = 632
        // left = 0, top = (1680 - 632) / 2 = 524
        val r = fitCentered(srcW = 1000, srcH = 500, dstW = 1264, dstH = 1680)
        assertEquals(FitRect(0, 524, 1264, 524 + 632), r)
    }

    @Test
    fun `a degenerate source falls back to the full target`() {
        assertEquals(FitRect(0, 0, 1264, 1680), fitCentered(srcW = 0, srcH = 1024, dstW = 1264, dstH = 1680))
    }

    @Test
    fun `cover mode fills the width and crops top and bottom for a tall cover`() {
        // 720x1024 cover into 1264x1680: scale = max(1264/720=1.7556, 1680/1024=1.6406) = 1.7556
        // width = 720 * 1.7556 = 1264, height = 1024 * 1.7556 = 1797 (truncated)
        // left = 0, top = (1680 - 1797) / 2 = -58 (Int division toward zero) -> rect overflows, gets clipped
        val r = fitCentered(srcW = 720, srcH = 1024, dstW = 1264, dstH = 1680, cover = true)
        assertEquals(FitRect(0, -58, 1264, -58 + 1797), r)
    }

    @Test
    fun `cover mode fills the height and crops left and right for a wide image`() {
        // 2000x1000 cover into 1264x1680: scale = max(1264/2000=0.632, 1680/1000=1.68) = 1.68
        // width = 2000 * 1.68 = 3360, height = 1000 * 1.68 = 1680
        // left = (1264 - 3360) / 2 = -1048, top = 0 -> overflows horizontally, gets clipped
        val r = fitCentered(srcW = 2000, srcH = 1000, dstW = 1264, dstH = 1680, cover = true)
        assertEquals(FitRect(-1048, 0, -1048 + 3360, 1680), r)
    }
}
