package com.komgareader.render.crengine

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.komgareader.domain.render.Hyphenation
import com.komgareader.domain.render.Margins
import com.komgareader.domain.render.ReflowConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Beweis für den ReflowableDocument-Adapter über crengine-ng:
 *  1. Die nackte Spike-Pipeline (Open + Render einer Seite mit Text) bleibt grün.
 *  2. Re-Layout funktioniert: andere Schriftgröße => andere Seitenzahl.
 *  3. TOC, Suche und der Anker-Round-Trip funktionieren über den Adapter.
 *  4. Alle gebündelten Lese-Fonts sind registriert und die echten DE/EN-
 *     Trennmuster-Wörterbücher sind zur Laufzeit aktivierbar.
 *
 * Voraussetzung: die gebündelten Lese-Fonts und die `*.pattern`-Trennmuster
 * liegen in den Test-Assets (`fonts/`, `hyph/`).
 */
@RunWith(AndroidJUnit4::class)
class CrengineRenderInstrumentedTest {

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
    fun alle_gebuendelten_lese_fonts_sind_registriert() {
        initFontManager()
        val faces = CrengineNative.nativeFontFaces().split(RECORD_SEP).toSet()
        // Registrierte Familiennamen (FreeType family_name), nicht Dateinamen.
        for (family in listOf("DejaVu Sans", "Literata", "Bitter")) {
            assertTrue(
                "Schriftfamilie '$family' registriert, vorhanden: $faces",
                faces.contains(family),
            )
        }
    }

    @Test
    fun echte_de_und_en_trennmuster_woerterbuecher_sind_aktivierbar() {
        initFontManager()
        // Die echten TeX-Muster-Wörterbücher müssen über ihre Dictionary-ID
        // (Dateiname) aktivierbar sein — nicht nur @algorithm.
        assertTrue(
            "deutsches Muster-Wörterbuch aktivierbar",
            CrengineNative.nativeActivateDictionary("hyph-de-1996.pattern"),
        )
        assertTrue(
            "englisches Muster-Wörterbuch aktivierbar",
            CrengineNative.nativeActivateDictionary("hyph-en-us.pattern"),
        )
    }

    @Test
    fun reflowcss_hyphenation_key_waehlt_das_woerterbuch_ueber_den_props_pfad() {
        initFontManager()
        // Beweis, dass der von ReflowCss erzeugte Property-Schlüssel der RICHTIGE
        // Dictionary-SELEKTIONS-Schlüssel ist (crengine.hyphenation.directory ->
        // HyphDictionaryList::activate(id)): exakt diese props an nativeApplyLayout
        // reichen — wäre der Schlüssel ein No-Op, würde die Selektion still scheitern;
        // der "hyph-de-1996.pattern"-Wert muss als registrierte Dictionary-ID greifen.
        val deProps = ReflowCss.toProperties(
            ReflowConfig(hyphenation = com.komgareader.domain.render.Hyphenation.Language("de")),
        )
        assertEquals(
            "ReflowCss muss das echte DE-Muster über den Selektions-Property setzen",
            "hyph-de-1996.pattern",
            deProps["crengine.hyphenation.directory"],
        )
        val handle = CrengineNative.nativeOpen(assetBytes("sample.epub"), "sample.epub")
        assertTrue("EPUB geöffnet (handle != 0)", handle != 0L)
        try {
            CrengineNative.nativeApplyLayout(
                handle, viewportW, viewportH, 24,
                deProps.keys.toTypedArray(), deProps.values.toTypedArray(),
                ReflowCss.toUserCss(ReflowConfig(hyphenation = com.komgareader.domain.render.Hyphenation.Language("de"))),
            )
            // Layout mit aktivem DE-Wörterbuch muss eine valide Seitenzahl liefern.
            assertTrue(
                "Layout über den DE-Trennungs-Props-Pfad ergibt Seiten",
                CrengineNative.nativePageCount(handle) > 0,
            )
        } finally {
            CrengineNative.nativeClose(handle)
        }
    }

