package com.komgareader.app.data

import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.repository.ServerConfig
import com.komgareader.domain.source.BrowsableSource
import com.komgareader.domain.source.SourceId
import com.komgareader.domain.source.SourceManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
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
    fun `wiederholtes activate mit gleicher config registriert NICHT neu (kein Churn)`() {
        // Schutz gegen die Race: current() ruft activate() bei jedem Aufruf. Solange die Config
        // unverändert ist, darf die Quelle NICHT ab- und neu registriert werden — sonst gibt es
        // ein Fenster, in dem der Coil-Fetcher sources.get(id) == null sieht.
        val sources = SourceManager()
        val registration = SourceRegistration(sources, KomgaSourceProvider())
        val config = ServerConfig(name = "Heim", baseUrl = "http://h", apiKey = "k")

        val id1 = registration.activate(config)!!
        val firstInstance = sources.get(id1)
        val id2 = registration.activate(config)!!

        assertEquals(id1, id2)
        assertSame(firstInstance, sources.get(id2)) // dieselbe Instanz → nicht neu registriert
    }

    @Test
    fun `sync registriert mehrere quellen gleichzeitig und entfernt nicht mehr gelistete`() {
        val sources = SourceManager()
        val registration = SourceRegistration(sources, KomgaSourceProvider())
        val a = ServerConfig(name = "A", baseUrl = "http://a", apiKey = "k")
        val b = ServerConfig(name = "B", baseUrl = "http://b", apiKey = "k")

        val ids = registration.sync(listOf(a, b))
        assertEquals(2, ids.size)
        assertEquals(2, sources.sources.value.size) // beide registriert (n Komga gleichzeitig)

        registration.sync(listOf(a)) // b entfernt
        assertEquals(1, sources.sources.value.size)

        registration.sync(emptyList())
        assertEquals(0, sources.sources.value.size)
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
