package com.komgareader.source.komga

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ApiKeyInterceptorTest {

    private val server = MockWebServer()

    @AfterTest fun tearDown() = server.shutdown()

    @Test
    fun `setzt X-API-Key-Header auf jede Anfrage`() {
        server.enqueue(MockResponse().setBody("ok"))
        server.start()
        val client = OkHttpClient.Builder().addInterceptor(ApiKeyInterceptor("geheim123")).build()

        client.newCall(Request.Builder().url(server.url("/api/v1/series")).build()).execute().close()

        val recorded = server.takeRequest()
        assertEquals("geheim123", recorded.getHeader("X-API-Key"))
    }
}
