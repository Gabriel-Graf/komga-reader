package com.komgareader.app

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.komgareader.app.ui.library.browseAllSeries
import com.komgareader.domain.source.BrowsableSource
import com.komgareader.source.opds.OpdsSourceFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device E2E gegen die echten lokalen Test-Server (Komga + Kavita auf `10.0.2.2`), das den
 * **App-Cover-/Lese-Pfad** ausübt: `coverBytes` (was Coils `SourceCoverFetcher` aufruft) wird zu
 * einem echten `Bitmap` **dekodiert** — beweist „Cover funktionieren" in der App-Maschinerie, nicht
 * nur Bytes —, plus OPDS-Navigation, Pagination und PSE-Seiten-Streaming.
 *
 * **Nur on-demand:** über `-e opdsLive 1` (+ `-e kavitaKey <key>` für den Kavita-Teil) freigeschaltet,
 * sonst geskippt — die Suite braucht weder Netz noch laufende Server.
 *
 * Bekannte, hier festgeschriebene Asymmetrie: **Komga**s `/opds/v1.2/series`-Nav-Feed liefert **keine**
 * Serien-Thumbnails → Serien-Cover leer (Buch-Cover funktionieren). **Kavita**s Library-Feed liefert
 * Serien-Cover → funktionieren.
 */
@RunWith(AndroidJUnit4::class)
class OpdsLiveCoversInstrumentedTest {

    private fun arg(key: String): String? =
        InstrumentationRegistry.getArguments().getString(key)

    private fun decodes(bytes: ByteArray): Boolean =
        bytes.isNotEmpty() && BitmapFactory.decodeByteArray(bytes, 0, bytes.size) != null

    @Test
    fun komga_navigation_pagination_covers_and_pse() {
        assumeTrue("set -e opdsLive 1", arg("opdsLive") == "1")
        runBlocking {
            val src: BrowsableSource = OpdsSourceFactory.create(
                name = "Komga-Live",
                catalogUrl = "http://10.0.2.2:25600/opds/v1.2/series",
                username = "admin@test.local",
                password = "testpass123",
            )

            // Navigation + Pagination: alle Serien über den next-Cursor.
            val all = browseAllSeries(src)
            assertTrue("Komga: keine Serien", all.isNotEmpty())
            assertTrue(
                "Pagination: Berserk (Seite 1+) nicht erreicht",
                all.any { it.title.contains("Berserk", ignoreCase = true) },
            )

            val aot = all.first { it.title.contains("Attack on Titan", ignoreCase = true) }

            // Serien-Cover: Komga-Nav-Feed liefert keinen → leer (dokumentierte Grenze).
            val seriesCover = src.coverBytes(aot.remoteId, isSeriesCover = true)
            assertTrue("Komga-Serien-Cover sollte leer sein (Nav-Feed ohne Thumbnail)", seriesCover.isEmpty())

            // Buch über die Naht-Navigation.
            val book = src.books(aot.remoteId).first { it.pageCount > 0 }

            // Buch-Cover: echtes dekodierbares Bild (App-Cover-Pfad).
            val bookCover = src.coverBytes(book.remoteId, isSeriesCover = false)
            assertTrue("Komga-Buch-Cover muss als Bitmap dekodieren", decodes(bookCover))

            // PSE-Seite: echtes dekodierbares Bild.
            val refs = src.pages(book.remoteId)
            assertTrue("Komga: keine PSE-Seiten", refs.isNotEmpty())
            val page = src.openPage(refs.first())
            assertTrue("Komga-PSE-Seite muss als Bitmap dekodieren", decodes(page))

            Log.i(TAG, "KOMGA: ${all.size} Serien, seriesCover=${seriesCover.size}B, bookCover=${bookCover.size}B, page0=${page.size}B")
        }
    }

    @Test
    fun kavita_navigation_covers_and_pse() {
        assumeTrue("set -e opdsLive 1", arg("opdsLive") == "1")
        val key = arg("kavitaKey")
        assumeTrue("set -e kavitaKey <key>", key != null && key.isNotBlank())
        runBlocking {
            val src: BrowsableSource = OpdsSourceFactory.create(
                name = "Kavita-Live",
                catalogUrl = "http://10.0.2.2:5001/api/opds/$key/libraries/1",
            )

            val all = browseAllSeries(src)
            assertTrue("Kavita: keine Serien", all.isNotEmpty())
            val series = all.first()

            // Serien-Cover: Kavita liefert es → echtes dekodierbares Bild.
            val seriesCover = src.coverBytes(series.remoteId, isSeriesCover = true)
            assertTrue("Kavita-Serien-Cover muss als Bitmap dekodieren", decodes(seriesCover))

            val book = src.books(series.remoteId).first { it.pageCount > 0 }
            val refs = src.pages(book.remoteId)
            assertTrue("Kavita: keine PSE-Seiten", refs.isNotEmpty())
            val page = src.openPage(refs.first())
            assertTrue("Kavita-PSE-Seite muss als Bitmap dekodieren", decodes(page))

            Log.i(TAG, "KAVITA: ${all.size} Serien, seriesCover=${seriesCover.size}B, page0=${page.size}B")
        }
    }

    private companion object {
        const val TAG = "OpdsLiveCovers"
    }
}
