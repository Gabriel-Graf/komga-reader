package com.komgareader.render.crengine

import android.graphics.Bitmap

/**
 * JNI-Oberfläche über crengine-ng. Öffnet ein EPUB aus dem Speicher, schichtet
 * es über [nativeApplyLayout] mit Schrift/Properties/CSS/Trennung um und liefert
 * die ReflowableDocument-Bausteine: Seitenzahl, TOC, stabile xpointer-Anker,
 * Prozent-Navigation und Volltextsuche.
 *
 * Die TOC- und Suchergebnisse werden als ein String mit ASCII-Separatoren
 * (Field 0x1F, Record 0x1E) serialisiert und in [CrengineDocument] geparst.
 */
object CrengineNative {

    init {
        System.loadLibrary("cr3bridge")
    }

    /**
     * Initialise the font manager, register every bundled TTF in [fontPaths] (each
     * by its real family name), and load the hyphenation pattern dictionaries from
     * [hyphDir] (a directory with a trailing '/' holding the extracted `*.pattern`
     * files). Returns true once at least one font is usable.
     */
    external fun nativeInit(fontPaths: Array<String>, hyphDir: String): Boolean

    /** Registered font face names, RECORD_SEP-separated ("" if none). */
    external fun nativeFontFaces(): String

    /**
     * Register a single font at [absolutePath] at runtime (post-boot), so an
     * installed font plugin becomes usable WITHOUT an app restart. Returns true
     * once the manager holds at least one usable font; re-registering the same
     * path is a benign no-op.
     */
    external fun nativeAddFont(absolutePath: String): Boolean

    /** Activate a hyphenation dictionary by id (e.g. "hyph-de-1996.pattern"); true if reachable. */
    external fun nativeActivateDictionary(id: String): Boolean

    /** Open a document from bytes; returns an opaque handle (0 = failure). */
    external fun nativeOpen(bytes: ByteArray, formatHint: String): Long

    /**
     * Register the font, set the base font size (px), apply the [keys]/[vals]
     * engine properties + [userCss], then Render at [width]x[height]. Page count
     * reflects the layout afterwards.
     */
    external fun nativeApplyLayout(
        handle: Long,
        width: Int,
        height: Int,
        fontSizePx: Int,
        keys: Array<String>,
        vals: Array<String>,
        userCss: String,
    )

    /** Page count of the current layout. */
    external fun nativePageCount(handle: Long): Int

    /** Go to [pageIndex] of the current layout and rasterise into [dst]. */
    external fun nativeRenderPage(
        handle: Long,
        pageIndex: Int,
        width: Int,
        height: Int,
        dst: Bitmap,
    )

    /** Serialized TOC: title<US>xpointer<US>level<RS>... ("" if none). */
    external fun nativeChapters(handle: Long): String

    /** EPUB-Werktitel aus den Dokument-Metadaten ("" falls unbekannt). */
    external fun nativeTitle(handle: Long): String

    /** EPUB-Autor(en) aus den Dokument-Metadaten ("" falls unbekannt). */
    external fun nativeAuthors(handle: Long): String

    /** EPUB document language from dc:language / xml:lang ("" if unknown). */
    external fun nativeLanguage(handle: Long): String

    /** Stable xpointer of the current page top ("" if unavailable). */
    external fun nativeCurrentAnchor(handle: Long): String

    /** Index (0-based) of the page the view is currently on, in the current layout. */
    external fun nativeCurrentPage(handle: Long): Int

    /** Navigate to the position named by [xpointer]. */
    external fun nativeSeekToAnchor(handle: Long, xpointer: String)

    /** Navigate to relative position [fraction] (0.0..1.0). */
    external fun nativeSeekToProgress(handle: Long, fraction: Float)

    /** Serialized search hits: xpointer<US>snippet<RS>... ("" if none). */
    external fun nativeSearch(handle: Long, query: String, maxCount: Int): String

    external fun nativeClose(handle: Long)
}
