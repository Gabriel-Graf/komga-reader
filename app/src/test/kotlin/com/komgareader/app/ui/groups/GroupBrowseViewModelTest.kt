package com.komgareader.app.ui.groups

import androidx.lifecycle.SavedStateHandle
import com.komgareader.app.data.ActiveSource
import com.komgareader.app.data.KomgaSourceProvider
import com.komgareader.app.data.SourceRegistration
import com.komgareader.domain.model.Book
import com.komgareader.domain.model.Series
import com.komgareader.domain.model.Shelf
import com.komgareader.domain.model.ShelfSource
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
        override suspend fun coverBytes(remoteId: String, isSeriesCover: Boolean): ByteArray = error("not used")
    }

    private class FakeServerRepository(cfg: ServerConfig?) : ServerRepository {
        override val configs: Flow<List<ServerConfig>> = flowOf(listOfNotNull(cfg))
        override val config: Flow<ServerConfig?> = flowOf(cfg)
        override suspend fun save(config: ServerConfig) = error("not used")
        override suspend fun remove(id: Long) = error("not used")
        override suspend fun clear() = error("not used")
    }

    /** Multi-source-fähig: löst Quellen per `id` auf, genau wie der echte [ActiveSource]. */
    private class FakeActiveSource(private val sources: List<BrowsableSource>) : ActiveSource(
        sources = SourceManager(),
        servers = FakeServerRepository(null),
        registration = SourceRegistration(SourceManager(), KomgaSourceProvider()),
    ) {
        constructor(source: BrowsableSource?) : this(listOfNotNull(source))
        override suspend fun current(): BrowsableSource? = sources.firstOrNull()
        override suspend fun get(sourceId: Long): BrowsableSource? = sources.firstOrNull { it.id == sourceId }
        override suspend fun all(): List<BrowsableSource> = sources
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

    private fun series(remoteId: String, sourceId: Long = 99L) =
        Series(id = 0, sourceId = sourceId, remoteId = remoteId, title = remoteId)

    @Test
    fun `state laedt serien ueber active source (quellen-agnostisch)`() = runTest(dispatcher) {
        val shelf = Shelf(id = 1L, name = "Regal", sources = listOf(ShelfSource(sourceId = 99L, containerIds = emptyList())))
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

    @Test
    fun `state merged serien aus allen quellen des regals - nicht nur der ersten`() = runTest(dispatcher) {
        // Regal referenziert ZWEI Quellen; jede liefert eigene Serien. Greift der VM nur auf
        // „die erste/aktive" zu, fehlen die Serien der zweiten Quelle (b) → Merge bräche.
        val shelf = Shelf(
            id = 1L, name = "Regal",
            sources = listOf(
                ShelfSource(sourceId = 42L, containerIds = emptyList()),
                ShelfSource(sourceId = 99L, containerIds = emptyList()),
            ),
        )
        val first = FakeSource(id = 42L, series = listOf(series("a", 42L)))
        val second = FakeSource(id = 99L, series = listOf(series("b", 99L)))
        val vm = GroupBrowseViewModel(
            savedStateHandle = SavedStateHandle(mapOf("shelfId" to 1L)),
            shelfRepository = FakeShelves(listOf(shelf)),
            serverRepository = FakeServerRepository(ServerConfig("Heim", "http://h")),
            active = FakeActiveSource(listOf(first, second)),
            downloadRepository = FakeDownloads(),
        )

        val seen = mutableListOf<GroupBrowseUiState>()
        val job = backgroundScope.launch { vm.state.collect { seen += it } }
        advanceUntilIdle()
        job.cancel()

        val content = seen.last() as GroupBrowseUiState.Content
        assertEquals(listOf("a", "b"), content.series.map { it.remoteId })
    }
}
