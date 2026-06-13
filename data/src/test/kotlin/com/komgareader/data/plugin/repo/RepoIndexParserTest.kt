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

    @Test fun parsesOptionalMetadataFields() {
        val json = """
            {"name":"R","plugins":[{
              "packageName":"com.x","apkUrl":"x.apk","fingerprint":"ab","versionCode":1,
              "previewUrl":"img/x.png","readmeUrl":"x/README.md","license":"OFL-1.1"
            }]}
        """.trimIndent()
        val e = parseRepoIndex(json)!!.plugins.single()
        assertEquals("img/x.png", e.previewUrl)
        assertEquals("x/README.md", e.readmeUrl)
        assertEquals("OFL-1.1", e.license)
    }

    @Test fun optionalMetadataDefaultsToEmpty() {
        val json = """{"name":"R","plugins":[{"packageName":"com.x","apkUrl":"x.apk","fingerprint":"ab","versionCode":1}]}"""
        val e = parseRepoIndex(json)!!.plugins.single()
        assertEquals("", e.previewUrl)
        assertEquals("", e.readmeUrl)
        assertEquals("", e.license)
    }

    @Test fun resolveRepoUrl_absolutePassesThrough() {
        assertEquals("https://x/c.png", resolveRepoUrl("https://r/repo.json", "https://x/c.png"))
    }

    @Test fun resolveRepoUrl_relativeAgainstRepoBase() {
        assertEquals("https://r/sub/p/a.png", resolveRepoUrl("https://r/sub/repo.json", "p/a.png"))
        assertEquals("https://r/p/a.png", resolveRepoUrl("https://r/repo.json", "/p/a.png"))
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

    @Test fun fontTypeMapsToFontKind() {
        assertEquals(PluginKind.FONT, pluginKindOf("font"))
        assertEquals(PluginKind.FONT, pluginKindOf("FONT"))
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
