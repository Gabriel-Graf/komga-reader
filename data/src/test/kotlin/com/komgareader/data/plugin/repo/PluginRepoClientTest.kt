package com.komgareader.data.plugin.repo

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PluginRepoClientTest {
    private val server = MockWebServer()
    private val client = PluginRepoClient(OkHttpClient())

    @AfterTest fun tearDown() { server.shutdown() }

    @Test fun fetchIndexReturnsBody() = runTest {
        server.enqueue(MockResponse().setBody("""{"name":"R","plugins":[]}"""))
        server.start()
        val body = client.fetchIndex(server.url("/repo.json").toString())
        assertEquals("""{"name":"R","plugins":[]}""", body)
    }

    @Test fun fetchIndexReturnsNullOn404() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        server.start()
        assertNull(client.fetchIndex(server.url("/repo.json").toString()))
    }

    @Test fun fetchTextReturnsBody() = runTest {
        server.enqueue(MockResponse().setBody("# Hallo\n![x](https://h/x.png)"))
        server.start()
        val body = client.fetchText(server.url("/README.md").toString())
        assertEquals("# Hallo\n![x](https://h/x.png)", body)
    }

    @Test fun fetchTextReturnsNullOn404() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        server.start()
        assertNull(client.fetchText(server.url("/missing").toString()))
    }

    @Test fun downloadWritesBytesToFile() = runTest {
        server.enqueue(MockResponse().setBody("APKBYTES"))
        server.start()
        val dest = File.createTempFile("dl_", ".apk").apply { deleteOnExit() }
        val ok = client.download(server.url("/a.apk").toString(), dest)
        assertTrue(ok)
        assertEquals("APKBYTES", dest.readText())
    }
}
