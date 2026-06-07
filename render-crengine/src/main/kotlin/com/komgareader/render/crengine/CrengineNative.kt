package com.komgareader.render.crengine

import android.graphics.Bitmap

/**
 * Thin JNI surface over crengine-ng (Phase 1c spike). Just enough to open an
 * EPUB from memory and rasterise one reflowed page into a Bitmap. The full
 * ReflowableDocument seam comes in a later phase.
 */
object CrengineNative {

    init {
        System.loadLibrary("cr3bridge")
    }

    /** Initialise the font manager and register one TTF font (path on disk). */
    external fun nativeInit(fontPath: String): Boolean

    /** Open a document from bytes; returns an opaque handle (0 = failure). */
    external fun nativeOpen(bytes: ByteArray, formatHint: String): Long

    /** Reflow at [width]x[height], go to [pageIndex], rasterise into [dst]. */
    external fun nativeRenderPage(
        handle: Long,
        pageIndex: Int,
        width: Int,
        height: Int,
        dst: Bitmap,
    )

    external fun nativeClose(handle: Long)
}
