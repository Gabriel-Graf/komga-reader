package com.komgareader.render.crengine

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.komgareader.domain.render.Hyphenation
import com.komgareader.domain.render.ReflowConfig
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Beweis für die Wort-Lesezeichen-Naht (Naht B) über crengine-ng:
 *  1. [CrengineDocument.wordAt] liefert für einen Punkt mitten im Textkörper
 *     ein nicht-leeres WordHit (xpointer + Wort + Rect).
 *  2. [CrengineDocument.rectsFor] findet genau diesen xpointer auf der aktuellen
 *     Seite wieder (Round-Trip Punkt -> xpointer -> Rect).
 *
 * Gerätegebunden: die `cr3bridge`-`.so` wird nur für arm64 gebaut, daher läuft
 * dieser Test ausschließlich auf einer echten Boox, nicht auf dem x86_64-Emulator.
 *
 * Voraussetzung: die gebündelten Lese-Fonts und die `*.pattern`-Trennmuster
 * liegen in den Test-Assets (`fonts/`, `hyph/`), plus `sample.epub`.
 */
@RunWith(AndroidJUnit4::class)
class CrengineWordBookmarkInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().context

    private val viewportW = 800
    private val viewportH = 1200

    private fun copyAssetToFile(asset: String): File {
        val out = File(context.cacheDir, asset.substringAfterLast('/'))
        out.parentFile?.mkdirs()
        context.assets.open(asset).use { input -> out.outputStream().use { input.copyTo(it) } }
        return out
    }

    private fun assetBytes(name: String): ByteArray =
        context.assets.open(name).use { it.readBytes() }

    private val bundledFonts = listOf(
        "fonts/DejaVuSans.ttf",
        "fonts/Literata.ttf",
        "fonts/Bitter.ttf",
    )

    private val hyphPatterns = listOf("hyph-de-1996.pattern", "hyph-en-us.pattern")

    /** Entpackt die Trennmuster in ein Verzeichnis und liefert dessen Pfad mit Trailing-Slash. */
    private fun extractHyphDir(): String {
        val dir = File(context.cacheDir, "hyph").apply { mkdirs() }
        for (p in hyphPatterns) {
            context.assets.open("hyph/$p").use { input ->
                File(dir, p).outputStream().use { input.copyTo(it) }
            }
        }
        return dir.absolutePath + "/"
    }

    private fun initFontManager() {
        val fontPaths = bundledFonts.map { copyAssetToFile(it).absolutePath }.toTypedArray()
        assertTrue(
            "Font-Manager init + Fonts + Trennmuster registriert",
            CrengineNative.nativeInit(fontPaths, extractHyphDir()),
        )
    }

    private fun openDocument(): CrengineDocument =
        CrengineDocument(assetBytes("sample.epub"), "sample.epub", viewportW, viewportH)

    @Test
    fun word_at_und_rects_for_machen_einen_punkt_xpointer_rect_round_trip() {
        initFontManager()
        openDocument().use { doc ->
            doc.applyLayout(ReflowConfig(hyphenation = Hyphenation.Language("de")))

            // Ein Punkt im oberen Viertel, horizontal mittig — sollte im Textkörper
            // der ersten Seite liegen.
            val hit = doc.wordAt(viewportW / 2, viewportH / 4)
            assertTrue("wordAt liefert ein WordHit, war null", hit != null)
            val word = hit!!
            assertTrue(
                "WordHit hat einen xpointer, war '${word.xpointer}'",
                word.xpointer.isNotBlank(),
            )

            // Round-Trip: der gefundene xpointer muss auf der aktuellen Seite ein
            // Rect liefern.
            val rects = doc.rectsFor(listOf(word.xpointer))
            assertTrue(
                "rectsFor enthält den xpointer des Treffers, vorhanden: ${rects.keys}",
                rects.containsKey(word.xpointer),
            )
        }
    }
}
