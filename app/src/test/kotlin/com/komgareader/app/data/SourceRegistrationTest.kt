package com.komgareader.app.data

import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.repository.ServerConfig
import com.komgareader.domain.source.BrowsableSource
import com.komgareader.domain.source.SourceId
import com.komgareader.domain.source.SourceManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SourceRegistrationTest {

    // Die Komga-Quelle leitet ihre id aus der normalisierten Basis-URL (mit Slash)
    // ab — derselbe Vertrag wie KomgaSourceFactory und GroupsViewModel.computeSourceId.
    private val expectedId = SourceId.of("Heim", SourceKind.KOMGA, "http://h/")

    @Test
    fun `config setzt registriert komga-quelle mit deterministischer id`() {
        val sources = SourceManager()
        val registration = SourceRegistration(sources, KomgaSourceProvider())
        val config = ServerConfig(name = "Heim", baseUrl = "http://h", apiKey = "k")

        val id = registration.activate(config)

        assertEquals(expectedId, id)
        assertTrue(sources.get(id!!) is BrowsableSource)
        assertEquals(id, registration.activeSourceId())
    }

    @Test
    fun `opds-config registriert eine opds-quelle mit deterministischer id`() {
        val sources = SourceManager()
        val registration = SourceRegistration(sources, KomgaSourceProvider())
        val config = ServerConfig(name = "Feed", baseUrl = "http://o/opds", kind = SourceKind.OPDS)

        val id = registration.activate(config)

        assertEquals(SourceId.of("Feed", SourceKind.OPDS, "http://o/opds"), id)
        assertTrue(sources.get(id!!) is BrowsableSource)
    }

    @Test
    fun `null-config deaktiviert die zuvor aktive quelle`() {
        val sources = SourceManager()
        val registration = SourceRegistration(sources, KomgaSourceProvider())
        val id = registration.activate(ServerConfig("Heim", "http://h", apiKey = "k"))!!

        registration.activate(null)

        assertNull(sources.get(id))
        assertNull(registration.activeSourceId())
    }
}
