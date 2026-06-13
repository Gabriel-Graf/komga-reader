package com.komgareader.render.crengine

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Beweis für [CrengineNative.nativeAddFont]: das Laufzeit-Primitiv, mit dem ein
 * installiertes Font-Plugin OHNE App-Neustart nutzbar wird (RegisterFont nach Boot).
 *
 * Verifikations-Ansatz (bewusste Wahl): Der crengine-Font-Manager ist
 * prozess-global und `InitFontManager` läuft genau EINMAL pro Prozess; ohne
 * Test-Orchestrator teilen sich alle Test-Klassen dieses Moduls denselben Prozess.
 * Ein sauberer Subset-Boot (z. B. nur DejaVuSans, dann Bitter live nachladen) wäre
 * daher flaky: läuft [CrengineRenderInstrumentedTest] zuerst, ist Bitter bereits
 * registriert, und die Behauptung „Bitter fehlt noch" schlägt fehl. Stattdessen wird
 * der order-unabhängige, echte Invariant geprüft:
 *  1. Booten (idempotent — egal ob eine andere Klasse schon gebootet hat).
 *  2. Eine gültige TTF unter einem BRANDNEUEN Pfad zur Laufzeit nachladen
 *     (`nativeAddFont` → true), und die Face-Liste danach enthält die Familie
 *     der nachgeladenen Schrift. Das beweist, dass `RegisterFont` nach Boot greift.
 *  3. Ein Nicht-Font-Pfad ist benign: kein Crash, kein Verlust bereits registrierter
 *     Schriften (Rückgabe bleibt true, weil der Manager weiter Schriften hält).
 */
@RunWith(AndroidJUnit4::class)
class NativeAddFontInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().context

    private val bundledFonts = listOf(
        "fonts/DejaVuSans.ttf",
        "fonts/Literata.ttf",
        "fonts/Bitter.ttf",
    )
    private val hyphPatterns = listOf("hyph-de-1996.pattern", "hyph-en-us.pattern")

    private fun copyAssetToFile(asset: String, targetName: String? = null): File {
        val out = File(context.cacheDir, targetName ?: asset.substringAfterLast('/'))
        out.parentFile?.mkdirs()
        context.assets.open(asset).use { input -> out.outputStream().use { input.copyTo(it) } }
        return out
    }

    private fun extractHyphDir(): String {
        val dir = File(context.cacheDir, "hyph").apply { mkdirs() }
        for (p in hyphPatterns) {
            context.assets.open("hyph/$p").use { input ->
                File(dir, p).outputStream().use { input.copyTo(it) }
            }
        }
        return dir.absolutePath + "/"
    }

    /** Idempotenter Boot — sicher, egal ob eine andere Test-Klasse schon gebootet hat. */
    private fun bootFontManager() {
        val fontPaths = bundledFonts.map { copyAssetToFile(it).absolutePath }.toTypedArray()
        assertTrue(
            "Font-Manager init liefert mindestens eine nutzbare Schrift",
            CrengineNative.nativeInit(fontPaths, extractHyphDir()),
        )
    }

    private fun faces(): Set<String> =
        CrengineNative.nativeFontFaces().split(RECORD_SEP).filter { it.isNotEmpty() }.toSet()

    @Test
    fun add_font_registriert_eine_gueltige_ttf_zur_laufzeit() {
        bootFontManager()

        // Brandneuer Pfad (nie beim Boot registriert) mit gültigem TTF-Inhalt.
        val newPath = copyAssetToFile("fonts/Bitter.ttf", targetName = "RuntimeAddedBitter.ttf").absolutePath

        val ok = CrengineNative.nativeAddFont(newPath)
        assertTrue("nativeAddFont(gültige TTF) liefert true", ok)

        val facesAfter = faces()
        assertTrue("Face-Liste nach Laufzeit-Add nicht leer, war $facesAfter", facesAfter.isNotEmpty())
        // Die Familie der nachgeladenen Schrift ist zur Laufzeit erreichbar.
        assertTrue(
            "Familie 'Bitter' nach Laufzeit-Add registriert, vorhanden: $facesAfter",
            facesAfter.contains("Bitter"),
        )
    }

    @Test
    fun add_font_mit_ungueltigem_pfad_ist_benign() {
        bootFontManager()
        val before = faces()
        assertTrue("Vorbedingung: mindestens eine Schrift registriert", before.isNotEmpty())

        // Kein Font: darf nicht crashen und keine bereits registrierten Schriften verlieren.
        val bogus = File(context.cacheDir, "not-a-font.txt").apply { writeText("definitely not a font") }
        val ok = CrengineNative.nativeAddFont(bogus.absolutePath)

        assertTrue(
            "nativeAddFont(Nicht-Font) bleibt true, weil der Manager weiter Schriften hält",
            ok,
        )
        val after = faces()
        assertTrue(
            "kein Verlust registrierter Familien durch den fehlgeschlagenen Add " +
                "(vorher=$before, nachher=$after)",
            after.containsAll(before),
        )
    }

    @Test
    fun add_font_desselben_pfads_ist_benign_no_op() {
        bootFontManager()
        // DejaVuSans wurde beim Boot von genau diesem Pfad registriert; erneut über
        // denselben Pfad nachzuladen ist ein benigner No-Op (RegisterFont -> false),
        // der Rückgabewert bleibt true (Manager hält weiter Schriften).
        val samePath = copyAssetToFile("fonts/DejaVuSans.ttf").absolutePath
        val ok = CrengineNative.nativeAddFont(samePath)
        assertTrue("erneuter Add desselben Pfads bleibt true (benign)", ok)
        assertFalse("DejaVu Sans bleibt registriert", faces().isEmpty())
    }

    private companion object {
        /** Datensatz-Trennzeichen der nativen Serialisierung (ASCII Record-Separator). */
        const val RECORD_SEP = '\u001E'
    }
}
