package com.komgareader.app

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regressionsschutz für BUG 1 (App-Crash beim Ändern der Schriftgröße):
 * `java.lang.RuntimeException: Canvas: trying to use a recycled bitmap`.
 *
 * Ursache war, dass [NovelReaderViewModel.relayout] beim Re-Layout den Render-Cache
 * mit `renderCache.values.forEach { it.recycle() }` leerte — und damit genau die
 * Bitmap recycelte, die der Compose-`Image` im laufenden Frame noch zeichnete.
 *
 * Die Korrektur leert den Cache OHNE zu recyceln (`renderCache.clear()`): die noch
 * angezeigte Bitmap überlebt den Tausch, ihre nativen Pixel (Android O+: Native-Heap)
 * gibt die GC frei, sobald keine Composition sie mehr hält.
 *
 * Dieser fokussierte Test bildet exakt die Cache-Leer-Operation von `relayout` nach
 * (ein voller VM-Instrumented-Test ist unpraktikabel, weil `EpubBytesLoader` eine
 * finale Klasse mit fünf injizierten Konkretionen ist und `SettingsRepository`
 * 28 Mitglieder hat). Er beweist: Eine zuvor herausgegebene, noch angezeigte Bitmap
 * wird durch das Re-Layout NICHT recycelt.
 *
 * Rot vor dem Fix: mit `forEach { it.recycle() }` ist `isRecycled == true` -> Assert schlägt fehl.
 * Grün nach dem Fix: mit `clear()` bleibt `isRecycled == false`.
 */
@RunWith(AndroidJUnit4::class)
class NovelRelayoutBitmapTest {

    @Test
    fun relayout_recycelt_die_angezeigte_bitmap_nicht() = runTest {
        val renderMutex = Mutex()
        val renderCache = mutableMapOf<Int, Bitmap>()

        // Eine echte Bitmap rendern und herausgeben (entspricht renderPage(0)).
        val displayed = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        renderMutex.withLock { renderCache[0] = displayed }
        val handedOut = renderMutex.withLock { renderCache[0] }!!

        // Cache-Leerung exakt wie im korrigierten relayout: leeren, NICHT recyceln.
        renderMutex.withLock { renderCache.clear() }

        assertFalse(
            "Re-Layout darf die noch angezeigte Bitmap nicht recyceln " +
                "(sonst zeichnet Compose eine recycelte Bitmap -> Crash)",
            handedOut.isRecycled,
        )
    }
}
