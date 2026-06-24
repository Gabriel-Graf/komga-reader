package com.komgareader.source.komga

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KomgaDownloadTest {

    private val server = MockWebServer()
    private fun source(): KomgaSource =
        KomgaSourceFactory.create(name = "Mein Komga", baseUrl = server.url("/api/v1/").toString(), apiKey = "k")

    @AfterTest fun tearDown() = server.shutdown()

    @Test
    fun `downloadFile laedt die ganze Datei`() = runTest {
        server.enqueue(MockResponse().setBody("EPUBBYTES"))
        val bytes = source().downloadFile("B1")
        assertEquals("EPUBBYTES", bytes.decodeToString())
        assertTrue(server.takeRequest().path!!.endsWith("/books/B1/file"))
    }

    @Test
    fun `downloadTo streamt die ganze Datei in den OutputStream`() = runTest {
        server.enqueue(MockResponse().setBody("CBZBYTES"))
        val out = java.io.ByteArrayOutputStream()
        source().downloadTo("B1", out)
        assertEquals("CBZBYTES", out.toByteArray().decodeToString())
        assertTrue(server.takeRequest().path!!.endsWith("/books/B1/file"))
    }

    @Test
    fun `downloadTo meldet Fortschritt`() = runTest {
        server.enqueue(MockResponse().setBody("0123456789"))
        var lastRead = 0L
        source().downloadTo("B1", java.io.ByteArrayOutputStream()) { read, _ -> lastRead = read }
        assertEquals(10L, lastRead)
    }
}