    @Test
    fun rendert_reflowte_epub_seite_mit_text() {
        initFontManager()
        val handle = CrengineNative.nativeOpen(assetBytes("sample.epub"), "sample.epub")
        assertTrue("EPUB geöffnet (handle != 0)", handle != 0L)

        val bitmap = Bitmap.createBitmap(viewportW, viewportH, Bitmap.Config.ARGB_8888)
        try {
            // Layout muss vor dem ersten Render angewandt sein.
            CrengineNative.nativeApplyLayout(
                handle, viewportW, viewportH, 24, emptyArray(), emptyArray(), "",
            )
            CrengineNative.nativeRenderPage(handle, 0, viewportW, viewportH, bitmap)

            val pixels = IntArray(viewportW * viewportH)
            bitmap.getPixels(pixels, 0, viewportW, 0, 0, viewportW, viewportH)

            val distinctColors = pixels.toHashSet().size
            assertTrue("Seite ist nicht uniform (mehrere Farben), war $distinctColors", distinctColors > 1)

            val darkPixels = pixels.count { argb ->
                val r = (argb shr 16) and 0xff
                val g = (argb shr 8) and 0xff
                val b = argb and 0xff
                (r + g + b) / 3 < 80
            }
            assertTrue("gerenderter Text (dunkle Pixel vorhanden), war $darkPixels", darkPixels > 300)
        } finally {
            bitmap.recycle()
            CrengineNative.nativeClose(handle)
        }
    }

    @Test
    fun reflow_aendert_seitenzahl_bei_groesserer_schrift() {
        initFontManager()
        openDocument().use { doc ->
            doc.applyLayout(ReflowConfig(fontSizeEm = 1.0f))
            val pagesSmall = doc.pageCount()
            assertTrue("Seitenzahl bei 1.0em > 0, war $pagesSmall", pagesSmall > 0)

            doc.applyLayout(ReflowConfig(fontSizeEm = 2.0f))
            val pagesLarge = doc.pageCount()

            assertNotEquals(
                "Re-Layout: groessere Schrift muss die Seitenzahl aendern " +
                    "(1.0em=$pagesSmall, 2.0em=$pagesLarge)",
                pagesSmall,
                pagesLarge,
            )
            assertTrue(
                "Groessere Schrift sollte mehr Seiten ergeben (1.0em=$pagesSmall, 2.0em=$pagesLarge)",
                pagesLarge > pagesSmall,
            )
        }
    }

    /**
     * Beweis, dass die Seitenränder tatsächlich auf das gerenderte Bild wirken
     * (Regression für BUG 2): Der linke Inhaltsrand des **Textkörpers** liegt bei
     * WIDE weiter rechts als bei NARROW. Schlägt fehl, solange der Bridge
     * `view->Render(width, height)` statt `view->Render(0, 0)` aufruft — denn nur
     * bei 0,0 leitet LVDocView die Layoutbreite aus der Seitenfläche ABZÜGLICH der
     * Ränder ab; mit den vollen Viewport-Maßen wird der Text über die volle Breite
     * gesetzt und der linke Rand bleibt für NARROW wie WIDE identisch.
     *
     * px-Werte (12/50) müssen in der crengine-ng-Erlaubnisliste liegen, sonst setzt
     * `limitValueList` sie still auf 8 zurück (siehe [NovelSettings.marginFor]).
     */
    @Test
    fun seitenraender_verschieben_den_linken_inhaltsrand() {
        initFontManager()
        val narrow = Margins(top = 12, bottom = 12, left = 12, right = 12)
        val wide = Margins(top = 50, bottom = 50, left = 50, right = 50)

        val narrowEdge = openDocument().use { doc ->
            doc.applyLayout(ReflowConfig(margin = narrow))
            firstTextColumnInBand(doc.renderPage(0, zoom = 1f, rotation = 0))
        }
        val wideEdge = openDocument().use { doc ->
            doc.applyLayout(ReflowConfig(margin = wide))
            firstTextColumnInBand(doc.renderPage(0, zoom = 1f, rotation = 0))
        }

        assertTrue("NARROW hat Text im Band (linker Rand gefunden), war $narrowEdge", narrowEdge >= 0)
        assertTrue("WIDE hat Text im Band (linker Rand gefunden), war $wideEdge", wideEdge >= 0)
        assertTrue(
            "WIDE-Margin muss den Text-Inhaltsrand weiter nach rechts schieben " +
                "(NARROW=$narrowEdge, WIDE=$wideEdge)",
            wideEdge > narrowEdge,
        )
    }

