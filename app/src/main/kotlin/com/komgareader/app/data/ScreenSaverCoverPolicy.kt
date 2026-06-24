package com.komgareader.app.data

import com.komgareader.app.ui.reader.ViewerMode

/** Which cover the screensaver should use for a given reader type. */
enum class ScreenSaverCoverKind { SERIES, WORK }

/**
 * A Webtoon is a continuous multi-chapter strip, so a single chapter cover is arbitrary → use the
 * whole-series cover. Every other reader type (paged/comic, and EPUB/novel which opens as PAGED)
 * shows the current work's own cover.
 */
fun coverKindFor(viewerMode: ViewerMode): ScreenSaverCoverKind =
    if (viewerMode == ViewerMode.WEBTOON) ScreenSaverCoverKind.SERIES else ScreenSaverCoverKind.WORK

/**
 * Whether the server cover is too low-resolution for the standby screen and a high-res upgrade
 * (full first page / whole-file extraction) is worth fetching. True when the cover's shorter edge is
 * **below** the screen's shorter edge — i.e. whenever the standby would have to upscale it. Any
 * upscale softens on the E-Ink panel (the Onyx standby renderer dithers and downscales further), and
 * fill/crop mode scales the cover up to the full screen width, so even a "near-screen" 1067px Komga
 * thumbnail looks blurry; the full first page (native page resolution) downsamples crisply instead.
 * A non-positive [coverMinEdgePx] (no/undecodable cover) also returns true so the upgrade is
 * attempted; a non-positive [screenMinEdgePx] (unknown) returns false so we never loop on a bad metric.
 */
fun needsHighResUpgrade(coverMinEdgePx: Int, screenMinEdgePx: Int): Boolean {
    if (screenMinEdgePx <= 0) return false
    if (coverMinEdgePx <= 0) return true
    return coverMinEdgePx < screenMinEdgePx
}
