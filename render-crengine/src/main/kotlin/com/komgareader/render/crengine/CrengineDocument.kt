package com.komgareader.render.crengine

import android.graphics.Bitmap
import com.komgareader.domain.render.Chapter
import com.komgareader.domain.render.PageSize
import com.komgareader.domain.render.ReflowConfig
import com.komgareader.domain.render.ReflowableDocument
import com.komgareader.domain.render.RenderedPage
import com.komgareader.domain.render.SearchHit

/**
 * crengine-ng-Implementierung der reflowbaren Render-Naht (Naht B, nur EPUB).
 *
 * Hält einen nativen `LVDocView`-Handle und den aktuellen Viewport. Anders als
 * beim paginierten MuPDF-Pfad hängt das Layout (und damit die Seitenzahl) vom
 * Viewport und der Typografie ab: [applyLayout] schichtet bei der aktuellen
 * Viewport-Größe neu um. [setViewport] meldet eine Größenänderung und schichtet
 * mit der zuletzt angewandten Konfiguration erneut um.
 *
 * Nicht thread-sicher — pro Lesefluss eine Instanz; [close] gibt den nativen
 * Handle frei. Vor dem ersten [applyLayout] muss [CrengineNative.nativeInit]
 * (Font-Manager + mindestens eine registrierte Schrift) gelaufen sein.
 *
 * Serialisierungsformat der nativen TOC-/Such-Rückgaben: Datensätze durch
 * 0x1E getrennt, Felder innerhalb eines Datensatzes durch 0x1F.
 */
class CrengineDocument(
    bytes: ByteArray,
    formatHint: String,
    viewportWidth: Int,
    viewportHeight: Int,
) : ReflowableDocument {

    private val handle: Long = CrengineNative.nativeOpen(bytes, formatHint)
    private var width: Int = viewportWidth
    private var height: Int = viewportHeight
    private var lastConfig: ReflowConfig = ReflowConfig.DEFAULT

    init {
        require(handle != 0L) { "crengine konnte das Dokument nicht öffnen" }
    }

    /** Meldet eine neue Viewport-Größe und schichtet mit der letzten Konfig um. */
    fun setViewport(viewportWidth: Int, viewportHeight: Int) {
        width = viewportWidth
        height = viewportHeight
        applyLayout(lastConfig)
    }

    override fun applyLayout(cfg: ReflowConfig) {
        lastConfig = cfg
        val props = ReflowCss.toProperties(cfg)
        val keys = props.keys.toTypedArray()
        val vals = props.values.toTypedArray()
        CrengineNative.nativeApplyLayout(
            handle = handle,
            width = width,
            height = height,
            fontSizePx = fontSizePx(cfg.fontSizeEm),
            keys = keys,
            vals = vals,
            userCss = ReflowCss.toUserCss(cfg),
        )
    }

    override fun pageCount(): Int = CrengineNative.nativePageCount(handle)

    override fun pageSize(index: Int): PageSize = PageSize(width, height)

    /**
     * Rendert die umgeschichtete Seite [index] in den Viewport. [zoom] und
     * [rotation] werden im Reflow-Modus ignoriert: die Schriftgröße steuert die
     * "Vergrößerung", die Geometrie ist durch den Viewport festgelegt.
     */
    override fun renderPage(index: Int, zoom: Float, rotation: Int): RenderedPage {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            CrengineNative.nativeRenderPage(handle, index, width, height, bitmap)
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            return RenderedPage(width = width, height = height, pixels = pixels)
        } finally {
            bitmap.recycle()
        }
    }

    override fun chapters(): List<Chapter> =
        parseRecords(CrengineNative.nativeChapters(handle)) { fields ->
            Chapter(
                title = fields.getOrElse(0) { "" },
                anchor = fields.getOrElse(1) { "" },
                depth = fields.getOrNull(2)?.toIntOrNull() ?: 0,
            )
        }

    override fun title(): String = CrengineNative.nativeTitle(handle)

    override fun authors(): String = CrengineNative.nativeAuthors(handle)

    override fun contentLanguage(): String = CrengineNative.nativeLanguage(handle)

    override fun currentAnchor(): String = CrengineNative.nativeCurrentAnchor(handle)

    override fun currentPage(): Int = CrengineNative.nativeCurrentPage(handle)

    override fun seekToAnchor(anchor: String) {
        CrengineNative.nativeSeekToAnchor(handle, anchor)
    }

    override fun seekToProgress(fraction: Float) {
        CrengineNative.nativeSeekToProgress(handle, fraction)
    }

    override fun search(query: String): List<SearchHit> =
        parseRecords(CrengineNative.nativeSearch(handle, query, MAX_SEARCH_HITS)) { fields ->
            SearchHit(
                anchor = fields.getOrElse(0) { "" },
                snippet = fields.getOrElse(1) { "" },
            )
        }

    override fun close() {
        CrengineNative.nativeClose(handle)
    }

    /** Wandelt em-Schriftgröße in eine Basis-Pixelgröße für `setFontSize`. */
    private fun fontSizePx(fontSizeEm: Float): Int =
        (fontSizeEm * BASE_FONT_PX).toInt().coerceAtLeast(1)

    private fun <T> parseRecords(serialized: String, parse: (List<String>) -> T): List<T> {
        if (serialized.isEmpty()) return emptyList()
        return serialized.split(RECORD_SEP)
            .filter { it.isNotEmpty() }
            .map { parse(it.split(FIELD_SEP)) }
    }

    private companion object {
        const val RECORD_SEP = '\u001E'
        const val FIELD_SEP = '\u001F'
        const val BASE_FONT_PX = 24
        const val MAX_SEARCH_HITS = 200
    }
}
