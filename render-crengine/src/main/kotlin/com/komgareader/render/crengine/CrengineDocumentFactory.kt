package com.komgareader.render.crengine

import android.content.Context
import com.komgareader.domain.render.NovelFonts
import com.komgareader.domain.render.ReflowableDocument
import com.komgareader.domain.render.ReflowableDocumentFactory
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * crengine-ng-Implementierung von [ReflowableDocumentFactory] (Naht B). Sie kapselt
 * zwei Dinge, die das `app`-Modul nicht kennen soll:
 *
 *  1. den **prozessweiten, genau einmaligen** Engine-Bootstrap (Font-Manager +
 *     Trennmuster) via [CrengineNative.nativeInit] — der native Font-Manager ist ein
 *     C++-Prozess-Singleton, doppelte Initialisierung wäre undefiniertes Verhalten;
 *  2. das Öffnen eines [CrengineDocument] aus Bytes für eine Viewport-Geometrie.
 *
 * Der Bootstrap läuft beim ersten [open] und ist über [AtomicBoolean.compareAndSet]
 * **exakt einmal** — auch wenn mehrere Threads gleichzeitig öffnen (kein Check-then-Act).
 */
class CrengineDocumentFactory(
    private val context: Context,
) : ReflowableDocumentFactory {

    /** true, sobald der prozessweite Font-Manager-Bootstrap durchgelaufen ist. */
    private val fontManagerReady = AtomicBoolean(false)

    // Plugin font paths registered before the (single) nativeInit boot; flushed in ensureFontManager().
    private val pendingFontPaths = mutableListOf<String>()

    /**
     * Registers a plugin-supplied font file with the crengine font manager.
     *
     * If the engine has not booted yet (no novel opened), the path is buffered in
     * [pendingFontPaths] and passed to the single [CrengineNative.nativeInit] call when the first
     * novel is opened. Once the engine is booted, the font is registered live via
     * [CrengineNative.nativeAddFont].
     *
     * Concurrency note: this method is [Synchronized]; [ensureFontManager] snapshots
     * [pendingFontPaths] inside [synchronized] as well. A path added in the tiny window after
     * the snapshot but before nativeInit completes is benign — it will be picked up by the next
     * [registerFont] call (which sees [fontManagerReady] == true and delegates to nativeAddFont).
     * A second nativeInit must never be called.
     */
    @Synchronized
    override fun registerFont(absolutePath: String): Boolean {
        // Boot not done yet → defer; ensureFontManager() passes these to the single nativeInit.
        if (!fontManagerReady.get()) {
            if (absolutePath !in pendingFontPaths) pendingFontPaths.add(absolutePath)
            return false
        }
        // Already booted → register live; a second nativeInit is forbidden.
        return CrengineNative.nativeAddFont(absolutePath)
    }

    override fun open(
        bytes: ByteArray,
        formatHint: String,
        viewportWidth: Int,
        viewportHeight: Int,
    ): ReflowableDocument {
        ensureFontManager()
        return CrengineDocument(bytes, formatHint, viewportWidth, viewportHeight)
    }

    /**
     * Initialises the crengine font manager exactly once: extracts all bundled novel fonts
     * ([NovelFonts.ALL]) and hyphenation patterns from assets into the cache, then registers
     * them — together with any plugin fonts buffered in [pendingFontPaths] — via the single
     * [CrengineNative.nativeInit] call. [AtomicBoolean.compareAndSet] wins the race atomically;
     * only the first caller invokes nativeInit, all subsequent callers return immediately.
     */
    private fun ensureFontManager() {
        if (!fontManagerReady.compareAndSet(false, true)) return
        val builtinPaths = NovelFonts.ALL.map { extractAsset(it.asset, context.cacheDir).absolutePath }
        val pending = synchronized(this) { pendingFontPaths.toList() }
        val fontPaths = (builtinPaths + pending).toTypedArray()
        val hyphDir = extractHyphenationPatterns()
        CrengineNative.nativeInit(fontPaths, hyphDir)
    }

    /**
     * Entpackt die `.pattern`-Wörterbücher in ein eigenes Cache-Verzeichnis und liefert
     * dessen Pfad **mit abschließendem '/'** zurück — crengine-ng erkennt ein Verzeichnis
     * (statt Archiv) nur am Trailing-Slash.
     */
    private fun extractHyphenationPatterns(): String {
        val hyphDir = File(context.cacheDir, "hyph").apply { mkdirs() }
        for (pattern in HYPH_PATTERNS) {
            extractAsset("hyph/$pattern", hyphDir)
        }
        return hyphDir.absolutePath + "/"
    }

    /** Kopiert ein Asset einmalig in [targetDir] (nach Basisnamen) und liefert die Datei. */
    private fun extractAsset(asset: String, targetDir: File): File {
        val out = File(targetDir, asset.substringAfterLast('/'))
        if (!out.exists()) {
            context.assets.open(asset).use { input ->
                out.outputStream().use { input.copyTo(it) }
            }
        }
        return out
    }

    private companion object {
        /** Gebündelte Silbentrennungs-Muster (Assets unter `hyph/`). */
        val HYPH_PATTERNS = listOf("hyph-de-1996.pattern", "hyph-en-us.pattern")
    }
}
