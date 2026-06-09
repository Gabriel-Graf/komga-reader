package com.komgareader.source.komga

import com.komgareader.domain.model.CollectionKind
import com.komgareader.domain.source.CollectionSyncSource
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KomgaCollectionSyncTest {

    private val server = MockWebServer()
    private fun source(): KomgaSource =
        KomgaSourceFactory.create(name = "Test", baseUrl = server.url("/api/v1/").toString(), apiKey = "k")

    @AfterTest fun tearDown() = server.shutdown()

    @Test
    fun `canWriteCollections gibt true zurück wenn Rolle ADMIN`() = runTest {
        server.enqueue(MockResponse().setBody("""{"roles":["ADMIN","USER"]}""").addHeader("Content-Type", "application/json"))

        val sync = source() as CollectionSyncSource
        assertTrue(sync.canWriteCollections())

        val req = server.takeRequest()
        assertEquals("GET", req.method)
        assertTrue(req.path!!.endsWith("/users/me"), "Pfad war: ${req.path}")
    }

    @Test
    fun `createCollection SERIES trifft POST collections mit seriesIds`() = runTest {
        server.enqueue(MockResponse().setBody("""{"id":"col1","name":"X","ordered":true,"seriesIds":["a","b"]}""").addHeader("Content-Type", "application/json"))

        val sync = source() as CollectionSyncSource
        val result = sync.createCollection(CollectionKind.SERIES, "X", listOf("a", "b"))

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertTrue(req.path!!.endsWith("/collections"), "Pfad war: ${req.path}")
        val body = req.body.readUtf8()
        assertTrue(body.contains("\"seriesIds\""), "Body enthält kein seriesIds: $body")
        assertTrue(body.contains("\"a\""), "Body enthält nicht a: $body")
        assertTrue(body.contains("\"b\""), "Body enthält nicht b: $body")

        assertEquals("col1", result.remoteId)
        assertEquals("X", result.name)
        assertEquals(listOf("a", "b"), result.memberRemoteIds)
    }

    @Test
    fun `createCollection BOOK trifft POST readlists mit bookIds`() = runTest {
        server.enqueue(MockResponse().setBody("""{"id":"rl1","name":"X","ordered":true,"bookIds":["a"]}""").addHeader("Content-Type", "application/json"))

        val sync = source() as CollectionSyncSource
        val result = sync.createCollection(CollectionKind.BOOK, "X", listOf("a"))

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertTrue(req.path!!.endsWith("/readlists"), "Pfad war: ${req.path}")
        val body = req.body.readUtf8()
        assertTrue(body.contains("\"bookIds\""), "Body enthält kein bookIds: $body")
        assertTrue(body.contains("\"a\""), "Body enthält nicht a: $body")

        assertEquals("rl1", result.remoteId)
        assertEquals("X", result.name)
        assertEquals(listOf("a"), result.memberRemoteIds)
    }
}
