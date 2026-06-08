package com.komgareader.app.ui.groups

import androidx.lifecycle.SavedStateHandle
import com.komgareader.app.data.ActiveSource
import com.komgareader.app.data.KomgaSourceProvider
import com.komgareader.app.data.SourceRegistration
import com.komgareader.domain.model.Book
import com.komgareader.domain.model.Series
import com.komgareader.domain.model.Shelf
import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.repository.DownloadRepository
import com.komgareader.domain.repository.DownloadedBook
import com.komgareader.domain.repository.ServerConfig
import com.komgareader.domain.repository.ServerRepository
import com.komgareader.domain.repository.ShelfRepository
import com.komgareader.domain.source.BrowsableSource
import com.komgareader.domain.source.PageRef
import com.komgareader.domain.source.PagedResult
import com.komgareader.domain.source.SourceFilter
import com.komgareader.domain.source.SourceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Beweist Task 11: das ViewModel lädt **quellen-agnostisch** über [ActiveSource]/
 * [BrowsableSource] — kein `KomgaSourceProvider` im Konstruktor. Eine OPDS-/Stub-/Plugin-
 * Quelle würde dieselben Ergebnisse liefern, ohne eine Zeile zu ändern.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GroupBrowseViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeEach fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterEach fun tearDown() = Dispatchers.resetMain()

    private class FakeSource(
        override val id: Long = 99L,
        private val series: List<Series> = emptyList(),
    ) : BrowsableSource {
        override val name = "Fake"
        override val kind = SourceKind.OPDS

        override suspend fun browse(page: Int, filter: SourceFilter): PagedResult<Series> =
            PagedResult(series, hasNextPage = false)
        override suspend fun search(query: String, page: Int): PagedResult<Series> = error("not used")
        override suspend fun books(seriesRemoteId: String): List<Book> = error("not used")
        override suspend fun seriesDetail(seriesRemoteId: String): Series? = error("not used")
        override suspend fun pages(bookRemoteId: String): List<PageRef> = error("not used")
        override suspend fun openPage(ref: PageRef): ByteArray = error("not used")
        override suspend fun downloadFile(bookRemoteId: String, onProgress: (Long, Long) -> Unit): ByteArray = error("not used")
        override suspend fun seriesIdOf(bookRemoteId: String): String = error("not used")
    }

    private class FakeServerRepository(cfg: ServerConfig?) : ServerRepository {
        override val config: Flow<ServerConfig?> = flowOf(cfg)
        override suspend fun save(config: ServerConfig) = error("not used")
        override suspend fun clear() = error("not used")
    }

    private class FakeActiveSource(private val source: BrowsableSource?) : ActiveSource(
        sources = SourceManager(),
        servers = FakeServerRepository(null),
        registration = SourceRegistration(SourceManager(), KomgaSourceProvider()),
    ) {
        override suspend fun current(): BrowsableSource? = source
    }

    private class FakeShelves(items: List<Shelf>) : ShelfRepository {
        override val shelves: Flow<List<Shelf>> = flowOf(items)
        override suspend fun add(shelf: Shelf): Long = error("not used")
        override suspend fun update(shelf: Shelf) = error("not used")
        override suspend fun delete(id: Long) = error("not used")
    }

    private class FakeDownloads : DownloadRepository {
        override val downloads: Flow<List<DownloadedBook>> = flowOf(emptyList())
        override suspend fun get(bookRemoteId: String): DownloadedBook? = null
        override suspend fun put(book: DownloadedBook) = error("not used")
        override suspend fun remove(bookRemoteId: String) = error("not used")
    }

    private fun series(remoteId: String) = Series(id = 0, sourceId = 99L, remoteId = remoteId, title = remoteId)

    @Test
    fun `state laedt serien ueber active source (quellen-agnostisch)`() = runTest(dispatcher) {
        val shelf = Shelf(id = 1L, name = "Regal", sources = emptyList())
        val vm = GroupBrowseViewModel(
            savedStateHandle = SavedStateHandle(mapOf("shelfId" to 1L)),
            shelfRepository = FakeShelves(listOf(shelf)),
            serverRepository = FakeServerRepository(ServerConfig("Heim", "http://h")),
            active = FakeActiveSource(FakeSource(series = listOf(series("a"), series("b"), series("c")))),
            downloadRepository = FakeDownloads(),
        )

        // Subscriber halten, damit die WhileSubscribed-Quelle läuft.
        val seen = mutableListOf<GroupBrowseUiState>()
        val job = backgroundScope.launch { vm.state.collect { seen += it } }
        advanceUntilIdle()
        job.cancel()

        val content = seen.last() as GroupBrowseUiState.Content
        assertEquals(3, content.series.size)
        assertEquals(listOf("a", "b", "c"), content.series.map { it.remoteId })
    }
}
