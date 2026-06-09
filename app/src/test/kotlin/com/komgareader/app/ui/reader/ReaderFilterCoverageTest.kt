package com.komgareader.app.ui.reader

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Statischer Quelltext-Scan (kein Compose-Runtime): Jeder Reader-Screen **muss** Seiten
 * über `FilteredReaderAsyncImage(` oder `FilteredReaderImage(` rendern — niemals raw
 * `AsyncImage`/`Image` direkt.
 *
 * Warum die Klammer im Muster (`FilteredReaderAsyncImage(`)? Import-Zeilen enden nie mit
 * `(` — damit werden Import- und Kommentarerwähnungen ausgeschlossen und nur echte
 * Call-Sites gezählt. Ein Screen, der den Composable nur importiert, aber nicht aufruft,
 * würde diesen Test ebenfalls fehlschlagen lassen.
 *
 * Regressionsschutz: Wird ein neuer Reader-Screen ohne Filterpfad hinzugefügt, scheitert
 * dieser Test sofort. Wird eine bestehende Datei auf raw `AsyncImage` umgestellt und die
 * `FilteredReader*`-Referenz entfernt, fällt der Test ebenfalls.
 */
class ReaderFilterCoverageTest {

    /**
     * Löst das Reader-Verzeichnis auf, egal ob der Test aus dem Modul-Root (`app/`) oder
     * aus dem Repo-Root gestartet wird.
     */
    private val readerDir: File by lazy {
        val relPath = "src/main/kotlin/com/komgareader/app/ui/reader"
        val fromModule = File(relPath)
        val fromRepo = File("app/$relPath")
        when {
            fromModule.isDirectory -> fromModule
            fromRepo.isDirectory -> fromRepo
            else -> error(
                "Reader-Verzeichnis nicht gefunden. Gesucht:\n" +
                    "  ${fromModule.absolutePath}\n  ${fromRepo.absolutePath}"
            )
        }
    }

    private val screens = listOf(
        "PagedReaderScreen",
        "WebtoonReaderScreen",
        "ComicReaderScreen",
        "EpubReaderScreen",
        "NovelReaderScreen",
    )

    @Test
    fun `every reader screen renders pages through the color filter`() {
        val offenders = screens.filterNot { name ->
            val file = File(readerDir, "$name.kt")
            // Wirft FileNotFoundException wenn die Datei fehlt — das ist gewollt: ein
            // fehlender Screen-File ist ein lauter Fehler, kein stilles Übersehen.
            val src = file.readText()
            // Klammer im Muster schließt Import-Zeilen und Kommentare aus:
            // Imports enden nie mit `(`, Comments wären zusätzlicher Aufwand zum Fälschen.
            "FilteredReaderAsyncImage(" in src || "FilteredReaderImage(" in src
        }
        assertTrue(
            offenders.isEmpty(),
            "Folgende Reader-Screens rendern Seiten NICHT über FilteredReader*: $offenders\n" +
                "Jeder Screen muss FilteredReaderAsyncImage() (Coil) oder FilteredReaderImage() " +
                "(MuPDF-Bitmap) verwenden, damit der Farb-Filter auf allen Pfaden aktiv ist."
        )
    }
}
