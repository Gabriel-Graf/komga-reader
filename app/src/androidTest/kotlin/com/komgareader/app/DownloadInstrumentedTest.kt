package com.komgareader.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.komgareader.app.data.KomgaSourceProvider
import com.komgareader.domain.repository.ServerConfig
import com.komgareader.render.mupdf.MupdfDocumentFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DownloadInstrumentedTest {

    @Test fun download_und_offline_render() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val source = KomgaSourceProvider().from(
            ServerConfig("T", "http://10.0.2.2:25600/api/v1/", "2243c9f4ecc5404992ddf8eba4bf6488"),
        )!!
        val bytes = source.downloadFile("0QKVPRDV42BFA")     // Berserk vol01 cbz
        assertTrue("bytes empfangen: ${bytes.size}", bytes.size > 1000)
        val dir = File(ctx.filesDir, "downloads-test").apply { mkdirs() }
        val f = File(dir, "b.cbz")
        f.writeBytes(bytes)
        // Offline lesen aus der lokalen Datei (kein Netz):
        val doc = MupdfDocumentFactory().open(f.readBytes(), ".cbz")
        assertTrue("pageCount >= 4: ${doc.pageCount()}", doc.pageCount() >= 4)
        val page = doc.renderPage(0, 2f, 0)
        val dark = page.pixels.count { pixel ->
            val r = (pixel shr 16) and 0xff
            val g = (pixel shr 8) and 0xff
            val b = pixel and 0xff
            (r + g + b) / 3 < 80
        }
        assertTrue("nicht leer: $dark", dark > 100)
        doc.close()
        f.delete()
    }
}
