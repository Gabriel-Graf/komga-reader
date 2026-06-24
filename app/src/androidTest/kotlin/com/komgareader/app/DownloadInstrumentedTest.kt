package com.komgareader.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.komgareader.app.data.KomgaSourceProvider
import com.komgareader.data.db.AppDatabase
import com.komgareader.data.download.DownloadManager
import com.komgareader.data.repository.RoomDownloadRepository
import com.komgareader.data.repository.RoomSettingsRepository
import com.komgareader.render.mupdf.MupdfDocumentFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DownloadInstrumentedTest {

    private lateinit var db: AppDatabase
    private lateinit var downloadManager: DownloadManager

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        downloadManager = DownloadManager(
            ctx = ctx,
            downloads = RoomDownloadRepository(db.downloadDao()),
            settings = RoomSettingsRepository(db.settingsDao()),
        )
    }

    @After fun teardown() = db.close()

    @Test fun download_und_offline_render() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val source = KomgaSourceProvider().from(LocalTestServer.config("T"))!!
        val bookRemoteId = "0QKVPRDV42BFA"
        val bytes = source.downloadFile(bookRemoteId)
        assertTrue("bytes empfangen: ${bytes.size}", bytes.size > 1000)

        // Über DownloadManager speichern (testet store-Pfad)
        downloadManager.store(
            bookRemoteId = bookRemoteId,
            sourceId = 1L,
            seriesRemoteId = "0QKVPRDV0293Z",
            title = "Berserk Vol01",
            format = "CBZ",
            totalPages = 200,
        ) { out -> out.write(bytes) }

        // DB-Eintrag muss vorhanden sein
        val downloaded = db.downloadDao().get(bookRemoteId)
        assertTrue("DB-Eintrag vorhanden", downloaded != null)
        val localPath = downloaded!!.localPath

        // Bytes über readBytes() lesen (unterstützt Datei-Pfad + content-URI)
        val localBytes = downloadManager.readBytes(localPath)
        assertTrue("lokale Bytes nicht leer: ${localBytes.size}", localBytes.size > 1000)

        // Offline rendern aus dem gespeicherten Pfad
        val doc = MupdfDocumentFactory().open(localBytes, ".cbz")
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

        // Aufräumen via DownloadManager.delete()
        downloadManager.delete(bookRemoteId)
        assertTrue("DB-Eintrag gelöscht", db.downloadDao().get(bookRemoteId) == null)
        // Lokale Datei muss ebenfalls verschwunden sein
        if (!localPath.startsWith("content://")) {
            assertTrue("Datei gelöscht", !File(localPath).exists())
        }
    }
}
