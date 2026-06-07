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
     * Initialisiert den crengine-Font-Manager genau einmal: entpackt alle gebündelten
     * Lese-Schriften ([NovelFonts.ALL]) und die Silbentrennungs-Muster aus den Assets in
     * den Cache und registriert sie nativ. [AtomicBoolean.compareAndSet] gewinnt das
     * Rennen atomar — nur der erste Aufrufer ruft [CrengineNative.nativeInit], alle
     * weiteren kehren sofort zurück.
     */
    private fun ensureFontManager() {
        if (!fontManagerReady.compareAndSet(false, true)) return
        val fontPaths = NovelFonts.ALL
            .map { extractAsset(it.asset, context.cacheDir).absolutePath }
            .toTypedArray()
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
