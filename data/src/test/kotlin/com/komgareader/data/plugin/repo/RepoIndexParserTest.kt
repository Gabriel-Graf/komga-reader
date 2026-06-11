package com.komgareader.data.plugin.repo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RepoIndexParserTest {

    private val full = """
        {"name":"Off","plugins":[
          {"packageName":"a.b","name":"A","type":"preset","abiVersion":1,"versionCode":2,"versionName":"1.1","apkUrl":"p/a.apk","fingerprint":"AA:bb"},
          {"packageName":"c.d","name":"C","type":"source","abiVersion":1,"versionCode":1,"versionName":"1.0","apkUrl":"https://x/c.apk","fingerprint":"cc"}
        ]}
    """.trimIndent()

    @Test fun parsesValidIndex() {
        val idx = parseRepoIndex(full)!!
        assertEquals("Off", idx.name)
        assertEquals(2, idx.plugins.size)
        assertEquals("a.b", idx.plugins[0].packageName)
    }

    @Test fun ignoresUnknownFields() {
        val idx = parseRepoIndex("""{"name":"X","extra":true,"plugins":[{"packageName":"a","name":"A","versionCode":1,"apkUrl":"u","fingerprint":"f","weird":9}]}""")
        assertEquals("X", idx!!.name)
        assertEquals(1, idx.plugins.size)
    }

    @Test fun dropsEntriesMissingRequiredFields() {
        val idx = parseRepoIndex("""{"plugins":[
            {"name":"no-pkg","versionCode":1,"apkUrl":"u","fingerprint":"f"},
            {"packageName":"ok","name":"Ok","versionCode":1,"apkUrl":"u","fingerprint":"f"}
        ]}""")
        assertEquals(1, idx!!.plugins.size)
        assertEquals("ok", idx.plugins[0].packageName)
    }

    @Test fun malformedReturnsNull() {
        assertNull(parseRepoIndex("not json"))
        assertNull(parseRepoIndex("""[1,2,3]"""))
    }

    @Test fun resolveApkUrl_absolutePassesThrough() {
        assertEquals("https://x/c.apk", resolveApkUrl("https://r/repo.json", "https://x/c.apk"))
    }

    @Test fun resolveApkUrl_relativeAgainstRepoBase() {
        assertEquals("https://r/sub/p/a.apk", resolveApkUrl("https://r/sub/repo.json", "p/a.apk"))
        assertEquals("https://r/p/a.apk", resolveApkUrl("https://r/repo.json", "/p/a.apk"))
    }

    @Test fun installStateAllCases() {
        assertEquals(InstallState.NOT_INSTALLED, installState(2, null))
        assertEquals(InstallState.INSTALLED, installState(2, 2))
        assertEquals(InstallState.INSTALLED, installState(2, 3))
        assertEquals(InstallState.UPDATE_AVAILABLE, installState(3, 2))
    }

    @Test fun fingerprintMatchesNormalizesColonsAndCase() {
        assertTrue(fingerprintMatches("AA:bb:CC", "aabbcc"))
        assertTrue(fingerprintMatches("aa bb", "AABB"))
        assertTrue(!fingerprintMatches("aa", "bb"))
    }

    @Test fun pluginKindOf_unknownIsSource() {
        assertEquals(PluginKind.PRESET, pluginKindOf("preset"))
        assertEquals(PluginKind.SOURCE, pluginKindOf("source"))
        assertEquals(PluginKind.SOURCE, pluginKindOf("garbage"))
    }

    @Test fun mergeDedupsByPackageKeepingHighestVersion() {
        val e1 = BrowsableEntry(RepoPluginEntry("a.b", "A", versionCode = 1, apkUrl = "u", fingerprint = "f"), "R1", "https://r1/repo.json", PluginKind.SOURCE)
        val e2 = BrowsableEntry(RepoPluginEntry("a.b", "A", versionCode = 3, apkUrl = "u", fingerprint = "f"), "R2", "https://r2/repo.json", PluginKind.SOURCE)
        val e3 = BrowsableEntry(RepoPluginEntry("c.d", "C", versionCode = 1, apkUrl = "u", fingerprint = "f"), "R1", "https://r1/repo.json", PluginKind.PRESET)
        val merged = mergeRepoEntries(listOf(e1, e2, e3))
        assertEquals(2, merged.size)
        assertEquals(3L, merged.first { it.entry.packageName == "a.b" }.entry.versionCode)
        assertEquals("R2", merged.first { it.entry.packageName == "a.b" }.repoName)
    }
}
