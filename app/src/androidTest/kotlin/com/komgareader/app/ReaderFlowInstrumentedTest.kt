package com.komgareader.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.komgareader.app.data.KomgaSourceProvider
import com.komgareader.domain.model.ReadProgress
import com.komgareader.domain.repository.ServerConfig
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderFlowInstrumentedTest {

    @Test fun laedt_seiten_und_synct_fortschritt() = runTest {
        val source = KomgaSourceProvider().from(
            ServerConfig(
                name = "T",
                baseUrl = "http://10.0.2.2:25600/api/v1/",
                apiKey = "2243c9f4ecc5404992ddf8eba4bf6488",
            ),
        )!!
        val books = source.books("0QKVPRDV0293Z")          // Berserk
        val book = books.first { it.remoteId == "0QKVPRDV42BFA" } // vol01
        val pages = source.pages(book.remoteId)
        assertEquals(4, pages.size)
        val bytes = source.openPage(pages.first())
        assertTrue(bytes.size > 1000)                       // echtes Bild
        source.pushProgress(
            book.remoteId,
            ReadProgress(bookId = 0, page = 2, totalPages = 4, updatedAt = 1),
        )
        val pulled = source.pullProgress(book.remoteId)!!
        assertEquals(2, pulled.page)                        // Komga hat den Stand
    }
}
