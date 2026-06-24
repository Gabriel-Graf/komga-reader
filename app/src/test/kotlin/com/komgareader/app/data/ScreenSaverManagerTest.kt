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
    fun `cover mode does not upscale a sub-screen cover - stays native and centered`() {
        // The real regression case: a 1067x1600 manga page on a 1264x1680 standby. Cover fill would be
        // max(1264/1067=1.1846, 1680/1600=1.05) = 1.1846, but upscaling only blurs -> capped at 1.0.
        // At native size both edges fit (1067<1264, 1600<1680) so it is centered, not cropped, and SHARP.
        // left = (1264-1067)/2 = 98, top = (1680-1600)/2 = 40.
        val r = fitCentered(srcW = 1067, srcH = 1600, dstW = 1264, dstH = 1680, cover = true)
        assertEquals(FitRect(98, 40, 98 + 1067, 40 + 1600), r)
    }

    @Test
    fun `cover mode downscales a high-res tall cover to fill the width and crops only the bottom`() {
        // 2000x3056 cover into 1264x1680: scale = max(1264/2000=0.632, 1680/3056=0.5497) = 0.632 (<=1, no cap)
        // width = 2000 * 0.632 = 1264, height = 3056 * 0.632 = 1931 (truncated)
        // left = 0, top = 0 (top-aligned so the title stays visible) -> overflow crops the bottom.
        val r = fitCentered(srcW = 2000, srcH = 3056, dstW = 1264, dstH = 1680, cover = true)
        assertEquals(FitRect(0, 0, 1264, 1931), r)
    }

    @Test
    fun `cover mode downscales a high-res wide cover to fill the height and crops the sides`() {
        // 3000x3360 cover into 1264x1680: scale = max(1264/3000=0.4213, 1680/3360=0.5) = 0.5 (<=1, no cap)
        // width = 3000 * 0.5 = 1500, height = 3360 * 0.5 = 1680
        // Height fits exactly (h == dstH), so top = 0 comes from the centering branch (NOT a bottom crop);
        // width overflows -> left = (1264 - 1500) / 2 = -118, the sides get clipped.
        val r = fitCentered(srcW = 3000, srcH = 3360, dstW = 1264, dstH = 1680, cover = true)
        assertEquals(FitRect(-118, 0, -118 + 1500, 1680), r)
    }

    @Test
    fun `cover mode at exactly native screen width fills and crops the bottom`() {
        // Cap boundary: a cover whose width equals the screen short edge needs no scaling (scale = 1.0).
        // 1264x1896 -> scale = max(1264/1264=1.0, 1680/1896=0.886) = 1.0 -> native, fills width.
        // height 1896 > 1680 -> top = 0, bottom cropped.
        val r = fitCentered(srcW = 1264, srcH = 1896, dstW = 1264, dstH = 1680, cover = true)
        assertEquals(FitRect(0, 0, 1264, 1896), r)
    }
}