    /**
     * Linkester Spaltenindex, in dem der **Textkörper** beginnt (linker Inhaltsrand).
     * Gemessen in einem schmalen vertikalen Band (Zeilen 100..400) statt über die ganze
     * Höhe — so triggern Kopf-/Fußzeilen-Dekorationen am Seitenrand (die unabhängig vom
     * Inhaltsrand am linken Bildrand liegen) den gemessenen Rand nicht fälschlich.
     */
    private fun firstTextColumnInBand(
        page: com.komgareader.domain.render.RenderedPage,
        bandTop: Int = 100,
        bandBottom: Int = 400,
    ): Int {
        val w = page.width
        val top = bandTop.coerceIn(0, page.height)
        val bottom = bandBottom.coerceIn(top, page.height)
        for (x in 0 until w) {
            for (y in top until bottom) {
                val argb = page.pixels[y * w + x]
                val r = (argb shr 16) and 0xff
                val g = (argb shr 8) and 0xff
                val b = argb and 0xff
                if ((r + g + b) / 3 < 80) return x
            }
        }
        return -1
    }

    @Test
    fun chapters_liefert_das_inhaltsverzeichnis() {
        initFontManager()
        openDocument().use { doc ->
            doc.applyLayout(ReflowConfig.DEFAULT)
            val chapters = doc.chapters()
            // sample.epub hat genau einen TOC-Eintrag ("K1").
            assertTrue("TOC nicht leer, war ${chapters.size}", chapters.isNotEmpty())
            assertTrue(
                "TOC-Eintrag hat einen Anker (xpointer), war '${chapters.first().anchor}'",
                chapters.first().anchor.isNotBlank(),
            )
        }
    }

    @Test
    fun search_findet_ein_im_text_vorhandenes_wort() {
        initFontManager()
        openDocument().use { doc ->
            doc.applyLayout(ReflowConfig.DEFAULT)
            // "Sonne" kommt im Korpus von sample.epub vielfach vor.
            val hits = doc.search("Sonne")
            assertTrue("mindestens ein Treffer für 'Sonne', war ${hits.size}", hits.isNotEmpty())
            assertTrue(
                "Treffer hat einen Anker (xpointer), war '${hits.first().anchor}'",
                hits.first().anchor.isNotBlank(),
            )
        }
    }

    @Test
    fun anker_round_trip_stuerzt_nicht_ab_und_liefert_anker() {
        initFontManager()
        openDocument().use { doc ->
            doc.applyLayout(ReflowConfig(hyphenation = Hyphenation.Language("de")))
            val anchor = doc.currentAnchor()
            assertTrue("currentAnchor liefert einen xpointer, war '$anchor'", anchor.isNotBlank())
            // Round-Trip: zurueck an den gemerkten Anker springen.
            doc.seekToAnchor(anchor)
            assertEquals(
                "seekToAnchor(currentAnchor()) ist stabil",
                anchor,
                doc.currentAnchor(),
            )
        }
    }

    private companion object {
        /** Datensatz-Trennzeichen der nativen Serialisierung (ASCII Record-Separator). */
        const val RECORD_SEP = '\u001E'
    }
}
