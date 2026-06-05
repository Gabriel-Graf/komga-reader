package com.komgareader.domain.source

import com.komgareader.domain.model.SourceKind
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeSource(
    override val id: Long,
    override val name: String,
    override val kind: SourceKind = SourceKind.KOMGA,
) : MediaSource

class SourceManagerTest {

    @Test
    fun `get liefert registrierte Quelle`() {
        val manager = SourceManager()
        val source = FakeSource(id = 42, name = "Komga")
        manager.register(source)
        assertEquals(source, manager.get(42))
    }

    @Test
    fun `get liefert null für unbekannte Quelle`() {
        val manager = SourceManager()
        assertNull(manager.get(99))
    }

    @Test
    fun `getOrStub liefert Stub für fehlende Quelle statt null`() {
        val manager = SourceManager()
        val stub = manager.getOrStub(id = 7, name = "Verschwunden")
        assertTrue(stub is StubSource)
        assertEquals(7, stub.id)
        assertEquals("Verschwunden", stub.name)
    }

    @Test
    fun `sources-Flow emittiert nach Registrierung`() = runTest {
        val manager = SourceManager()
        manager.sources.test {
            assertEquals(emptyMap(), awaitItem())
            manager.register(FakeSource(id = 1, name = "A"))
            assertEquals(setOf(1L), awaitItem().keys)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
