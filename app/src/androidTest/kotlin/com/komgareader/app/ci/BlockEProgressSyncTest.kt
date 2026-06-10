package com.komgareader.app.ci

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.komgareader.data.db.AppDatabase
import com.komgareader.data.repository.RoomReadProgressRepository
import com.komgareader.domain.model.ReadProgress
import com.komgareader.domain.source.SourceFilter
import com.komgareader.domain.source.SyncingSource
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Spec §9 Block E — Lese-Fortschritt offline-first (lokaler dirty-Lifecycle) + live push/pull. */
@RunWith(AndroidJUnit4::class)
class BlockEProgressSyncTest {

    private lateinit var stack: CiSourceStack
    private lateinit var db: AppDatabase
    private lateinit var progress: RoomReadProgressRepository

    @Before fun setUp() {
        stack = CiSourceStack()
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        progress = RoomReadProgressRepository(db.readProgressDao())
    }

    @After fun tearDown() { db.close(); stack.close() }

    /** E-local: markProgress → dirty enthält den Eintrag → markSynced → dirty leer (Sync-Queue). */
    @Test fun e_lokaler_dirty_lifecycle() = runTest {
        progress.markProgress(sourceId = 1L, bookRemoteId = "book-1", page = 3, completed = false, totalPages = 10)
        val dirtyAfterMark = progress.dirty()
        assertTrue("Frischer Fortschritt muss dirty sein", dirtyAfterMark.any { it.bookRemoteId == "book-1" })

        progress.markSynced("book-1")
        assertTrue("Nach markSynced nicht mehr dirty", progress.dirty().none { it.bookRemoteId == "book-1" })
    }

    /** E18: Fortschritt live an die Quelle pushen und zurücklesen (round-trip über SyncingSource). */
    @Test fun e18_push_und_pull_progress_live() = runTest {
        stack.register(CiKomga.A)
        val source = stack.activeSource.all().first()
        val sync = source as? SyncingSource
        assertNotNull("Komga-Quelle muss SyncingSource sein", sync)

        val series = source.browse(0, SourceFilter()).items.first { it.title == CiFixtures.MANGA_SERIES }
        val book = source.books(series.remoteId).first()
        val pages = source.pages(book.remoteId)
        val target = (pages.size / 2).coerceAtLeast(1)  // mittlere Seite (1-basiert)

        sync!!.pushProgress(
            book.remoteId,
            ReadProgress(bookId = 0L, page = target, totalPages = pages.size, updatedAt = 1_700_000_000_000L),
        )
        val pulled = sync.pullProgress(book.remoteId)
        assertNotNull("Fortschritt muss serverseitig abrufbar sein", pulled)
        assertEquals("Zurückgelesene Seite muss der gepushten entsprechen", target, pulled!!.page)
    }

    /**
     * E20: Bei zwei Quellen synct das Werk von B zu B — A bleibt unberührt.
     * Push auf ein B-Buch, Pull über DIE B-Quelle (get(sourceId)) liefert es; die A-Quelle
     * kennt diese remoteId nicht (eigener Namespace).
     */
    @Test fun e20_progress_synct_zur_richtigen_quelle() = runTest {
        stack.register(CiKomga.A, CiKomga.B)
        val all = stack.activeSource.all()
        val bSource = all.first { src ->
            src.browse(0, SourceFilter()).items.any { it.title == CiFixtures.WEBTOON_SERIES }
        }
        val series = bSource.browse(0, SourceFilter()).items.first { it.title == CiFixtures.WEBTOON_SERIES }
        val book = bSource.books(series.remoteId).first()

        val bSync = stack.activeSource.get(book.sourceId) as? SyncingSource
        assertNotNull("B-Quelle muss SyncingSource sein", bSync)
        bSync!!.pushProgress(
            book.remoteId,
            ReadProgress(bookId = 0L, page = 1, totalPages = book.pageCount.coerceAtLeast(1), updatedAt = 1_700_000_000_000L),
        )
        val pulledFromB = bSync.pullProgress(book.remoteId)
        assertNotNull("B muss den auf B gepushten Fortschritt liefern", pulledFromB)
        assertEquals(1, pulledFromB!!.page)
    }
}
