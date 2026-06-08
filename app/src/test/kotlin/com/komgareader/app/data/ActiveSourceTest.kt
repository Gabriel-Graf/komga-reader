package com.komgareader.app.data

import com.komgareader.domain.repository.ServerConfig
import com.komgareader.domain.repository.ServerRepository
import com.komgareader.domain.source.BrowsableSource
import com.komgareader.domain.source.SourceManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ActiveSourceTest {

    // ServerRepository ist eine reine Lese-Quelle für die Configs — minimaler Fake mit festem Flow.
    private class FakeServerRepository(private val list: List<ServerConfig>) : ServerRepository {
        override val configs: Flow<List<ServerConfig>> = flowOf(list)
        override val config: Flow<ServerConfig?> = flowOf(list.firstOrNull())
        override suspend fun save(config: ServerConfig) = error("not used")
        override suspend fun remove(id: Long) = error("not used")
        override suspend fun clear() = error("not used")
    }

    private fun activeSource(vararg configs: ServerConfig): ActiveSource {
        val sources = SourceManager()
        val registration = SourceRegistration(sources, KomgaSourceProvider())
        return ActiveSource(sources, FakeServerRepository(configs.toList()), registration)
    }

    @Test
    fun `current liefert aktive Quelle als BrowsableSource`() = runBlocking {
        val active = activeSource(ServerConfig(name = "Heim", baseUrl = "http://h", apiKey = "k"))

        assertTrue(active.current() is BrowsableSource)
    }

    @Test
    fun `current ist null ohne server`() = runBlocking {
        assertNull(activeSource().current())
    }

    @Test
    fun `all liefert mehrere Quellen gleichzeitig`() = runBlocking {
        val active = activeSource(
            ServerConfig(name = "A", baseUrl = "http://a", apiKey = "k"),
            ServerConfig(name = "B", baseUrl = "http://b", apiKey = "k"),
        )

        assertEquals(2, active.all().size)
    }
}
