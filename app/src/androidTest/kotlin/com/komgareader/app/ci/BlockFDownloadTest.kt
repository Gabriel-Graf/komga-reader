package com.komgareader.app.ci

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.komgareader.data.db.AppDatabase
import com.komgareader.data.download.DownloadManager
import com.komgareader.data.repository.RoomDownloadRepository
import com.komgareader.data.repository.RoomSettingsRepository
import com.komgareader.domain.source.SourceFilter
import com.komgareader.render.mupdf.MupdfDocumentFactory
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicLong

/** Spec §9 Block F — Download mit Fortschritt → offline gespeichert, lesbar, löschbar (F21). */
@RunWith(AndroidJUnit4::class)
class BlockFDownloadTest {

    private lateinit var stack: CiSourceStack
    private lateinit var db: AppDatabase
    private lateinit var downloads: DownloadManager

    @Before fun setUp() {
        stack = CiSourceStack()
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        downloads = DownloadManager(
            ctx,
            RoomDownloadRepository(db.downloadDao()),
            RoomSettingsRepository(db.settingsDao()),
        )
    }

    @After fun tearDown() { db.close(); stack.close() }

    /** F21: downloadFile meldet Fortschritt → store → readBytes nicht leer → MuPDF rendert → delete. */
    @Test fun f21_download_dann_offline_lesbar() = runTest {
        stack.register(CiKomga.A)
        val source = stack.activeSource.all().first()
        val series = source.browse(0, SourceFilter()).items.first { it.title == CiFixtures.MANGA_SERIES }
        val book = source.books(series.remoteId).first()

        // Download über die Naht, mit Fortschritts-Callback.
        val lastRead = AtomicLong(0)
        val bytes = source.downloadFile(book.remoteId) { read, _ -> lastRead.set(read) }
        assertTrue("Heruntergeladene Bytes > 1 KiB", bytes.size > 1024)
        assertTrue("onProgress muss mind. einmal mit read>0 gefeuert haben", lastRead.get() > 0)

        // Offline speichern und zurücklesen.
        downloads.store(
            bookRemoteId = book.remoteId, sourceId = source.id, seriesRemoteId = series.remoteId,
            title = book.title, format = "cbz", totalPages = book.pageCount,
        ) { out -> out.write(bytes) }
        val entity = db.downloadDao().get(book.remoteId)
        assertNotNull("Download-Eintrag muss persistiert sein", entity)
        val localBytes = downloads.readBytes(entity!!.localPath)
        assertTrue("Lokale Bytes nicht leer", localBytes.isNotEmpty())

        // Offline rendern (MuPDF) — beweist „lesbar ohne Netz".
        val doc = MupdfDocumentFactory().open(localBytes, ".cbz")
        assertTrue("Mind. eine Seite renderbar", doc.pageCount() >= 1)

        // Aufräumen → Eintrag + Datei weg.
        downloads.delete(book.remoteId)
        assertNull("Nach delete kein Eintrag mehr", db.downloadDao().get(book.remoteId))
    }
}
