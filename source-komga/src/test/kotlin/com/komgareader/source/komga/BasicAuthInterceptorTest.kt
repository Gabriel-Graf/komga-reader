package com.komgareader.source.komga

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

class BasicAuthInterceptorTest {

    private val server = MockWebServer()

    @AfterTest fun tearDown() = server.shutdown()

    @Test
    fun `setzt Authorization-Header mit Basic-Schema`() {
        server.enqueue(MockResponse().setBody("ok"))
        val client = OkHttpClient.Builder()
            .addInterceptor(BasicAuthInterceptor("benutzer", "geheim"))
            .build()

        client.newCall(Request.Builder().url(server.url("/api/v1/series")).build()).execute().close()

        val recorded = server.takeRequest()
        val authHeader = recorded.getHeader("Authorization") ?: ""
        assertTrue(authHeader.startsWith("Basic "), "Erwartet 'Basic ...', war: '$authHeader'")
    }
}
