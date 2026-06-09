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
        // Komga verlangt `ordered` (Default true) — Json muss encodeDefaults setzen, sonst 400.
        assertTrue(body.contains("\"ordered\":true"), "Body enthält kein ordered: $body")

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

    @Test
    fun `listCollections SERIES trifft GET collections unpaged und mappt RemoteCollection`() = runTest {
        server.enqueue(
            MockResponse()
                .setBody("""{"content":[{"id":"c1","name":"N","ordered":true,"seriesIds":["s1"]}],"last":true,"number":0,"totalPages":1}""")
                .addHeader("Content-Type", "application/json"),
        )

        val sync = source() as CollectionSyncSource
        val result = sync.listCollections(CollectionKind.SERIES)

        val req = server.takeRequest()
        assertEquals("GET", req.method)
        assertTrue(req.path!!.contains("/collections"), "Pfad war: ${req.path}")
        assertTrue(req.path!!.contains("unpaged=true"), "Kein unpaged-Param im Pfad: ${req.path}")

        assertEquals(1, result.size)
        assertEquals("c1", result[0].remoteId)
        assertEquals("N", result[0].name)
        assertEquals(listOf("s1"), result[0].memberRemoteIds)
    }

    @Test
    fun `listCollections BOOK trifft GET readlists unpaged und mappt RemoteCollection`() = runTest {
        server.enqueue(
            MockResponse()
                .setBody("""{"content":[{"id":"rl1","name":"M","ordered":true,"bookIds":["b1","b2"]}],"last":true,"number":0,"totalPages":1}""")
                .addHeader("Content-Type", "application/json"),
        )

        val sync = source() as CollectionSyncSource
        val result = sync.listCollections(CollectionKind.BOOK)

        val req = server.takeRequest()
        assertEquals("GET", req.method)
        assertTrue(req.path!!.contains("/readlists"), "Pfad war: ${req.path}")
        assertTrue(req.path!!.contains("unpaged=true"), "Kein unpaged-Param im Pfad: ${req.path}")

        assertEquals(1, result.size)
        assertEquals("rl1", result[0].remoteId)
        assertEquals("M", result[0].name)
        assertEquals(listOf("b1", "b2"), result[0].memberRemoteIds)
    }

    @Test
    fun `updateCollection SERIES trifft PATCH collections-id mit seriesIds`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))

        val sync = source() as CollectionSyncSource
        sync.updateCollection(CollectionKind.SERIES, "c1", "N", listOf("a", "b"))

        val req = server.takeRequest()
        assertEquals("PATCH", req.method)
        assertTrue(req.path!!.endsWith("/collections/c1"), "Pfad war: ${req.path}")
        val body = req.body.readUtf8()
        assertTrue(body.contains("\"seriesIds\""), "Body enthält kein seriesIds: $body")
        assertTrue(body.contains("\"a\""), "Body enthält nicht a: $body")
        assertTrue(body.contains("\"b\""), "Body enthält nicht b: $body")
    }

    @Test
    fun `deleteCollection SERIES trifft DELETE collections-id`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        val sync = source() as CollectionSyncSource
        sync.deleteCollection(CollectionKind.SERIES, "c1")

        val req = server.takeRequest()
        assertEquals("DELETE", req.method)
        assertTrue(req.path!!.endsWith("/collections/c1"), "Pfad war: ${req.path}")
    }

    @Test
    fun `canWriteCollections gibt false zurück wenn Rolle USER aber nicht ADMIN`() = runTest {
        server.enqueue(MockResponse().setBody("""{"roles":["USER"]}""").addHeader("Content-Type", "application/json"))

        val sync = source() as CollectionSyncSource
        val result = sync.canWriteCollections()

        val req = server.takeRequest()
        assertTrue(req.path!!.endsWith("/users/me"), "Pfad war: ${req.path}")
        assertEquals(false, result)
    }
}
