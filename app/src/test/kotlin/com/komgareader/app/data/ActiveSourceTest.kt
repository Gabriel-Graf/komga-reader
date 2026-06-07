package com.komgareader.app.data

import com.komgareader.domain.repository.ServerConfig
import com.komgareader.domain.repository.ServerRepository
import com.komgareader.domain.source.BrowsableSource
import com.komgareader.domain.source.SourceManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ActiveSourceTest {

    // ServerRepository ist eine reine Lese-Quelle für die Config — als minimaler Fake
    // mit festem Flow modelliert (save/clear werden von ActiveSource nicht berührt).
    private class FakeServerRepository(override val config: Flow<ServerConfig?>) : ServerRepository {
        override suspend fun save(config: ServerConfig) = error("not used")
        override suspend fun clear() = error("not used")
    }

    private fun activeSource(config: ServerConfig?): ActiveSource {
        val sources = SourceManager()
        val registration = SourceRegistration(sources, KomgaSourceProvider())
        return ActiveSource(sources, FakeServerRepository(flowOf(config)), registration)
    }

    @Test
    fun `current liefert aktive Quelle als BrowsableSource`() = runBlocking {
        val active = activeSource(ServerConfig(name = "Heim", baseUrl = "http://h", apiKey = "k"))

        val source = active.current()

        assertTrue(source is BrowsableSource)
    }

    @Test
    fun `current ist null ohne server`() = runBlocking {
        val active = activeSource(config = null)

        assertNull(active.current())
    }
}
